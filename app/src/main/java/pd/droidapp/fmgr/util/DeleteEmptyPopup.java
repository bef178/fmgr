package pd.droidapp.fmgr.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.getRelativePath;

public class DeleteEmptyPopup {

    private final Context context;
    private final View containerView;
    private final File startDirectory;
    private OnDelete onDelete;

    private final PopupWindow popupWindow;
    private final SelectionBar selectionBar;
    private final StatusBar statusBar;

    private final EmptyItemAdapter emptyItemAdapter;

    private Scanner scanner;

    public DeleteEmptyPopup(View containerView, File startDirectory) {
        this.context = Objects.requireNonNull(containerView, "containerView").getContext();
        this.containerView = containerView;
        this.startDirectory = startDirectory;

        View popupView = LayoutInflater.from(context).inflate(
                R.layout.delete_empty_popup,
                (ViewGroup) containerView,
                false);

        View popupArea = popupView.findViewById(R.id.popup_area);
        ImageButton closeButton = popupView.findViewById(R.id.action_close);

        RecyclerView filesListView = popupView.findViewById(R.id.files_list);

        statusBar = new StatusBar(popupView.findViewById(R.id.status_bar));

        selectionBar = new SelectionBar(popupView.findViewById(R.id.selection_bar));

        emptyItemAdapter = new EmptyItemAdapter(startDirectory, selectionBar.selectedFiles);
        emptyItemAdapter.whenEmptyItemClicked(selectionBar::invalidate);

        closeButton.setOnClickListener(v -> dismiss());

        selectionBar.addButton(R.layout.selection_button_delete, c -> c > 0, v -> {
            if (onDelete != null) {
                onDelete.accept(selectionBar.copySelectedFiles(), false);
            }
            dismiss();
        });

        selectionBar.addButton(R.layout.selection_button_delete_and_prune, c -> c > 0, v -> {
            if (onDelete != null) {
                onDelete.accept(selectionBar.copySelectedFiles(), true);
            }
            dismiss();
        });

        selectionBar.addButton(R.layout.selection_button_select_all, c -> c > 0, v -> {
            selectionBar.clear();
            for (EmptyItem item : emptyItemAdapter.emptyItems) {
                selectionBar.selectedFiles.add(item.file);
            }
            selectionBar.invalidate();
            emptyItemAdapter.notifyDataSetChanged();
        });

        selectionBar.addButton(R.layout.selection_button_select_clear, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.invalidate();
            emptyItemAdapter.notifyDataSetChanged();
        });

        filesListView.setLayoutManager(new LinearLayoutManager(context));
        filesListView.setAdapter(emptyItemAdapter);

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

    public void whenDeleteClicked(OnDelete onDelete) {
        this.onDelete = onDelete;
    }

    public void show() {
        containerView.post(() -> {
            popupWindow.showAtLocation(containerView, Gravity.NO_GRAVITY, 0, 0);
            doScan();
        });
    }

    private void doScan() {
        scanner = new Scanner();
        scanner.start(startDirectory);
    }

    void updateEmptyItems() {
        emptyItemAdapter.invalidate(scanner.copyEmptyItems());
    }

    public void dismiss() {
        if (scanner != null) {
            scanner.cancel();
        }
        popupWindow.dismiss();
    }

    public interface OnDelete {

        void accept(Collection<File> files, boolean parents);
    }

    static class EmptyItem {

        final File file;
        final boolean isDirectory;

        EmptyItem(File file, boolean isDirectory) {
            this.file = file;
            this.isDirectory = isDirectory;
        }
    }

    class Scanner extends FileScanner {

        final List<EmptyItem> emptyItems = Collections.synchronizedList(new ArrayList<>());

        @Override
        protected void onScanStarted() {
            containerView.post(() -> {
                statusBar.markRunning(context.getString(R.string.scanning));
                selectionBar.invalidate();
            });
        }

        @Override
        protected void onScanUpdated(int numFilesScanned) {
            containerView.post(() -> {
                updateEmptyItems();
                selectionBar.invalidate();
                if (isCompleted()) {
                    statusBar.markDone();
                }
                statusBar.setText(context.getString(R.string.x_scanned_y_found,
                        numFilesScanned, emptyItemAdapter.getItemCount()));
            });
        }

        @Override
        protected void onFile(File file) {
            if (file.length() == 0) {
                emptyItems.add(new EmptyItem(file, false));
            }
        }

        @Override
        protected boolean onDirectory(File directory) {
            File[] children = directory.listFiles();
            if (children == null || children.length == 0) {
                emptyItems.add(new EmptyItem(directory, true));
                return false;
            }
            return true;
        }

        List<EmptyItem> copyEmptyItems() {
            synchronized (emptyItems) {
                return new ArrayList<>(emptyItems);
            }
        }
    }

    static class EmptyItemAdapter extends RecyclerView.Adapter<EmptyItemAdapter.ViewHolder> {

        private final Set<File> selectedFiles;
        final List<EmptyItem> emptyItems = new LinkedList<>();
        private final File startDirectory;
        private Runnable onEmptyItemClicked;

        EmptyItemAdapter(File startDirectory, Set<File> selectedFiles) {
            this.startDirectory = startDirectory;
            this.selectedFiles = selectedFiles;
        }

        void whenEmptyItemClicked(Runnable onEmptyItemClicked) {
            this.onEmptyItemClicked = onEmptyItemClicked;
        }

        void invalidate(List<EmptyItem> emptyItems) {
            this.emptyItems.clear();
            this.emptyItems.addAll(emptyItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.popup_file, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            EmptyItem item = emptyItems.get(position);
            boolean selected = selectedFiles.contains(item.file);

            holder.indexText.setText(String.valueOf(position + 1));

            if (item.isDirectory) {
                holder.icon.setImageResource(R.drawable.i_directory_24);
            } else {
                holder.icon.setImageResource(R.drawable.i_file_24);
            }

            String name = getRelativePath(startDirectory, item.file);
            if (item.isDirectory) {
                name = name + "/";
            }
            holder.nameText.setText(name);

            holder.selectedIcon.setVisibility(selected ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> {
                // selecting only starts once something is selected (e.g. via long-press)
                if (!selectedFiles.isEmpty()) {
                    toggleSelected(item.file, position);
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                toggleSelected(item.file, position);
                return true;
            });
        }

        private void toggleSelected(File file, int position) {
            if (selectedFiles.contains(file)) {
                selectedFiles.remove(file);
            } else {
                selectedFiles.add(file);
            }
            notifyItemChanged(position);
            if (onEmptyItemClicked != null) {
                onEmptyItemClicked.run();
            }
        }

        @Override
        public int getItemCount() {
            return emptyItems.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {

            final ImageView icon;
            final ImageView selectedIcon;
            final TextView nameText;
            final TextView indexText;

            ViewHolder(View itemView) {
                super(itemView);
                indexText = itemView.findViewById(R.id.popup_file_index);
                icon = itemView.findViewById(R.id.popup_file_icon);
                selectedIcon = itemView.findViewById(R.id.popup_file_selected);
                nameText = itemView.findViewById(R.id.popup_file_name);
            }
        }
    }
}
