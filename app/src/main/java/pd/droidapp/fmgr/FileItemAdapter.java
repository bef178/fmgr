package pd.droidapp.fmgr;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class FileItemAdapter extends RecyclerView.Adapter<FileItemAdapter.FileItemViewHolder> {

    private final List<FileItem> fileItems = new ArrayList<>();
    private OnFileClickedListener onFileClickedListener;

    public void whenFileClicked(OnFileClickedListener onFileClickedListener) {
        this.onFileClickedListener = onFileClickedListener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void invalidate(File directory) {
        fileItems.clear();
        fileItems.addAll(getFileItems(directory));
        notifyDataSetChanged();
    }

    private List<FileItem> getFileItems(File directory) {
        if (directory == null) {
            return new LinkedList<>();
        }

        File[] files = directory.listFiles();
        if (files == null) {
            // possible not directory or no privilege
            return new LinkedList<>();
        }

        return Arrays.stream(files)
                .filter(f -> !f.getName().startsWith("."))
                .sorted((f1, f2) -> {
                    if (f1.isDirectory() && !f2.isDirectory()) {
                        return -1;
                    } else if (!f1.isDirectory() && f2.isDirectory()) {
                        return 1;
                    } else {
                        return f1.getName().compareToIgnoreCase(f2.getName());
                    }
                })
                .map(FileItem::new)
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public FileItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.file_item, parent, false);
        return new FileItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileItemViewHolder viewHolder, int position) {
        FileItem fileItem = fileItems.get(position);
        viewHolder.fileNameTextView.setText(fileItem.getFile().getName());
        if (fileItem.getFile().isDirectory()) {
            viewHolder.fileIconImageView.setImageResource(R.drawable.i_directory_24);
            viewHolder.fileDetailsTextView.setText(getDirectoryDetailsString(fileItem.getNumOrdinaryItems(), fileItem.getNumHiddenItems()));
        } else {
            viewHolder.fileIconImageView.setImageResource(R.drawable.i_file_24);
            String sizeText = getFileDetailsString(fileItem.getSize());
            viewHolder.fileDetailsTextView.setText(sizeText);
        }

        viewHolder.itemView.setOnClickListener(v -> {
            if (onFileClickedListener != null) {
                onFileClickedListener.onFileClicked(fileItem.getFile());
            }
        });
    }

    private String getDirectoryDetailsString(int numOrdinary, int numHidden) {
        if (numOrdinary < 0 || numHidden < 0) {
            return "Error";
        }

        String ordinaryItemsString;
        {
            if (numOrdinary == 0) {
                ordinaryItemsString = null;
            } else if (numOrdinary == 1) {
                ordinaryItemsString = numOrdinary + " item";
            } else {
                ordinaryItemsString = numOrdinary + " items";
            }
        }

        String hiddenItemsString = numHidden == 0 ? null : numHidden + " hidden";

        if (ordinaryItemsString == null) {
            return hiddenItemsString == null ? "Empty" : hiddenItemsString;
        } else {
            return hiddenItemsString == null ? ordinaryItemsString : ordinaryItemsString + " + " + hiddenItemsString;
        }
    }

    private String getFileDetailsString(long size) {
        if (size < 0) {
            return "Error";
        } else if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format(Locale.getDefault(), "%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    @Override
    public int getItemCount() {
        return fileItems.size();
    }

    public interface OnFileClickedListener {

        void onFileClicked(File file);
    }

    public static class FileItemViewHolder extends RecyclerView.ViewHolder {

        private final ImageView fileIconImageView;
        private final TextView fileNameTextView;
        private final TextView fileDetailsTextView;

        public FileItemViewHolder(@NonNull View itemView) {
            super(itemView);
            fileIconImageView = itemView.findViewById(R.id.file_icon);
            fileNameTextView = itemView.findViewById(R.id.file_name);
            fileDetailsTextView = itemView.findViewById(R.id.file_details);
        }
    }

    public static class FileItem {

        private final File file;

        private final long size;

        private final int numOrdinaryItems;

        private final int numHiddenItems;

        public FileItem(File file) {
            this.file = file;
            this.size = file.length();

            int ordinarys = 0;
            int hiddens = 0;
            if (file.isDirectory()) {
                File[] subitems = file.listFiles();
                if (subitems != null) {
                    for (File subitem : subitems) {
                        if (subitem.getName().startsWith(".")) {
                            hiddens++;
                        } else {
                            ordinarys++;
                        }
                    }
                }
            }
            this.numOrdinaryItems = ordinarys;
            this.numHiddenItems = hiddens;
        }

        public File getFile() {
            return file;
        }

        public long getSize() {
            return size;
        }

        public int getNumOrdinaryItems() {
            return numOrdinaryItems;
        }

        public int getNumHiddenItems() {
            return numHiddenItems;
        }
    }
}
