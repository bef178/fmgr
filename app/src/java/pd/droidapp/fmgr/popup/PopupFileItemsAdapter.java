package pd.droidapp.fmgr.popup;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.FileProperties;
import pd.util.PathOps;

import static pd.droidapp.fmgr.popup.PopupFileItemBar.BadgeState;

class PopupFileItemsAdapter extends RecyclerView.Adapter<PopupFileItemsAdapter.ItemViewHolder> {

    private final String startDirectory;
    private final boolean selectable;
    private final List<FileProperties> items = new ArrayList<>();
    private final Set<String> selectedPaths = new LinkedHashSet<>();

    // more data
    private final List<BadgeState> badgeStates = new ArrayList<>();

    private Runnable onSelectionChanged;

    public PopupFileItemsAdapter(String startDirectory, boolean selectable) {
        this.startDirectory = startDirectory;
        this.selectable = selectable;
    }

    public void whenSelectionChanged(Runnable onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
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

    public int getSelectedCount() {
        return selectedPaths.size();
    }

    public boolean hasSelection() {
        return !selectedPaths.isEmpty();
    }

    public void selectAll() {
        selectedPaths.clear();
        for (FileProperties item : items) {
            selectedPaths.add(item.path);
        }
        notifySelectionChanged();
    }

    public void clearSelection() {
        selectedPaths.clear();
        notifySelectionChanged();
    }

    public void deselect(Collection<FileProperties> toDeselect) {
        for (FileProperties item : toDeselect) {
            selectedPaths.remove(item.path);
        }
        notifySelectionChanged();
    }

    public List<FileProperties> getSelectedItems() {
        List<FileProperties> selected = new ArrayList<>();
        for (FileProperties item : items) {
            if (isSelected(item)) {
                selected.add(item);
            }
        }
        return selected;
    }

    private boolean isSelected(FileProperties item) {
        return selectedPaths.contains(item.path);
    }

    private void notifySelectionChanged() {
        if (onSelectionChanged != null) {
            onSelectionChanged.run();
        }
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

        BadgeState badgeState = position < badgeStates.size() ? badgeStates.get(position) : BadgeState.NONE;
        viewHolder.itemBar.setBadge(selectable && isSelected(item) ? BadgeState.SELECTED : badgeState);

        viewHolder.itemBar.setPath(PathOps.singleton.relativize(startDirectory, item.path));

        viewHolder.itemBar.setIndex(position + 1);

        if (selectable) {
            viewHolder.itemView.setOnClickListener(v -> {
                if (hasSelection()) {
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
        if (isSelected(item)) {
            selectedPaths.remove(item.path);
        } else {
            selectedPaths.add(item.path);
        }
        notifyItemChanged(position);
        notifySelectionChanged();
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
