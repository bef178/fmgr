package pd.droidapp.fmgr.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Objects;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;

public class DeleteEmptyPopup {

    private final Context context;
    private final View containerView;
    private final File startDirectory;

    // views
    private final View selfView;
    private final PopupWindow selfWindow;
    private final LinearLayout mainAreaView;
    private final PopupTitleBar titleBar;
    private final StatusBar statusBar;
    private final SelectionBar selectionBar;
    private final RecyclerView itemsView;
    private final PopupFileItemsAdapter itemsAdapter;
    private final PopupButtonBar buttonBar;

    // callbacks
    private Consumer<File> onJump;
    private PopupOnDismissListener onDismiss;

    private FileScanUpdater scanner;
    private int totalScanned;
    private final Collection<File> removedFiles = new LinkedList<>();

    public DeleteEmptyPopup(View containerView, File startDirectory) {
        this.context = Objects.requireNonNull(containerView, "containerView").getContext();
        this.containerView = containerView;
        this.startDirectory = startDirectory;

        selfView = LayoutInflater.from(context).inflate(
                R.layout.delete_empty_popup,
                (ViewGroup) containerView,
                false);
        selfWindow = new PopupWindow(selfView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                true) {
            @Override
            public void dismiss() {
                if (scanner != null && scanner.isRunning()) {
                    return;
                }
                super.dismiss();
            }
        };
        mainAreaView = selfView.findViewById(R.id.popup_area);

        titleBar = new PopupTitleBar(mainAreaView.findViewById(R.id.popup_title_bar));
        statusBar = new StatusBar(mainAreaView.findViewById(R.id.status_bar));
        selectionBar = new SelectionBar(mainAreaView.findViewById(R.id.selection_bar));
        itemsView = mainAreaView.findViewById(R.id.files_list);
        itemsAdapter = new PopupFileItemsAdapter(startDirectory, selectionBar.selectedItems);
        buttonBar = new PopupButtonBar(mainAreaView.findViewById(R.id.popup_button_bar));

        initPopupWindow();
        enableClosePopupOnOutsideTouch();
        initPopupTitleBar();
        initSelectionBar();
        initItemsView();
        initPopupButtonBar();
    }

    private void initPopupWindow() {
        selfWindow.setOutsideTouchable(false);
        selfWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        selfWindow.setElevation(24);
        selfWindow.setOnDismissListener(() -> {
            if (scanner != null) {
                scanner.cancel();
            }
            if (onDismiss != null) {
                onDismiss.accept(removedFiles);
            }
        });
    }

    private void enableClosePopupOnOutsideTouch() {
        selfView.setOnClickListener(v -> selfWindow.dismiss());
        mainAreaView.setOnClickListener(v -> {});
    }

    private void initPopupTitleBar() {
        titleBar.setTitle(R.string.delete_empty_files);
        titleBar.whenCloseButtonClicked(v -> selfWindow.dismiss());
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
            deletePopup.whenDismissClicked(removed -> {
                removedFiles.addAll(removed);
                itemsAdapter.removeAll(removed);
                selectionBar.selectedItems.removeAll(removed);
                selectionBar.invalidate();
            });
            deletePopup.show();
        });

        selectionBar.addButton(R.layout.selection_button_delete_and_prune, c -> c > 0, v -> {
            DeletePopup deletePopup = new DeletePopup(containerView, selectionBar.copySelectedItems(), true);
            deletePopup.whenDismissClicked(removed -> {
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

    private void initPopupButtonBar() {
        buttonBar.addButton(R.string.abort, () -> true, this::isScanning, v -> {
            if (scanner != null) {
                scanner.cancel();
            }
        });
        updateButtons();
    }

    private void updateButtons() {
        buttonBar.invalidate();
        titleBar.enableCloseButton(!isScanning());
    }

    private boolean isScanning() {
        return scanner != null && scanner.isRunning();
    }

    public void whenJumpClicked(Consumer<File> onJump) {
        this.onJump = onJump;
    }

    public void whenDismissClicked(PopupOnDismissListener onDismiss) {
        this.onDismiss = onDismiss;
    }

    public void show() {
        containerView.post(() -> {
            selfWindow.showAtLocation(containerView, Gravity.NO_GRAVITY, 0, 0);
            doScan();
        });
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
