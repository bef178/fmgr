package pd.droidapp.fmgr.popup;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.SelectionBar;
import pd.util.PathOps;

class PopupFileItemsAdapter extends RecyclerView.Adapter<PopupFileItemsAdapter.ItemViewHolder> {

    private final String startDirectory;
    private final SelectionBar selectionBar;
    private final List<String> items = new LinkedList<>();

    public PopupFileItemsAdapter(String startDirectory, SelectionBar selectionBar) {
        this.startDirectory = startDirectory;
        this.selectionBar = selectionBar;
    }

    public List<String> getItems() {
        return items;
    }

    public void add(Collection<String> paths) {
        if (paths.isEmpty()) {
            return;
        }
        int start = items.size();
        items.addAll(paths);
        notifyItemRangeInserted(start, paths.size());
    }

    public void remove(Collection<String> paths) {
        List<String> oldItems = new LinkedList<>(items);
        items.removeIf(paths::contains);
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
                return oldItems.get(oldPos).equals(items.get(newPos));
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

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.popup_file_item, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder viewHolder, int position) {
        String path = items.get(position);

        if (path.endsWith("/")) {
            viewHolder.itemBar.setIcon(R.drawable.i_directory_24);
        } else {
            viewHolder.itemBar.setIcon(R.drawable.i_file_24);
        }

        viewHolder.itemBar.setSelected(selectionBar.hasSelected(path));

        viewHolder.itemBar.setPath(PathOps.singleton.relativize(startDirectory, path));

        viewHolder.itemBar.setIndex(position + 1);

        viewHolder.itemView.setOnClickListener(v -> {
            if (!selectionBar.isEmpty()) {
                toggleSelected(path, position);
            }
        });

        viewHolder.itemView.setOnLongClickListener(v -> {
            toggleSelected(path, position);
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

    public static class ItemViewHolder extends RecyclerView.ViewHolder {

        final PopupFileItemBar itemBar;

        ItemViewHolder(View view) {
            super(view);
            itemBar = new PopupFileItemBar(view);
        }
    }
}
