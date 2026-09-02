package pd.droidapp.fmgr.util;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import pd.droidapp.fmgr.R;
import pd.util.PathOps;

public class PopupFileItemsAdapter extends RecyclerView.Adapter<PopupFileItemsAdapter.ItemViewHolder> {

    private final File startDirectory;
    private final Set<File> selectedFiles;
    private final List<File> items = new LinkedList<>();
    private Runnable onItemFileToggled;

    public PopupFileItemsAdapter(File startDirectory, Set<File> selectedFiles) {
        this.startDirectory = startDirectory;
        this.selectedFiles = selectedFiles;
    }

    public void whenItemFileToggled(Runnable onItemFileToggled) {
        this.onItemFileToggled = onItemFileToggled;
    }

    public void addAll(List<String> paths) {
        if (paths.isEmpty()) {
            return;
        }
        int start = items.size();
        for (String path : paths) {
            items.add(new File(path));
        }
        notifyItemRangeInserted(start, paths.size());
    }

    public void removeAll(Collection<File> files) {
        List<File> oldFiles = new LinkedList<>(items);
        items.removeAll(files);
        diffAndDispatch(oldFiles);
    }

    public void clear() {
        int oldSize = items.size();
        items.clear();
        notifyItemRangeRemoved(0, oldSize);
    }

    private void invalidateItem(File item) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).equals(item)) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void invalidateItems(Iterable<File> items) {
        for (File item : items) {
            invalidateItem(item);
        }
    }

    private void diffAndDispatch(List<File> oldFiles) {
        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldFiles.size();
            }

            @Override
            public int getNewListSize() {
                return items.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldFiles.get(oldPos).equals(items.get(newPos));
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                return oldPos == newPos;
            }

            @Nullable
            @Override
            public Object getChangePayload(int oldPos, int newPos) {
                return Boolean.TRUE;
            }
        }).dispatchUpdatesTo(this);
    }

    public List<File> copyItems() {
        return new LinkedList<>(items);
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.popup_file_item, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder viewHolder, int position) {
        File file = items.get(position);

        if (file.isDirectory()) {
            viewHolder.fileItem.setIcon(R.drawable.i_directory_24);
        } else {
            viewHolder.fileItem.setIcon(R.drawable.i_file_24);
        }

        viewHolder.fileItem.setSelected(selectedFiles.contains(file));

        viewHolder.fileItem.setPath(PathOps.singleton.relativize(startDirectory.getPath(), file.getPath()));

        viewHolder.fileItem.setIndex(position + 1);

        viewHolder.itemView.setOnClickListener(v -> {
            if (!selectedFiles.isEmpty()) {
                toggleSelected(file, position);
            }
        });

        viewHolder.itemView.setOnLongClickListener(v -> {
            toggleSelected(file, position);
            return true;
        });

        viewHolder.fileItem.forwardPathViewClicksTo(viewHolder.itemView);
    }

    private void toggleSelected(File file, int position) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file);
        } else {
            selectedFiles.add(file);
        }
        notifyItemChanged(position);
        if (onItemFileToggled != null) {
            onItemFileToggled.run();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ItemViewHolder extends RecyclerView.ViewHolder {

        final PopupFileItem fileItem;

        ItemViewHolder(View view) {
            super(view);
            fileItem = new PopupFileItem(view);
        }
    }
}
