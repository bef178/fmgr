package pd.droidapp.fmgr.popup;

import android.view.View;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.popup.PopupFileGroupsAdapter.PopupFileGroup;
import pd.droidapp.fmgr.popup.ProcessingWorker.StopReason;
import pd.droidapp.fmgr.popup.StatusBar.IconState;
import pd.droidapp.fmgr.popup.StatusBar.State;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.util.SelectionBar;
import pd.util.FileOps;
import pd.util.PathOps;

public class FindDupPopup extends ProcessingPopup {

    private final String startDirectory;

    // views
    private final StatusBar statusBar;
    private final SelectionBar selectionBar;
    private final RecyclerView groupsView;
    private final PopupFileGroupsAdapter groupsAdapter;

    // callbacks
    private Consumer<String> onJump;
    private Consumer<Collection<FileProperties>> onCopy;
    private Consumer<Collection<FileProperties>> onCut;
    private PopupOnDismissedListener onPopupDismissed;

    private FindDupWorker worker;
    private final Collection<FileProperties> netRemoved = new LinkedList<>();

    private final Map<String, List<FileProperties>> byChecksum = new LinkedHashMap<>();
    private final Map<String, FileProperties> byPath = new HashMap<>();
    private int totalScanned;
    private IconState statusBarIconState = IconState.IDLE;

    public FindDupPopup(View containerView, String startDirectory) {
        super(containerView, R.layout.find_dup_popup);
        this.startDirectory = startDirectory;

        statusBar = new StatusBar(mainAreaView.findViewById(R.id.status_bar), R.drawable.i_delete_copy_24);
        selectionBar = new SelectionBar(mainAreaView.findViewById(R.id.selection_bar));
        groupsView = mainAreaView.findViewById(R.id.popup_items_list);
        groupsAdapter = new PopupFileGroupsAdapter(startDirectory);
        groupsAdapter.whenSelectionChanged(this::renderSelectionBar);

        titleBar.setTitle(R.string.find_duplicate);

        initSelectionBar();
        initItemsView();
    }

    @Override
    protected void initPopupButtons() {
        super.initPopupButtons();
        buttonBar.addButton(R.string.abort, this::isProcessing, () -> isProcessing() && !worker.isCancelled(), v -> {
            if (worker != null) {
                worker.cancel();
            }
            updateButtons();
        });
        buttonBar.addButton(R.string.close, () -> worker != null && !worker.isWorking(), () -> true, v -> selfWindow.dismiss());
    }

    private void renderSelectionBar() {
        selectionBar.render(groupsAdapter.getSelectedCount());
    }

    private void initSelectionBar() {
        selectionBar.addButton(R.layout.selection_button_jump, () -> groupsAdapter.getSelectedCount() == 1, v -> {
            if (groupsAdapter.getSelectedCount() == 1) {
                if (onJump != null) {
                    onJump.accept(groupsAdapter.getSelectedItems().get(0).path);
                }
                selfWindow.dismiss();
            }
        });

        selectionBar.addButton(R.layout.selection_button_copy, () -> groupsAdapter.hasSelection(), v -> {
            if (onCopy != null) {
                onCopy.accept(groupsAdapter.getSelectedItems());
            }
            selfWindow.dismiss();
        });

        selectionBar.addButton(R.layout.selection_button_cut, () -> groupsAdapter.hasSelection(), v -> {
            if (onCut != null) {
                onCut.accept(groupsAdapter.getSelectedItems());
            }
            selfWindow.dismiss();
        });

        selectionBar.addButton(R.layout.selection_button_delete, () -> groupsAdapter.hasSelection(), v -> {
            DeletePopup deletePopup = new DeletePopup(containerView, startDirectory, groupsAdapter.getSelectedItems(), false);
            deletePopup.whenPopupDismissed((added, removed) -> {
                netRemoved.addAll(removed);
                groupsAdapter.deselect(removed);
                for (FileProperties item : removed) {
                    FileProperties props = byPath.remove(item.path);
                    if (props == null) {
                        continue;
                    }
                    List<FileProperties> group = byChecksum.get(props.sha256sum);
                    if (group != null) {
                        group.remove(props);
                    }
                }
                refreshGroups();
            });
            deletePopup.show();
        });

        selectionBar.addButton(R.layout.selection_button_smart_select, () -> groupsAdapter.hasSelection(), v -> {
            List<FileProperties> newlySelected = suggestToSelect();
            groupsAdapter.select(newlySelected);
            groupsAdapter.notifyDataSetChanged();
        });

        selectionBar.addButton(R.layout.selection_button_select_clear, () -> groupsAdapter.hasSelection(), v -> {
            groupsAdapter.clearSelection();
            groupsAdapter.notifyDataSetChanged();
        });
    }

    private void initItemsView() {
        groupsView.setLayoutManager(new LinearLayoutManager(context));
        groupsView.setAdapter(groupsAdapter);
    }

    @Override
    protected boolean isProcessing() {
        return worker != null && worker.isWorking();
    }

    @Override
    protected void onDismissing(Runnable continueDismiss) {
        if (worker != null) {
            worker.cancel();
        }
        continueDismiss.run();
    }

    @Override
    protected void onDismissed() {
        if (onPopupDismissed != null) {
            onPopupDismissed.accept(Collections.emptyList(), netRemoved);
        }
    }

    public void whenJumpClicked(Consumer<String> onJump) {
        this.onJump = onJump;
    }

    public void whenCopyClicked(Consumer<Collection<FileProperties>> onCopy) {
        this.onCopy = onCopy;
    }

    public void whenCutClicked(Consumer<Collection<FileProperties>> onCut) {
        this.onCut = onCut;
    }

    public void whenPopupDismissed(PopupOnDismissedListener onPopupDismissed) {
        this.onPopupDismissed = onPopupDismissed;
    }

    @Override
    protected void onShow() {
        doScan();
    }

    private void doScan() {
        worker = new FindDupWorker();
        totalScanned = 0;
        byChecksum.clear();
        byPath.clear();

        worker.whenStarted(() -> containerView.post(() -> {
            statusBarIconState = IconState.RUNNING;
            renderStatusBar(statusBarIconState);
        }));
        worker.whenUpdated((scanned, completed) -> containerView.post(() -> {
            totalScanned += scanned;
            for (FileProperties props : completed) {
                List<FileProperties> group = byChecksum.computeIfAbsent(props.sha256sum, k -> new LinkedList<>());
                group.add(props);
                byPath.put(props.path, props);
                if (group.size() == 2) {
                    // keep the new visible group appended not inserted
                    byChecksum.remove(props.sha256sum);
                    byChecksum.put(props.sha256sum, group);
                }
            }
            refreshGroups();
        }));
        worker.whenStopped(reason -> containerView.post(() -> {
            statusBarIconState = reason == StopReason.COMPLETED ? IconState.COMPLETED : IconState.STOPPED;
            renderStatusBar(statusBarIconState);
            updateButtons();
        }));
        worker.start(startDirectory);
        updateButtons();
    }

    private void refreshGroups() {
        groupsAdapter.set(buildFileGroups());
        renderStatusBar(statusBarIconState);
    }

    // derive the group totals from byChecksum
    private void renderStatusBar(IconState iconState) {
        int totalGroups = 0;
        int totalGroupItems = 0;
        for (List<FileProperties> group : byChecksum.values()) {
            if (group.size() > 1) {
                totalGroups++;
                totalGroupItems += group.size();
            }
        }
        statusBar.render(new State(iconState, context.getString(R.string.x_scanned_y_found_groups,
                totalScanned, totalGroups, totalGroupItems)));
    }

    private List<PopupFileGroup> buildFileGroups() {
        List<PopupFileGroup> newFileGroups = new LinkedList<>();
        for (List<FileProperties> group : byChecksum.values()) {
            if (group.size() > 1) {
                FileProperties first = group.get(0);
                if (first.size == null) {
                    continue;
                }
                newFileGroups.add(new PopupFileGroup(first.size, first.sha256sum, group));
            }
        }
        return newFileGroups;
    }

    /**
     * Returns files to newly select, leaving already-selected ones untouched.
     * Keeps at most one file per group unselected.
     */
    private List<FileProperties> suggestToSelect() {
        List<FileProperties> newlySelected = new LinkedList<>();
        for (PopupFileGroup group : groupsAdapter.getGroups()) {
            List<FileProperties> items = group.getItems();
            List<FileProperties> unselected = new LinkedList<>();
            for (FileProperties item : items) {
                if (!groupsAdapter.isSelected(item)) {
                    unselected.add(item);
                }
            }
            if (unselected.size() <= 1) {
                // 0: group fully selected; 1: keep it, nothing else to select
                continue;
            }
            FileProperties itemToKeep = unselected.get(0);
            for (int i = 1; i < unselected.size(); i++) {
                FileProperties item = unselected.get(i);
                if (smartCompare(item.path, itemToKeep.path) < 0) {
                    newlySelected.add(itemToKeep);
                    itemToKeep = item;
                } else {
                    newlySelected.add(item);
                }
            }
        }
        return newlySelected;
    }

    private int smartCompare(String path1, String path2) {
        long time1 = mtimeOf(path1);
        long time2 = mtimeOf(path2);
        if (time1 != time2) {
            return -Long.compare(time1, time2);
        }

        String basename1 = PathOps.singleton.basename(path1);
        String basename2 = PathOps.singleton.basename(path2);
        if (!basename1.equals(basename2)) {
            return Integer.compare(basename1.length(), basename2.length());
        }

        return path1.length() - path2.length();
    }

    private static long mtimeOf(String path) {
        Long mtime = FileOps.singleton.stat(path).mtime;
        return mtime == null ? 0 : mtime;
    }
}
