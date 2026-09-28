package pd.droidapp.fmgr.popup;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.popup.ProcessingWorker.StopReason;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.view.ButtonState;
import pd.droidapp.fmgr.view.FileItemBar;
import pd.droidapp.fmgr.view.PopupBottomBar;
import pd.droidapp.fmgr.view.FileGroupsAdapter;
import pd.droidapp.fmgr.view.PopupTitleBar;
import pd.droidapp.fmgr.view.SelectionBar;
import pd.droidapp.fmgr.view.StatusBar;
import pd.util.FileOps;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.getSizeString;

public class FindDupPopup extends ProcessingPopup {

    private final String startDirectory;

    // views
    private final StatusBar statusBar;
    private final SelectionBar selectionBar;
    private final RecyclerView groupsView;
    private final FileGroupsAdapter groupsAdapter;

    // callbacks
    private Consumer<String> onJump;
    private Consumer<Collection<FileProperties>> onCopy;
    private Consumer<Collection<FileProperties>> onCut;
    private PopupOnDismissedListener onPopupDismissed;

    private FindDupWorker worker;
    private final Collection<FileProperties> netRemoved = new LinkedList<>();

    private final Map<String, List<FileProperties>> byChecksum = new LinkedHashMap<>();
    private final Map<String, FileProperties> byPath = new HashMap<>();
    private final Set<String> selectedPaths = new LinkedHashSet<>();
    private int totalScanned;
    private StatusBar.IconStatus statusBarIconStatus = StatusBar.IconStatus.IDLE;

    public FindDupPopup(View containerView, String startDirectory) {
        super(containerView);
        this.startDirectory = startDirectory;

        statusBar = new StatusBar(contentView.findViewById(R.id.status_bar), R.drawable.ic_find_dup_24);
        selectionBar = new SelectionBar(contentView.findViewById(R.id.selection_bar));
        groupsView = contentView.findViewById(R.id.popup_items_list);
        groupsAdapter = new FileGroupsAdapter();

        bottomBar.whenButtonClicked(id -> {
            if (id == R.string.abort) {
                if (worker != null) {
                    worker.cancel();
                }
                renderBottomBar();
            } else if (id == R.string.close) {
                selfWindow.dismiss();
            }
        });

        initSelectionBar();
        initItemsView();
    }

    @Override
    protected void inflateContent() {
        LinearLayout.LayoutParams contentParams = (LinearLayout.LayoutParams) contentView.getLayoutParams();
        contentParams.height = 0;
        contentParams.weight = 1;
        contentView.setLayoutParams(contentParams);
        LayoutInflater.from(context).inflate(R.layout.find_dup_popup_content, contentView, true);
    }

    private void initSelectionBar() {
        selectionBar.whenButtonClicked(id -> {
            if (id == R.drawable.baseline_arrow_forward_24) {
                if (selectedPaths.size() == 1) {
                    if (onJump != null) {
                        onJump.accept(selectedPaths.iterator().next());
                    }
                    selfWindow.dismiss();
                }
            } else if (id == R.drawable.ic_copy_24) {
                if (onCopy != null) {
                    onCopy.accept(getSelectedItems());
                }
                selfWindow.dismiss();
            } else if (id == R.drawable.ic_cut_24) {
                if (onCut != null) {
                    onCut.accept(getSelectedItems());
                }
                selfWindow.dismiss();
            } else if (id == R.drawable.ic_delete_24) {
                DeletePopup deletePopup = new DeletePopup(containerView, startDirectory, getSelectedItems(), false);
                deletePopup.whenPopupDismissed((added, removed) -> {
                    netRemoved.addAll(removed);
                    for (FileProperties item : removed) {
                        selectedPaths.remove(item.path);
                        FileProperties props = byPath.remove(item.path);
                        if (props == null) {
                            continue;
                        }
                        List<FileProperties> group = byChecksum.get(props.sha256sum);
                        if (group != null) {
                            group.remove(props);
                        }
                    }
                    selectedPaths.retainAll(getVisiblePaths());
                    refreshGroups();
                    renderSelectionBar();
                });
                deletePopup.show();
            } else if (id == R.drawable.ic_check_all_24) {
                List<FileProperties> newlySelected = suggestToSelect();
                for (FileProperties item : newlySelected) {
                    selectedPaths.add(item.path);
                }
                refreshGroups();
                renderSelectionBar();
            } else if (id == R.drawable.ic_close_24) {
                selectedPaths.clear();
                refreshGroups();
                renderSelectionBar();
            }
        });
    }

    private void renderSelectionBar() {
        int numSelected = selectedPaths.size();
        selectionBar.render(new SelectionBar.State(numSelected,
                new ButtonState(R.drawable.baseline_arrow_forward_24, numSelected == 1),
                new ButtonState(R.drawable.ic_copy_24, numSelected > 0),
                new ButtonState(R.drawable.ic_cut_24, numSelected > 0),
                new ButtonState(R.drawable.ic_delete_24, numSelected > 0),
                new ButtonState(R.drawable.ic_check_all_24, numSelected > 0),
                new ButtonState(R.drawable.ic_close_24, numSelected > 0)));
    }

    private void initItemsView() {
        groupsAdapter.whenItemClicked(path -> {
            if (!selectedPaths.isEmpty()) {
                toggleSelected(path);
            }
        });
        groupsAdapter.whenItemLongClicked(this::toggleSelected);
        groupsView.setLayoutManager(new LinearLayoutManager(context));
        groupsView.setAdapter(groupsAdapter);
    }

    private void toggleSelected(String path) {
        if (selectedPaths.contains(path)) {
            selectedPaths.remove(path);
        } else {
            selectedPaths.add(path);
        }

        renderSelectionBar();

        FileProperties item = byPath.get(path);
        List<FileProperties> group = item == null ? null : byChecksum.get(item.sha256sum);
        if (group == null) {
            return;
        }
        groupsAdapter.update(buildGroupState(group));
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

    @Override
    protected void onShow() {
        titleBar.render(new PopupTitleBar.State(context.getString(R.string.find_duplicate)));
        renderStatusBar(statusBarIconStatus);
        renderBottomBar();

        start();
    }

    // derive the group totals from byChecksum
    private void renderStatusBar(StatusBar.IconStatus iconStatus) {
        int totalGroups = 0;
        int totalGroupItems = 0;
        for (List<FileProperties> group : byChecksum.values()) {
            if (group.size() > 1) {
                totalGroups++;
                totalGroupItems += group.size();
            }
        }
        statusBar.render(new StatusBar.State(iconStatus, context.getString(R.string.x_scanned_y_found_groups,
                totalScanned, totalGroups, totalGroupItems)));
    }

    private void renderBottomBar() {
        boolean isProcessing = worker != null && worker.isWorking();
        bottomBar.render(new PopupBottomBar.State(
                ButtonState.ofText(R.string.abort, context.getString(R.string.abort),
                        isProcessing, isProcessing && !worker.isCancelled()),
                ButtonState.ofText(R.string.close, context.getString(R.string.close),
                        worker != null && !worker.isWorking())));
    }

    private void start() {
        worker = new FindDupWorker();
        totalScanned = 0;
        byChecksum.clear();
        byPath.clear();
        selectedPaths.clear();

        worker.whenStarted(() -> containerView.post(() -> {
            statusBarIconStatus = StatusBar.IconStatus.RUNNING;
            renderStatusBar(statusBarIconStatus);
        }));
        worker.whenUpdated((scanned, completed) -> containerView.post(() -> {
            totalScanned += scanned;
            Map<String, List<FileProperties>> dirty = new LinkedHashMap<>();
            for (FileProperties completedItem : completed) {
                List<FileProperties> group = byChecksum.computeIfAbsent(completedItem.sha256sum, k -> new LinkedList<>());
                group.add(completedItem);
                byPath.put(completedItem.path, completedItem);
                if (group.size() < 2) {
                    continue;
                }
                if (group.size() == 2) {
                    // keep the new visible group appended not inserted
                    byChecksum.remove(completedItem.sha256sum);
                    byChecksum.put(completedItem.sha256sum, group);
                }
                dirty.put(completedItem.sha256sum, group);
            }
            List<FileGroupsAdapter.State> states = new LinkedList<>();
            for (List<FileProperties> group : dirty.values()) {
                states.add(buildGroupState(group));
            }
            groupsAdapter.updateOrAppend(states);
            renderStatusBar(statusBarIconStatus);
        }));
        worker.whenStopped(reason -> containerView.post(() -> {
            statusBarIconStatus = reason == StopReason.COMPLETED ? StatusBar.IconStatus.COMPLETED : StatusBar.IconStatus.STOPPED;
            renderStatusBar(statusBarIconStatus);
            renderBottomBar();
        }));
        worker.start(startDirectory);
        renderBottomBar();
    }

    private void refreshGroups() {
        groupsAdapter.render(buildFileGroupStates());
        renderStatusBar(statusBarIconStatus);
    }

    private List<FileGroupsAdapter.State> buildFileGroupStates() {
        List<FileGroupsAdapter.State> states = new LinkedList<>();
        for (List<FileProperties> group : getVisibleGroups()) {
            states.add(buildGroupState(group));
        }
        return states;
    }

    private FileGroupsAdapter.State buildGroupState(List<FileProperties> group) {
        FileItemBar.State[] itemStates = new FileItemBar.State[group.size()];
        for (int i = 0; i < itemStates.length; i++) {
            FileProperties item = group.get(i);
            itemStates[i] = new FileItemBar.State(item.path,
                    PathOps.singleton.relativize(startDirectory, item.path),
                    null,
                    null,
                    R.drawable.i_file_24,
                    selectedPaths.contains(item.path),
                    null);
        }
        FileProperties first = group.get(0);
        String title = context.getString(R.string.x_files_y_each, group.size(), getSizeString(first.size));
        return new FileGroupsAdapter.State(first.sha256sum, title, itemStates);
    }

    private List<List<FileProperties>> getVisibleGroups() {
        List<List<FileProperties>> visibleGroups = new LinkedList<>();
        for (List<FileProperties> group : byChecksum.values()) {
            if (group.size() > 1 && group.get(0).size != null) {
                visibleGroups.add(group);
            }
        }
        return visibleGroups;
    }

    private Set<String> getVisiblePaths() {
        Set<String> visiblePaths = new HashSet<>();
        for (List<FileProperties> group : getVisibleGroups()) {
            for (FileProperties item : group) {
                visiblePaths.add(item.path);
            }
        }
        return visiblePaths;
    }

    private List<FileProperties> getSelectedItems() {
        List<FileProperties> selected = new LinkedList<>();
        for (List<FileProperties> group : getVisibleGroups()) {
            for (FileProperties item : group) {
                if (selectedPaths.contains(item.path)) {
                    selected.add(item);
                }
            }
        }
        return selected;
    }

    /**
     * Returns files to newly select, leaving already-selected ones untouched.
     * Keeps at most one file per group unselected.
     */
    private List<FileProperties> suggestToSelect() {
        List<FileProperties> newlySelected = new LinkedList<>();
        for (List<FileProperties> items : getVisibleGroups()) {
            List<FileProperties> unselected = new LinkedList<>();
            for (FileProperties item : items) {
                if (!selectedPaths.contains(item.path)) {
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
