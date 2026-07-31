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
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
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

    // callbacks
    private Consumer<File> onJump;
    private BiFunction<Collection<File>, Boolean, Collection<File>> onDelete;
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
                true);
        mainAreaView = selfView.findViewById(R.id.popup_area);

        titleBar = new PopupTitleBar(mainAreaView.findViewById(R.id.popup_title_bar));
        statusBar = new StatusBar(mainAreaView.findViewById(R.id.status_bar));
        selectionBar = new SelectionBar(mainAreaView.findViewById(R.id.selection_bar));
        itemsView = mainAreaView.findViewById(R.id.files_list);
        itemsAdapter = new PopupFileItemsAdapter(startDirectory, selectionBar.selectedFiles);

        initPopupWindow();
        enableClosePopupOnOutsideTouch();
        initPopupTitleBar();
        initSelectionBar();
        initItemsView();
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
            if (selectionBar.selectedFiles.size() == 1) {
                File file = selectionBar.selectedFiles.iterator().next();
                if (onJump != null) {
                    onJump.accept(file);
                }
                selfWindow.dismiss();
            }
        });

        selectionBar.addButton(R.layout.selection_button_delete, c -> c > 0, v -> {
            if (onDelete != null) {
                List<File> selected = selectionBar.copySelectedFiles();
                Collection<File> removed = onDelete.apply(selected, false);
                removedFiles.addAll(removed);
                itemsAdapter.removeAll(removed);
            }
            selectionBar.clear();
            selectionBar.invalidate();
        });

        selectionBar.addButton(R.layout.selection_button_delete_and_prune, c -> c > 0, v -> {
            if (onDelete != null) {
                List<File> selected = selectionBar.copySelectedFiles();
                Collection<File> removed = onDelete.apply(selected, true);
                removedFiles.addAll(removed);
                itemsAdapter.removeAll(removed);
            }
            selectionBar.clear();
            selectionBar.invalidate();
        });

        selectionBar.addButton(R.layout.selection_button_select_all, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.selectedFiles.addAll(itemsAdapter.copyItems());
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

    public void whenJumpClicked(Consumer<File> onJump) {
        this.onJump = onJump;
    }

    public void whenDeleteClicked(BiFunction<Collection<File>, Boolean, Collection<File>> onDelete) {
        this.onDelete = onDelete;
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
        scanner.whenDirectoryReached(directory -> {
            File[] children = directory.listFiles();
            return children == null || children.length == 0;
        });
        scanner.whenFileReached(file -> file.length() == 0);
        scanner.whenScanStarted(() -> containerView.post(() -> {
            statusBar.markRunning();
            statusBar.setText(context.getString(R.string.scanning));
            selectionBar.invalidate();
        }));
        scanner.whenScanUpdated((delta, scanned) -> containerView.post(() -> {
            totalScanned += scanned;
            itemsAdapter.addAll(delta);
            selectionBar.invalidate();
            statusBar.setText(context.getString(R.string.x_scanned_y_found,
                    totalScanned, itemsAdapter.getItemCount()));
        }));
        scanner.whenScanStopped(() -> containerView.post(() -> {
            if (scanner.isCompleted()) {
                statusBar.markDone();
            }
        }));
        scanner.start(startDirectory);
    }
}
