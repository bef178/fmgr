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
import pd.droidapp.fmgr.popup.StatusBar.IconState;
import pd.droidapp.fmgr.popup.StatusBar.State;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.util.SelectionBar;

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
        itemsAdapter.whenSelectionChanged(this::renderSelectionBar);

        titleBar.setTitle(R.string.find_empty);

        initSelectionBar();
        initItemsView();
    }

    private void renderSelectionBar() {
        selectionBar.render(itemsAdapter.getSelectedCount());
    }

    private void initSelectionBar() {
        selectionBar.addButton(R.layout.selection_button_jump, () -> itemsAdapter.getSelectedCount() == 1, v -> {
            if (itemsAdapter.getSelectedCount() == 1) {
                if (onJump != null) {
                    onJump.accept(itemsAdapter.getSelectedItems().get(0).path);
                }
                selfWindow.dismiss();
            }
        });

        selectionBar.addButton(R.layout.selection_button_delete, () -> itemsAdapter.hasSelection(), v -> {
            DeletePopup deletePopup = new DeletePopup(containerView, startDirectory, itemsAdapter.getSelectedItems(), false);
            deletePopup.whenPopupDismissed((added, removed) -> {
                netRemoved.addAll(removed);
                itemsAdapter.remove(removed);
                itemsAdapter.deselect(removed);
            });
            deletePopup.show();
        });

        selectionBar.addButton(R.layout.selection_button_delete_and_prune, () -> itemsAdapter.hasSelection(), v -> {
            DeletePopup deletePopup = new DeletePopup(containerView, startDirectory, itemsAdapter.getSelectedItems(), true);
            deletePopup.whenPopupDismissed((added, removed) -> {
                netRemoved.addAll(removed);
                itemsAdapter.remove(removed);
                itemsAdapter.deselect(removed);
            });
            deletePopup.show();
        });

        selectionBar.addButton(R.layout.selection_button_select_all, () -> itemsAdapter.hasSelection(), v -> {
            itemsAdapter.selectAll();
            itemsAdapter.notifyDataSetChanged();
        });

        selectionBar.addButton(R.layout.selection_button_select_clear, () -> itemsAdapter.hasSelection(), v -> {
            itemsAdapter.clearSelection();
            itemsAdapter.notifyDataSetChanged();
        });
    }

    private void initItemsView() {
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
            renderStatusBar(IconState.RUNNING);
        }));
        worker.whenUpdated((scanned, matched) -> containerView.post(() -> {
            totalScanned += scanned;
            itemsAdapter.append(matched);
            renderStatusBar(IconState.RUNNING);
        }));
        worker.whenStopped(reason -> containerView.post(() -> {
            updateButtons();
            renderStatusBar(reason == StopReason.COMPLETED ? IconState.COMPLETED : IconState.STOPPED);
        }));
        worker.start(startDirectory);

        updateButtons();
    }

    private void renderStatusBar(IconState iconState) {
        statusBar.render(new State(iconState, context.getString(R.string.x_scanned_y_found,
                totalScanned, itemsAdapter.getItemCount())));
    }
}
