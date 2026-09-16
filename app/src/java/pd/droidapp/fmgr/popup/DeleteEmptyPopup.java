package pd.droidapp.fmgr.popup;

import android.view.View;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.popup.ProcessingWorker.StopReason;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.util.SelectionBar;

public class DeleteEmptyPopup extends ProcessingPopup {

    private final String startDirectory;

    // views
    private final StatusBar statusBar;
    private final SelectionBar selectionBar;
    private final RecyclerView itemsView;
    private final PopupFileItemsAdapter itemsAdapter;

    // callbacks
    private Consumer<String> onJump;
    private PopupOnDismissedListener onPopupDismissed;

    private DeleteEmptyWorker worker;
    private int totalScanned;
    private final Collection<FileProperties> netRemoved = new LinkedList<>();

    public DeleteEmptyPopup(View containerView, String startDirectory) {
        super(containerView, R.layout.delete_empty_popup);
        this.startDirectory = startDirectory;

        statusBar = new StatusBar(mainAreaView.findViewById(R.id.status_bar));
        selectionBar = new SelectionBar(mainAreaView.findViewById(R.id.selection_bar));
        itemsView = mainAreaView.findViewById(R.id.popup_items_list);
        itemsAdapter = new PopupFileItemsAdapter(startDirectory, selectionBar);

        titleBar.setTitle(R.string.delete_empty_files);

        initSelectionBar();
        initItemsView();
    }

    private void initSelectionBar() {
        selectionBar.addButton(R.layout.selection_button_jump, c -> c == 1, v -> {
            if (selectionBar.size() == 1) {
                if (onJump != null) {
                    onJump.accept(itemsAdapter.getSelectedItems().get(0).path);
                }
                selfWindow.dismiss();
            }
        });

        selectionBar.addButton(R.layout.selection_button_delete, c -> c > 0, v -> {
            DeletePopup deletePopup = new DeletePopup(containerView, itemsAdapter.getSelectedItems(), false);
            deletePopup.whenPopupDismissed((added, removed) -> {
                netRemoved.addAll(removed);
                itemsAdapter.remove(removed);
                selectionBar.remove(removed.stream()
                        .map(item -> item.path)
                        .collect(Collectors.toList()));
                selectionBar.invalidate();
            });
            deletePopup.show();
        });

        selectionBar.addButton(R.layout.selection_button_delete_and_prune, c -> c > 0, v -> {
            DeletePopup deletePopup = new DeletePopup(containerView, itemsAdapter.getSelectedItems(), true);
            deletePopup.whenPopupDismissed((added, removed) -> {
                netRemoved.addAll(removed);
                itemsAdapter.remove(removed);
                selectionBar.remove(removed.stream()
                        .map(item -> item.path)
                        .collect(Collectors.toList()));
                selectionBar.invalidate();
            });
            deletePopup.show();
        });

        selectionBar.addButton(R.layout.selection_button_select_all, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.add(itemsAdapter.getItems().stream()
                    .map(item -> item.path)
                    .collect(Collectors.toList()));
            selectionBar.invalidate();
            itemsAdapter.notifyDataSetChanged();
        });

        selectionBar.addButton(R.layout.selection_button_select_clear, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.invalidate();
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
        worker = new DeleteEmptyWorker();
        worker.whenStarted(() -> containerView.post(() -> {
            statusBar.markRunning();
            statusBar.setText(context.getString(R.string.scanning));
            selectionBar.invalidate();
        }));
        worker.whenUpdated((scanned, matched) -> containerView.post(() -> {
            totalScanned += scanned;
            itemsAdapter.append(matched);
            selectionBar.invalidate();
            statusBar.setText(context.getString(R.string.x_scanned_y_found,
                    totalScanned, itemsAdapter.getItemCount()));
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
}
