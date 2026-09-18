package pd.droidapp.fmgr.fragment;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.util.SelectionBar;
import pd.util.FileOps;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.forwardViewActionsTo;
import static pd.droidapp.fmgr.util.Util.getSizeString;

class FileItemsAdapter extends RecyclerView.Adapter<FileItemsAdapter.ItemViewHolder> {

    private final SelectionBar selectionBar;
    private final Comparator<FileProperties> itemComparator = (p1, p2) -> {
        if (p1.isDirectory != p2.isDirectory) {
            return p1.isDirectory ? -1 : 1;
        }
        return PathOps.singleton.compare(p1.path, p2.path);
    };

    private final List<FileProperties> items = new LinkedList<>();
    private final Progressor<String> progressor = new Progressor<>();
    private final PropertiesLoader propertiesLoader;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private BiConsumer<String, Boolean> onItemClicked;

    FileItemsAdapter(SelectionBar selectionBar) {
        this.selectionBar = selectionBar;
        propertiesLoader = new PropertiesLoader();
        propertiesLoader.whenUpdated(updated -> handler.post(() -> {
            Set<FileProperties> updatedSet = new HashSet<>(updated);
            for (int i = 0; i < items.size(); i++) {
                if (updatedSet.contains(items.get(i))) {
                    notifyItemChanged(i);
                }
            }
        }));
    }

    public void whenItemClicked(BiConsumer<String, Boolean> onItemClicked) {
        this.onItemClicked = onItemClicked;
    }

    public void set(Collection<FileProperties> newItems) {
        List<FileProperties> oldItems = new LinkedList<>(items);
        propertiesLoader.clear();
        items.clear();
        items.addAll(newItems);
        items.sort(itemComparator);
        dispatchDiff(oldItems);
        propertiesLoader.add(new LinkedList<>(items));
    }

    private void dispatchDiff(List<FileProperties> oldItems) {
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
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldItems.get(oldItemPosition).path.equals(items.get(newItemPosition).path);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                FileProperties oldItem = oldItems.get(oldItemPosition);
                FileProperties newItem = items.get(newItemPosition);
                return oldItem.computed == newItem.computed
                        && Objects.equals(oldItem.size, newItem.size)
                        && Objects.equals(oldItem.numChildren, newItem.numChildren);
            }
        }).dispatchUpdatesTo(this);
    }

    public void add(Collection<FileProperties> toAdd) {
        if (toAdd.isEmpty()) {
            return;
        }
        List<FileProperties> oldItems = new LinkedList<>(items);
        Map<String, FileProperties> byPath = new HashMap<>();
        for (FileProperties item : toAdd) {
            byPath.put(item.path, item);
        }
        for (int i = 0; i < items.size(); i++) {
            FileProperties replacement = byPath.remove(items.get(i).path);
            if (replacement != null) {
                items.set(i, replacement);
            }
        }
        items.addAll(byPath.values());
        propertiesLoader.add(new LinkedList<>(toAdd));
        items.sort(itemComparator);
        dispatchDiff(oldItems);
    }

    public FileProperties remove(String path) {
        int index = indexOf(path);
        if (index < 0) {
            return null;
        }
        FileProperties removed = items.remove(index);
        notifyItemRemoved(index);
        return removed;
    }

    public void remove(Collection<FileProperties> toRemove) {
        Set<String> paths = new HashSet<>();
        for (FileProperties item : toRemove) {
            paths.add(item.path);
        }
        for (int i = items.size() - 1; i >= 0; i--) {
            if (paths.contains(items.get(i).path)) {
                items.remove(i);
                notifyItemRemoved(i);
            }
        }
    }

    public void invalidate(Collection<FileProperties> invalidatedItems) {
        if (invalidatedItems.isEmpty()) {
            return;
        }
        Set<String> paths = new HashSet<>();
        for (FileProperties item : invalidatedItems) {
            paths.add(item.path);
        }
        for (int i = 0; i < items.size(); i++) {
            if (paths.contains(items.get(i).path)) {
                notifyItemChanged(i);
            }
        }
    }

    public void loadProperties(Collection<String> paths) {
        if (paths.isEmpty()) {
            return;
        }
        Set<String> pathsSet = new HashSet<>(paths);
        List<FileProperties> toReload = new LinkedList<>();
        for (FileProperties item : items) {
            if (pathsSet.contains(item.path)) {
                item.computed = false;
                toReload.add(item);
            }
        }
        if (!toReload.isEmpty()) {
            propertiesLoader.add(toReload);
        }
    }

    public List<FileProperties> getItems() {
        return items;
    }

    public int indexOf(String path) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).path.equals(path)) {
                return i;
            }
        }
        return -1;
    }

    public void highlightItem(String path) {
        progressor.start(path, 1500, new AccelerateDecelerateInterpolator(), (distance, velocity) -> {
            int position = indexOf(path);
            if (position >= 0) {
                notifyItemChanged(position);
            }
        });
    }

    public List<FileProperties> getSelectedItems() {
        List<FileProperties> selected = new LinkedList<>();
        for (FileProperties item : items) {
            if (selectionBar.hasSelected(item)) {
                selected.add(item);
            }
        }
        return selected;
    }

    public void cancel() {
        propertiesLoader.cancel();
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.file_item, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder viewHolder, int position) {
        FileProperties item = items.get(position);

        viewHolder.pathTextView.setText(PathOps.singleton.basename(item.path));
        viewHolder.iconImageView.setImageResource(
                item.isDirectory ? R.drawable.i_directory_24 : R.drawable.i_file_24);
        viewHolder.detailsTextView.setText(getItemDetailsString(item, viewHolder.itemView.getContext()));

        if (selectionBar.hasSelected(item)) {
            viewHolder.selectedIconImageView.setVisibility(View.VISIBLE);
        } else {
            viewHolder.selectedIconImageView.setVisibility(View.GONE);
        }

        applyHighlightEffect(viewHolder.highlightView, progressor.getVelocity(item.path));

        viewHolder.itemView.setOnClickListener(v -> {
            if (!selectionBar.isEmpty()) {
                toggleSelected(item);
                return;
            }
            if (item.isDirectory || FileOps.singleton.stat(item.path).isFile(true)) {
                if (onItemClicked != null) {
                    onItemClicked.accept(item.path, item.isDirectory);
                }
            } else {
                Toast.makeText(v.getContext(), R.string.error_failed_to_handle, Toast.LENGTH_SHORT).show();
            }
        });

        viewHolder.itemView.setOnLongClickListener(v -> {
            toggleSelected(item);
            return true;
        });

        forwardViewActionsTo(viewHolder.pathTextView, viewHolder.itemView);
    }

    private String getItemDetailsString(FileProperties item, Context context) {
        if (!item.computed) {
            return "...";
        }
        if (item.isDirectory) {
            if (item.numChildren == null) {
                return context.getString(R.string.error_directory_not_accessible);
            }
            if (item.numChildren == 0) {
                return context.getString(R.string.empty);
            }
            return context.getString(R.string.x_items, item.numChildren);
        } else if (item.size != null) {
            return getSizeString(item.size);
        } else {
            return context.getString(R.string.error);
        }
    }

    private void applyHighlightEffect(View view, Float velocity) {
        TypedValue typedValue = new TypedValue();
        view.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorSecondary, typedValue, true);
        final int peakColor = typedValue.data;

        view.getContext().getTheme().resolveAttribute(android.R.attr.colorBackground, typedValue, true);
        final int baseColor = typedValue.data & 0x00FFFFFF;

        if (velocity == null) {
            velocity = 0f;
        }

        int peakA = Color.alpha(peakColor);
        int peakR = Color.red(peakColor);
        int peakG = Color.green(peakColor);
        int peakB = Color.blue(peakColor);

        int baseA = Color.alpha(baseColor);
        int baseR = Color.red(baseColor);
        int baseG = Color.green(baseColor);
        int baseB = Color.blue(baseColor);

        float x = (float) (velocity / (Math.PI / 2));
        x = Math.max(0f, Math.min(x, 1f));
        float alpha = x * x * (3f - 2f * x); // smoothstep: f(t) = 3t^2-3t^3
        int a = (int) (peakA * alpha + baseA * (1 - alpha));
        int r = (int) (peakR * alpha + baseR * (1 - alpha));
        int g = (int) (peakG * alpha + baseG * (1 - alpha));
        int b = (int) (peakB * alpha + baseB * (1 - alpha));
        int color = Color.argb(a, r, g, b);

        view.setBackgroundColor(color);
    }

    private void toggleSelected(FileProperties item) {
        selectionBar.toggleSelected(item);
        selectionBar.invalidate();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).path.equals(item.path)) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {

        private final View highlightView;
        private final ImageView iconImageView;
        private final ImageView selectedIconImageView;
        private final TextView pathTextView;
        private final TextView detailsTextView;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            highlightView = itemView.findViewById(R.id.file_highlight);
            iconImageView = itemView.findViewById(R.id.file_icon);
            selectedIconImageView = itemView.findViewById(R.id.file_selected_icon);
            pathTextView = itemView.findViewById(R.id.file_name);
            detailsTextView = itemView.findViewById(R.id.file_details);
        }
    }
}
