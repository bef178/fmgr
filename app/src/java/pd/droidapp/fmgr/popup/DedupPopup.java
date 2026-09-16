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
import java.util.stream.Collectors;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.popup.PopupFileGroupsAdapter.PopupFileGroup;
import pd.droidapp.fmgr.popup.ProcessingWorker.StopReason;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.util.SelectionBar;
import pd.util.FileOps;
import pd.util.PathOps;

public class DedupPopup extends ProcessingPopup {

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

    private DedupWorker worker;
    private final Collection<FileProperties> netRemoved = new LinkedList<>();

    private final Map<String, List<FileProperties>> byChecksum = new LinkedHashMap<>();
    private final Map<String, FileProperties> byPath = new HashMap<>();
    private int totalScanned;

    public DedupPopup(View containerView, String startDirectory) {
        super(containerView, R.layout.dedup_popup);
        this.startDirectory = startDirectory;

        statusBar = new StatusBar(mainAreaView.findViewById(R.id.status_bar));
        selectionBar = new SelectionBar(mainAreaView.findViewById(R.id.selection_bar));
        groupsView = mainAreaView.findViewById(R.id.popup_items_list);
        groupsAdapter = new PopupFileGroupsAdapter(startDirectory, selectionBar);

        titleBar.setTitle(R.string.delete_duplicate_files);

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

    private void initSelectionBar() {
        selectionBar.addButton(R.layout.selection_button_jump, c -> c == 1, v -> {
            if (selectionBar.size() == 1) {
                if (onJump != null) {
                    onJump.accept(groupsAdapter.getSelectedItems().get(0).path);
                }
                selfWindow.dismiss();
            }
        });

        selectionBar.addButton(R.layout.selection_button_copy, c -> c > 0, v -> {
            if (onCopy != null) {
                onCopy.accept(groupsAdapter.getSelectedItems());
            }
            selfWindow.dismiss();
        });

        selectionBar.addButton(R.layout.selection_button_cut, c -> c > 0, v -> {
            if (onCut != null) {
                onCut.accept(groupsAdapter.getSelectedItems());
            }
            selfWindow.dismiss();
        });

        selectionBar.addButton(R.layout.selection_button_delete, c -> c > 0, v -> {
            DeletePopup deletePopup = new DeletePopup(containerView, groupsAdapter.getSelectedItems(), false);
            deletePopup.whenPopupDismissed((added, removed) -> {
                netRemoved.addAll(removed);
                selectionBar.remove(removed.stream()
                        .map(item -> item.path)
                        .collect(Collectors.toList()));
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

        selectionBar.addButton(R.layout.selection_button_smart_select, c -> c > 0, v -> {
            List<String> newlySelected = suggestToSelect();
            selectionBar.add(newlySelected);
            selectionBar.invalidate();
            groupsAdapter.notifyDataSetChanged();
        });

        selectionBar.addButton(R.layout.selection_button_select_clear, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.invalidate();
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
        worker = new DedupWorker();
        totalScanned = 0;
        byChecksum.clear();
        byPath.clear();

        worker.whenStarted(() -> containerView.post(() -> {
            statusBar.markRunning();
            statusBar.setText(context.getString(R.string.scanning));
            selectionBar.invalidate();
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
            updateButtons();
            if (reason == StopReason.COMPLETED) {
                statusBar.markDone();
            } else {
                statusBar.markStopped();
            }
        }));
        worker.start(startDirectory);
        updateButtons();
    }

    // derive the group totals from byChecksum, then refresh list and status
    private void refreshGroups() {
        int totalGroups = 0;
        int totalGroupItems = 0;
        for (List<FileProperties> group : byChecksum.values()) {
            if (group.size() > 1) {
                totalGroups++;
                totalGroupItems += group.size();
            }
        }
        groupsAdapter.set(buildFileGroups());
        selectionBar.invalidate();
        statusBar.setText(context.getString(R.string.x_scanned_y_found_groups,
                totalScanned, totalGroups, totalGroupItems));
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
    private List<String> suggestToSelect() {
        List<String> newlySelected = new LinkedList<>();
        for (PopupFileGroup group : groupsAdapter.getGroups()) {
            List<FileProperties> items = group.getItems();
            List<String> unselected = new LinkedList<>();
            for (FileProperties item : items) {
                if (!selectionBar.hasSelected(item.path)) {
                    unselected.add(item.path);
                }
            }
            if (unselected.size() <= 1) {
                // 0: group fully selected; 1: keep it, nothing else to select
                continue;
            }
            String pathToKeep = unselected.get(0);
            for (int i = 1; i < unselected.size(); i++) {
                String path = unselected.get(i);
                if (smartCompare(path, pathToKeep) < 0) {
                    newlySelected.add(pathToKeep);
                    pathToKeep = path;
                } else {
                    newlySelected.add(path);
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
