package pd.droidapp.fmgr.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;

public class DeleteEmptyPopup {

    private final Context context;
    private final View containerView;
    private final File startDirectory;

    private final StatusBar statusBar;
    private final SelectionBar selectionBar;
    private Consumer<File> onJump;
    private BiFunction<Collection<File>, Boolean, Collection<File>> onDelete;
    private final PopupFileItemAdapter itemAdapter;
    private final PopupWindow popupWindow;

    private FileScanUpdater scanner;
    private int totalScanned;

    public DeleteEmptyPopup(View containerView, File startDirectory) {
        this.context = Objects.requireNonNull(containerView, "containerView").getContext();
        this.containerView = containerView;
        this.startDirectory = startDirectory;

        View popupView = LayoutInflater.from(context).inflate(
                R.layout.delete_empty_popup,
                (ViewGroup) containerView,
                false);

        View popupArea = popupView.findViewById(R.id.popup_area);

        PopupTitleBar titleBar = new PopupTitleBar(popupView.findViewById(R.id.popup_title_bar));
        titleBar.setTitle(R.string.delete_empty_files);
        titleBar.whenCloseButtonClicked(v -> dismiss());

        RecyclerView filesListView = popupView.findViewById(R.id.files_list);

        statusBar = new StatusBar(popupView.findViewById(R.id.status_bar));

        selectionBar = new SelectionBar(popupView.findViewById(R.id.selection_bar));

        itemAdapter = new PopupFileItemAdapter(startDirectory, selectionBar.selectedFiles);
        itemAdapter.whenItemFileToggled(selectionBar::invalidate);

        selectionBar.addButton(R.layout.selection_button_jump, c -> c == 1, v -> {
            if (selectionBar.selectedFiles.size() == 1) {
                File file = selectionBar.selectedFiles.iterator().next();
                if (onJump != null) {
                    onJump.accept(file);
                }
                dismiss();
            }
        });

        selectionBar.addButton(R.layout.selection_button_delete, c -> c > 0, v -> {
            if (onDelete != null) {
                List<File> selected = selectionBar.copySelectedFiles();
                Collection<File> removed = onDelete.apply(selected, false);
                itemAdapter.removeAll(removed);
            }
            selectionBar.clear();
            selectionBar.invalidate();
        });

        selectionBar.addButton(R.layout.selection_button_delete_and_prune, c -> c > 0, v -> {
            if (onDelete != null) {
                List<File> selected = selectionBar.copySelectedFiles();
                Collection<File> removed = onDelete.apply(selected, true);
                itemAdapter.removeAll(removed);
            }
            selectionBar.clear();
            selectionBar.invalidate();
        });

        selectionBar.addButton(R.layout.selection_button_select_all, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.selectedFiles.addAll(itemAdapter.copyItems());
            selectionBar.invalidate();
            itemAdapter.notifyDataSetChanged();
        });

        selectionBar.addButton(R.layout.selection_button_select_clear, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.invalidate();
            itemAdapter.notifyDataSetChanged();
        });

        filesListView.setLayoutManager(new LinearLayoutManager(context));
        filesListView.setAdapter(itemAdapter);

        popupView.setOnClickListener(v -> dismiss());
        popupArea.setOnClickListener(v -> {});

        popupWindow = new PopupWindow(popupView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                true);
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(24);
    }

    public void whenJumpClicked(Consumer<File> onJump) {
        this.onJump = onJump;
    }

    public void whenDeleteClicked(BiFunction<Collection<File>, Boolean, Collection<File>> onDelete) {
        this.onDelete = onDelete;
    }

    public void show() {
        containerView.post(() -> {
            popupWindow.showAtLocation(containerView, Gravity.NO_GRAVITY, 0, 0);
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
            itemAdapter.addAll(delta);
            selectionBar.invalidate();
            statusBar.setText(context.getString(R.string.x_scanned_y_found,
                    totalScanned, itemAdapter.getItemCount()));
        }));
        scanner.whenScanStopped(() -> containerView.post(statusBar::markDone));
        scanner.start(startDirectory);
    }

    public void dismiss() {
        if (scanner != null) {
            scanner.cancel();
        }
        popupWindow.dismiss();
    }
}
