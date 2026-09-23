package pd.droidapp.fmgr.popup;

import android.view.View;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.popup.ProcessingWorker.StopReason;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.view.ButtonState;
import pd.droidapp.fmgr.view.SelectionBar;
import pd.droidapp.fmgr.view.StatusBar;

public class FindEmptyPopup extends ProcessingPopup {

    private final String startDirectory;

    // views
    private final StatusBar statusBar;
    private final SelectionBar selectionBar;
    private final RecyclerView itemsView;
    private final PopupFileItemsAdapter itemsAdapter;

    // callbacks
    private Consumer<String> onJump;
    private PopupOnDismissedListener onPopupDismissed;

    private FindEmptyWorker worker;
    private int totalScanned;
    private final Collection<FileProperties> netRemoved = new LinkedList<>();

    public FindEmptyPopup(View containerView, String startDirectory) {
        super(containerView, R.layout.find_empty_popup);
        this.startDirectory = startDirectory;

        statusBar = new StatusBar(mainAreaView.findViewById(R.id.status_bar), R.drawable.i_delete_empty_24);
        selectionBar = new SelectionBar(mainAreaView.findViewById(R.id.selection_bar));
        itemsView = mainAreaView.findViewById(R.id.popup_items_list);
        itemsAdapter = new PopupFileItemsAdapter(startDirectory, true);

        initSelectionBar();
        initItemsView();

        titleBar.setTitle(R.string.find_empty);
    }

    private void initSelectionBar() {
        selectionBar.whenButtonClicked(id -> {
            if (id == R.drawable.baseline_arrow_forward_24) {
                if (itemsAdapter.getSelectedCount() == 1) {
                    if (onJump != null) {
                        onJump.accept(itemsAdapter.getSelectedItems().get(0).path);
                    }
                    selfWindow.dismiss();
                }
            } else if (id == R.drawable.outline_delete_24) {
                DeletePopup deletePopup = new DeletePopup(containerView, startDirectory, itemsAdapter.getSelectedItems(), false);
                deletePopup.whenPopupDismissed((added, removed) -> {
                    netRemoved.addAll(removed);
                    itemsAdapter.remove(removed);
                    itemsAdapter.deselect(removed);
                });
                deletePopup.show();
            } else if (id == R.drawable.i_delete_up_24) {
                DeletePopup deletePopup = new DeletePopup(containerView, startDirectory, itemsAdapter.getSelectedItems(), true);
                deletePopup.whenPopupDismissed((added, removed) -> {
                    netRemoved.addAll(removed);
                    itemsAdapter.remove(removed);
                    itemsAdapter.deselect(removed);
                });
                deletePopup.show();
            } else if (id == R.drawable.i_check_all_24) {
                itemsAdapter.selectAll();
                itemsAdapter.notifyDataSetChanged();
            } else if (id == R.drawable.baseline_close_24) {
                itemsAdapter.clearSelection();
                itemsAdapter.notifyDataSetChanged();
            }
        });
    }

    private void renderSelectionBar() {
        int numSelected = itemsAdapter.getSelectedCount();
        selectionBar.render(new SelectionBar.State(numSelected,
                new ButtonState(R.drawable.baseline_arrow_forward_24, numSelected == 1),
                new ButtonState(R.drawable.outline_delete_24, numSelected > 0),
                new ButtonState(R.drawable.i_delete_up_24, numSelected > 0),
                new ButtonState(R.drawable.i_check_all_24, numSelected > 0),
                new ButtonState(R.drawable.baseline_close_24, numSelected > 0)));
    }

    private void initItemsView() {
        itemsAdapter.whenSelectionChanged(this::renderSelectionBar);
        itemsView.setLayoutManager(new LinearLayoutManager(context));
        itemsView.setAdapter(itemsAdapter);
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

    public void whenPopupDismissed(PopupOnDismissedListener onPopupDismissed) {
        this.onPopupDismissed = onPopupDismissed;
    }

    @Override
    protected void onShow() {
        worker = new FindEmptyWorker();
        worker.whenStarted(() -> containerView.post(() -> {
            renderStatusBar(StatusBar.IconState.RUNNING);
        }));
        worker.whenUpdated((scanned, matched) -> containerView.post(() -> {
            totalScanned += scanned;
            itemsAdapter.append(matched);
            renderStatusBar(StatusBar.IconState.RUNNING);
        }));
        worker.whenStopped(reason -> containerView.post(() -> {
            renderStatusBar(reason == StopReason.COMPLETED ? StatusBar.IconState.COMPLETED : StatusBar.IconState.STOPPED);
            updateButtons();
        }));
        worker.start(startDirectory);

        updateButtons();
    }

    private void renderStatusBar(StatusBar.IconState iconState) {
        statusBar.render(new StatusBar.State(iconState, context.getString(R.string.x_scanned_y_found,
                totalScanned, itemsAdapter.getItemCount())));
    }
}
