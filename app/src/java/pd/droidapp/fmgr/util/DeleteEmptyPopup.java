package pd.droidapp.fmgr.util;

import android.view.View;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;

public class DeleteEmptyPopup extends ProcessingPopup {

    private final File startDirectory;

    // views
    private final StatusBar statusBar;
    private final SelectionBar selectionBar;
    private final RecyclerView itemsView;
    private final PopupFileItemsAdapter itemsAdapter;

    // callbacks
    private Consumer<File> onJump;
    private PopupOnDismissedListener onPopupDismissed;

    private FileScanUpdater scanner;
    private int totalScanned;
    private final Collection<File> removedFiles = new LinkedList<>();

    public DeleteEmptyPopup(View containerView, File startDirectory) {
        super(containerView, R.layout.delete_empty_popup);
        this.startDirectory = startDirectory;

        statusBar = new StatusBar(mainAreaView.findViewById(R.id.status_bar));
        selectionBar = new SelectionBar(mainAreaView.findViewById(R.id.selection_bar));
        itemsView = mainAreaView.findViewById(R.id.files_list);
        itemsAdapter = new PopupFileItemsAdapter(startDirectory, selectionBar.selectedItems);

        titleBar.setTitle(R.string.delete_empty_files);

        initSelectionBar();
        initItemsView();
    }

    private void initSelectionBar() {
        selectionBar.addButton(R.layout.selection_button_jump, c -> c == 1, v -> {
            if (selectionBar.selectedItems.size() == 1) {
                File file = selectionBar.selectedItems.iterator().next();
                if (onJump != null) {
                    onJump.accept(file);
                }
                selfWindow.dismiss();
            }
        });

        selectionBar.addButton(R.layout.selection_button_delete, c -> c > 0, v -> {
            DeletePopup deletePopup = new DeletePopup(containerView, selectionBar.copySelectedItems(), false);
            deletePopup.whenPopupDismissed(removed -> {
                removedFiles.addAll(removed);
                itemsAdapter.removeAll(removed);
                selectionBar.selectedItems.removeAll(removed);
                selectionBar.invalidate();
            });
            deletePopup.show();
        });

        selectionBar.addButton(R.layout.selection_button_delete_and_prune, c -> c > 0, v -> {
            DeletePopup deletePopup = new DeletePopup(containerView, selectionBar.copySelectedItems(), true);
            deletePopup.whenPopupDismissed(removed -> {
                removedFiles.addAll(removed);
                itemsAdapter.removeAll(removed);
                selectionBar.selectedItems.removeAll(removed);
                selectionBar.invalidate();
            });
            deletePopup.show();
        });

        selectionBar.addButton(R.layout.selection_button_select_all, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.selectedItems.addAll(itemsAdapter.copyItems());
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
        itemsAdapter.whenItemFileToggled(selectionBar::invalidate);
    }

    @Override
    protected void initPopupButtons() {
        super.initPopupButtons();
        buttonBar.addButton(R.string.abort, () -> true, this::isProcessing, v -> {
            if (scanner != null) {
                scanner.cancel();
            }
        });
    }

    @Override
    protected boolean isProcessing() {
        return scanner != null && scanner.isRunning();
    }

    @Override
    protected void onDismissed() {
        if (scanner != null) {
            scanner.cancel();
        }
        if (onPopupDismissed != null) {
            onPopupDismissed.accept(removedFiles);
        }
    }

    public void whenJumpClicked(Consumer<File> onJump) {
        this.onJump = onJump;
    }

    public void whenPopupDismissed(PopupOnDismissedListener onPopupDismissed) {
        this.onPopupDismissed = onPopupDismissed;
    }

    @Override
    protected void onShow() {
        doScan();
    }

    private void doScan() {
        scanner = new FileScanUpdater();
        scanner.whenReached(path -> {
            File file = new File(path);
            if (path.endsWith("/")) {
                File[] children = file.listFiles();
                return children == null || children.length == 0;
            }
            return file.length() == 0;
        });
        scanner.whenScanStarted(() -> containerView.post(() -> {
            statusBar.markRunning();
            statusBar.setText(context.getString(R.string.scanning));
            selectionBar.invalidate();
        }));
        scanner.whenScanUpdated((scanned, delta) -> containerView.post(() -> {
            totalScanned += scanned;
            itemsAdapter.addAll(delta);
            selectionBar.invalidate();
            statusBar.setText(context.getString(R.string.x_scanned_y_found,
                    totalScanned, itemsAdapter.getItemCount()));
        }));
        scanner.whenScanStopped(() -> containerView.post(() -> {
            updateButtons();
            if (scanner.isCompleted()) {
                statusBar.markDone();
            } else {
                statusBar.markStopped();
            }
        }));
        scanner.start(startDirectory.getPath());
        updateButtons();
    }
}
