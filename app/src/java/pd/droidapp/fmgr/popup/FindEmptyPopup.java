package pd.droidapp.fmgr.popup;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.repository.FindEmptyWorker;
import pd.droidapp.fmgr.repository.ProcessingWorker.StopReason;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.view.ButtonState;
import pd.droidapp.fmgr.view.FileItemAdapter;
import pd.droidapp.fmgr.view.FileItemBar;
import pd.droidapp.fmgr.view.PopupBottomBar;
import pd.droidapp.fmgr.view.PopupTitleBar;
import pd.droidapp.fmgr.view.SelectionBar;
import pd.droidapp.fmgr.view.StatusBar;
import pd.util.PathOps;

public class FindEmptyPopup extends ProcessingPopup {

    private final String startDirectory;
    private final List<FileProperties> items = new ArrayList<>();
    private final Set<String> selectedPaths = new LinkedHashSet<>();

    // views
    private final StatusBar statusBar;
    private final SelectionBar selectionBar;
    private final RecyclerView itemsView;
    private final FileItemAdapter itemAdapter;

    // callbacks
    private Consumer<String> onJump;
    private PopupOnDismissedListener onPopupDismissed;

    private FindEmptyWorker worker;
    private int totalScanned;
    private final Collection<FileProperties> netRemoved = new LinkedList<>();

    public FindEmptyPopup(View containerView, String startDirectory) {
        super(containerView);
        this.startDirectory = startDirectory;

        statusBar = new StatusBar(contentView.findViewById(R.id.status_bar), R.drawable.ia_find_24);
        selectionBar = new SelectionBar(contentView.findViewById(R.id.selection_bar));
        itemsView = contentView.findViewById(R.id.popup_items_list);
        itemAdapter = new FileItemAdapter();

        bottomBar.whenButtonClicked(id -> {
            if (id == R.id.action_abort) {
                if (worker != null) {
                    worker.cancel();
                }
                renderBottomBar();
            } else if (id == R.id.action_close) {
                selfWindow.dismiss();
            }
        });

        initSelectionBar();
        initItemsView();
    }

    @Override
    protected void inflateContent() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) areaView.getLayoutParams();
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        areaView.setLayoutParams(params);

        LinearLayout.LayoutParams contentParams = (LinearLayout.LayoutParams) contentView.getLayoutParams();
        contentParams.height = 0;
        contentParams.weight = 1;
        contentView.setLayoutParams(contentParams);
        LayoutInflater.from(context).inflate(R.layout.find_empty_popup_content, contentView, true);
    }

    private void initSelectionBar() {
        selectionBar.whenButtonClicked(id -> {
            if (id == R.drawable.ic_arrow_right_24) {
                if (selectedPaths.size() == 1) {
                    if (onJump != null) {
                        onJump.accept(getSelectedItems().get(0).path);
                    }
                    selfWindow.dismiss();
                }
            } else if (id == R.drawable.ia_delete_24) {
                DeletePopup deletePopup = new DeletePopup(containerView, startDirectory, getSelectedItems(), false);
                deletePopup.whenPopupDismissed((added, removed) -> {
                    netRemoved.addAll(removed);
                    removeItems(removed);
                    deselect(removed);
                });
                deletePopup.show();
            } else if (id == R.drawable.ia_delete_up_24) {
                DeletePopup deletePopup = new DeletePopup(containerView, startDirectory, getSelectedItems(), true);
                deletePopup.whenPopupDismissed((added, removed) -> {
                    netRemoved.addAll(removed);
                    removeItems(removed);
                    deselect(removed);
                });
                deletePopup.show();
            } else if (id == R.drawable.ia_select_all_24) {
                selectAll();
            } else if (id == R.drawable.ic_xmark_24) {
                clearSelection();
            }
        });
    }

    private void initItemsView() {
        itemAdapter.whenItemClicked(path -> {
            if (!selectedPaths.isEmpty()) {
                toggleSelected(path);
            }
        });
        itemAdapter.whenItemLongClicked(this::toggleSelected);
        itemsView.setLayoutManager(new LinearLayoutManager(context));
        itemsView.setAdapter(itemAdapter);
    }

    private void renderItems() {
        itemAdapter.render(buildStates(items, 1));
    }

    private List<FileItemBar.State> buildStates(Collection<FileProperties> items, int firstIndex) {
        List<FileItemBar.State> states = new ArrayList<>(items.size());
        int index = firstIndex;
        for (FileProperties item : items) {
            states.add(buildState(item, index++));
        }
        return states;
    }

    private FileItemBar.State buildState(FileProperties item, int index) {
        return new FileItemBar.State(
                item.path,
                PathOps.singleton.relativize(startDirectory, item.path),
                null,
                String.valueOf(index),
                item.isDirectory ? R.drawable.ic_directory_24 : R.drawable.ic_file_24,
                selectedPaths.contains(item.path));
    }

    private void renderSelectionBar() {
        int numSelected = selectedPaths.size();
        selectionBar.render(new SelectionBar.State(numSelected,
                new ButtonState(R.drawable.ic_arrow_right_24, numSelected == 1),
                new ButtonState(R.drawable.ia_delete_24, numSelected > 0),
                new ButtonState(R.drawable.ia_delete_up_24, numSelected > 0),
                new ButtonState(R.drawable.ia_select_all_24, numSelected > 0),
                new ButtonState(R.drawable.ic_xmark_24, numSelected > 0)));
    }

    private void onSelectionChanged() {
        renderSelectionBar();
        renderItems();
    }

    private void toggleSelected(String path) {
        if (!selectedPaths.remove(path)) {
            selectedPaths.add(path);
        }
        renderSelectionBar();
        for (int i = 0; i < items.size(); i++) {
            FileProperties item = items.get(i);
            if (item.path.equals(path)) {
                itemAdapter.update(i, buildState(item, i + 1));
                break;
            }
        }
    }

    private void selectAll() {
        selectedPaths.clear();
        for (FileProperties item : items) {
            selectedPaths.add(item.path);
        }
        onSelectionChanged();
    }

    private void clearSelection() {
        selectedPaths.clear();
        onSelectionChanged();
    }

    private void deselect(Collection<FileProperties> toDeselect) {
        for (FileProperties item : toDeselect) {
            selectedPaths.remove(item.path);
        }
        onSelectionChanged();
    }

    private void removeItems(Collection<FileProperties> toRemove) {
        Set<String> paths = new HashSet<>();
        for (FileProperties item : toRemove) {
            paths.add(item.path);
        }
        items.removeIf(item -> paths.contains(item.path));
        renderItems();
    }

    private List<FileProperties> getSelectedItems() {
        List<FileProperties> selected = new LinkedList<>();
        for (FileProperties item : items) {
            if (selectedPaths.contains(item.path)) {
                selected.add(item);
            }
        }
        return selected;
    }

    public void whenJumpClicked(Consumer<String> onJump) {
        this.onJump = onJump;
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
        titleBar.render(new PopupTitleBar.State(context.getString(R.string.find_empty)));
        renderStatusBar(StatusBar.IconStatus.IDLE);
        renderBottomBar();

        start();
    }

    private void renderStatusBar(StatusBar.IconStatus iconStatus) {
        statusBar.render(new StatusBar.State(iconStatus, context.getString(R.string.x_scanned_y_found,
                totalScanned, items.size())));
    }

    private void renderBottomBar() {
        boolean isProcessing = worker != null && worker.isWorking();
        bottomBar.render(new PopupBottomBar.State(
                new ButtonState(R.id.action_abort, R.drawable.ic_blockmark_24, isProcessing, isProcessing && !worker.isCancelled()),
                new ButtonState(R.id.action_close, R.drawable.ic_xmark_24, worker != null && !worker.isWorking(), true)));
    }

    private void start() {
        worker = new FindEmptyWorker();
        worker.whenStarted(() -> containerView.post(() -> {
            renderStatusBar(StatusBar.IconStatus.RUNNING);
        }));
        worker.whenUpdated((scanned, matched) -> containerView.post(() -> {
            totalScanned += scanned;
            int firstIndex = items.size() + 1;
            items.addAll(matched);
            itemAdapter.append(buildStates(matched, firstIndex));
            renderStatusBar(StatusBar.IconStatus.RUNNING);
        }));
        worker.whenStopped(reason -> containerView.post(() -> {
            renderStatusBar(reason == StopReason.COMPLETED ? StatusBar.IconStatus.COMPLETED : StatusBar.IconStatus.STOPPED);
            renderBottomBar();
        }));
        worker.start(startDirectory);

        renderBottomBar();
    }
}
