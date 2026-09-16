package pd.droidapp.fmgr.popup;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.util.SelectionBar;
import pd.util.PathOps;

class PopupFileItemsAdapter extends RecyclerView.Adapter<PopupFileItemsAdapter.ItemViewHolder> {

    private final String startDirectory;
    private final SelectionBar selectionBar;
    private final List<FileProperties> items = new LinkedList<>();

    public PopupFileItemsAdapter(String startDirectory, SelectionBar selectionBar) {
        this.startDirectory = startDirectory;
        this.selectionBar = selectionBar;
    }

    public List<FileProperties> getItems() {
        return items;
    }

    public void append(Collection<FileProperties> newItems) {
        if (newItems.isEmpty()) {
            return;
        }
        int start = items.size();
        items.addAll(newItems);
        notifyItemRangeInserted(start, newItems.size());
    }

    public void remove(Collection<FileProperties> removedItems) {
        Set<String> paths = new HashSet<>();
        for (FileProperties item : removedItems) {
            paths.add(item.path);
        }
        List<FileProperties> oldItems = new LinkedList<>(items);
        items.removeIf(item -> paths.contains(item.path));
        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return items.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldItems.get(oldPos).path.equals(items.get(newPos).path);
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                return oldPos == newPos;
            }

            @Override
            public Object getChangePayload(int oldPos, int newPos) {
                return Boolean.TRUE;
            }
        }).dispatchUpdatesTo(this);
    }

    public void clear() {
        int oldSize = items.size();
        items.clear();
        notifyItemRangeRemoved(0, oldSize);
    }

    public List<FileProperties> getSelectedItems() {
        List<FileProperties> selected = new LinkedList<>();
        for (FileProperties item : items) {
            if (selectionBar.hasSelected(item.path)) {
                selected.add(item);
            }
        }
        return selected;
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
        FileProperties item = items.get(position);

        if (item.isDirectory) {
            viewHolder.itemBar.setIcon(R.drawable.i_directory_24);
        } else {
            viewHolder.itemBar.setIcon(R.drawable.i_file_24);
        }

        viewHolder.itemBar.setSelected(selectionBar.hasSelected(item.path));

        viewHolder.itemBar.setPath(PathOps.singleton.relativize(startDirectory, item.path));

        viewHolder.itemBar.setIndex(position + 1);

        viewHolder.itemView.setOnClickListener(v -> {
            if (!selectionBar.isEmpty()) {
                toggleSelected(item.path, position);
            }
        });

        viewHolder.itemView.setOnLongClickListener(v -> {
            toggleSelected(item.path, position);
            return true;
        });

        viewHolder.itemBar.forwardPathViewClicksTo(viewHolder.itemView);
    }

    private void toggleSelected(String path, int position) {
        selectionBar.toggleSelected(path);
        notifyItemChanged(position);
        selectionBar.invalidate();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {

        final PopupFileItemBar itemBar;

        ItemViewHolder(View view) {
            super(view);
            itemBar = new PopupFileItemBar(view);
        }
    }
}
