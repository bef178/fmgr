package pd.droidapp.fmgr.popup;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.util.SelectionBar;
import pd.util.PathOps;

import static pd.droidapp.fmgr.popup.PopupFileItemBar.BadgeState;

class PopupFileItemsAdapter extends RecyclerView.Adapter<PopupFileItemsAdapter.ItemViewHolder> {

    private final String startDirectory;
    private final SelectionBar selectionBar;
    private final List<FileProperties> items = new ArrayList<>();

    // more data
    private final List<BadgeState> badgeStates = new ArrayList<>();

    public PopupFileItemsAdapter(String startDirectory, SelectionBar selectionBar) {
        this.startDirectory = startDirectory;
        this.selectionBar = selectionBar;
    }

    public void setItemBadge(int position, BadgeState badgeState) {
        if (position < badgeStates.size()) {
            if (badgeStates.get(position) == badgeState) {
                return;
            }
            badgeStates.set(position, badgeState);
        } else {
            badgeStates.add(badgeState);
        }
        notifyItemChanged(position);
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
        for (int i = items.size() - 1; i >= 0; i--) {
            if (paths.contains(items.get(i).path)) {
                items.remove(i);
                if (i < badgeStates.size()) {
                    badgeStates.remove(i);
                }
                notifyItemRemoved(i);
            }
        }
    }

    public void clear() {
        int oldSize = items.size();
        items.clear();
        badgeStates.clear();
        notifyItemRangeRemoved(0, oldSize);
    }

    public List<FileProperties> getItems() {
        return items;
    }

    public List<FileProperties> getSelectedItems() {
        List<FileProperties> selected = new ArrayList<>();
        if (selectionBar != null) {
            for (FileProperties item : items) {
                if (selectionBar.hasSelected(item)) {
                    selected.add(item);
                }
            }
        }
        return selected;
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.file_item, parent, false);
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

        boolean selected = selectionBar != null && selectionBar.hasSelected(item);
        BadgeState badgeState = position < badgeStates.size() ? badgeStates.get(position) : BadgeState.NONE;
        viewHolder.itemBar.setBadge(selected ? BadgeState.SELECTED : badgeState);

        viewHolder.itemBar.setPath(PathOps.singleton.relativize(startDirectory, item.path));

        viewHolder.itemBar.setIndex(position + 1);

        if (selectionBar != null) {
            viewHolder.itemView.setOnClickListener(v -> {
                if (!selectionBar.isEmpty()) {
                    toggleSelected(item, position);
                }
            });

            viewHolder.itemView.setOnLongClickListener(v -> {
                toggleSelected(item, position);
                return true;
            });

            viewHolder.itemBar.forwardPathViewClicksTo(viewHolder.itemView);
        }
    }

    private void toggleSelected(FileProperties item, int position) {
        if (selectionBar != null) {
            selectionBar.toggleSelected(item);
            notifyItemChanged(position);
            selectionBar.invalidate();
        }
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
