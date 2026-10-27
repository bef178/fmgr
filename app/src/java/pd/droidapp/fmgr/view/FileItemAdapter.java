package pd.droidapp.fmgr.view;

import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.view.OneShotPreDrawListener;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.Progressor;

public class FileItemAdapter extends RecyclerView.Adapter<FileItemAdapter.ItemViewHolder> {

    private final Progressor<String, Integer> progressor = new Progressor<>();

    private Consumer<String> onItemClicked;
    private Consumer<String> onItemLongClicked;

    private List<FileItemBar.State> states = new ArrayList<>();
    private RecyclerView recyclerView;

    public void whenItemClicked(Consumer<String> onItemClicked) {
        this.onItemClicked = onItemClicked;
    }

    public void whenItemLongClicked(Consumer<String> onItemLongClicked) {
        this.onItemLongClicked = onItemLongClicked;
    }

    public void render(List<FileItemBar.State> states) {
        render(states, false);
    }

    public void render(List<FileItemBar.State> states, boolean replaceAll) {
        if (replaceAll) {
            // whole-table replacement: the next layout drops queued adapter updates and
            // rebinds everything, so a pending insert is not scanned once per item update
            this.states = new ArrayList<>(states);
            notifyDataSetChanged();
            return;
        }
        List<FileItemBar.State> oldStates = this.states;
        this.states = new ArrayList<>(states);

        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldStates.size();
            }

            @Override
            public int getNewListSize() {
                return FileItemAdapter.this.states.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldStates.get(oldPos).itemId.equals(FileItemAdapter.this.states.get(newPos).itemId);
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                return oldStates.get(oldPos).equals(FileItemAdapter.this.states.get(newPos));
            }
        }).dispatchUpdatesTo(this);
    }

    public void append(List<FileItemBar.State> states) {
        if (states.isEmpty()) {
            return;
        }
        int start = this.states.size();
        this.states.addAll(states);
        notifyItemRangeInserted(start, states.size());
    }

    public void update(int position, FileItemBar.State state) {
        if (this.states.get(position).equals(state)) {
            return;
        }
        this.states.set(position, state);
        notifyItemChanged(position);
    }

    public void clear() {
        int size = states.size();
        if (size == 0) {
            return;
        }
        states.clear();
        notifyItemRangeRemoved(0, size);
    }

    public void render(ScrollToAndHighlightEvent event) {
        if (event == null || event.itemId == null) {
            return;
        }
        int position = indexOf(event.itemId);
        if (position < 0) {
            return;
        }
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null) {
            return;
        }
        OneShotPreDrawListener.add(recyclerView, () -> {
            View child = layoutManager.findViewByPosition(position);
            if (child != null && isFullyVisible(child)) {
                startHighlightAnimation(event.itemId, event.peakColor);
                return;
            }
            layoutManager.scrollToPosition(position);
            OneShotPreDrawListener.add(recyclerView, () -> startHighlightAnimation(event.itemId, event.peakColor));
        });
    }

    private int indexOf(String itemId) {
        for (int i = 0; i < states.size(); i++) {
            if (states.get(i).itemId.equals(itemId)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isFullyVisible(View child) {
        return child.getTop() >= recyclerView.getPaddingTop()
                && child.getBottom() <= recyclerView.getHeight() - recyclerView.getPaddingBottom();
    }

    private void startHighlightAnimation(String itemId, @ColorInt int peakColor) {
        progressor.start(itemId, 1500, peakColor, new AccelerateDecelerateInterpolator(), (distance, velocity) -> {
            if (recyclerView == null) {
                return;
            }
            int position = indexOf(itemId);
            if (position < 0) {
                return;
            }
            ItemViewHolder viewHolder = (ItemViewHolder) recyclerView.findViewHolderForAdapterPosition(position);
            if (viewHolder == null) {
                return;
            }
            renderItem(viewHolder, states.get(position));
        });
    }

    private void renderItem(ItemViewHolder viewHolder, FileItemBar.State oldState) {
        viewHolder.itemBar.render(oldState.copyOfHighlightColor(highlightColor(oldState.itemId)));
    }

    private int highlightColor(String itemId) {
        Integer peakColor = progressor.getValue(itemId);
        Float velocity = progressor.getVelocity(itemId);
        if (peakColor == null || velocity == null) {
            return Color.TRANSPARENT;
        }

        TypedValue typedValue = new TypedValue();
        recyclerView.getContext().getTheme().resolveAttribute(android.R.attr.colorBackground, typedValue, true);
        final int baseColor = typedValue.data & 0x00FFFFFF;

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
        return Color.argb(a, r, g, b);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        this.recyclerView = null;
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.file_item, parent, false);
        ItemViewHolder viewHolder = new ItemViewHolder(view);
        if (onItemClicked != null) {
            viewHolder.itemBar.whenClicked(state -> onItemClicked.accept(state.itemId));
        }
        if (onItemLongClicked != null) {
            viewHolder.itemBar.whenLongClicked(state -> onItemLongClicked.accept(state.itemId));
        }
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder viewHolder, int position) {
        renderItem(viewHolder, states.get(position));
    }

    @Override
    public int getItemCount() {
        return states.size();
    }

    public static class ItemViewHolder extends RecyclerView.ViewHolder {

        final FileItemBar itemBar;

        ItemViewHolder(View view) {
            super(view);
            itemBar = new FileItemBar(view);
        }
    }

    public static class ScrollToAndHighlightEvent {

        public final String itemId;
        public final int peakColor;

        public ScrollToAndHighlightEvent(String itemId, @ColorInt int peakColor) {
            this.itemId = itemId;
            this.peakColor = peakColor;
        }
    }
}
