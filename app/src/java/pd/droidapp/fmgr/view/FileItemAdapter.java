package pd.droidapp.fmgr.view;

import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.view.OneShotPreDrawListener;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.Progressor;

import static pd.droidapp.fmgr.util.Util.forwardViewActionsTo;

public class FileItemAdapter extends RecyclerView.Adapter<FileItemAdapter.ItemViewHolder> {

    private final Progressor<String, Integer> progressor = new Progressor<>();

    private Consumer<String> onItemClicked;
    private Consumer<String> onItemLongClicked;

    private List<State> states = new ArrayList<>();
    private RecyclerView recyclerView;

    public void whenItemClicked(Consumer<String> onItemClicked) {
        this.onItemClicked = onItemClicked;
    }

    public void whenItemLongClicked(Consumer<String> onItemLongClicked) {
        this.onItemLongClicked = onItemLongClicked;
    }

    public void render(List<State> states) {
        List<State> oldStates = this.states;
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

    public void render(ScrollToAndHighlightEvent event) {
        if (event == null) {
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
        View child = layoutManager.findViewByPosition(position);
        if (child != null && isFullyVisible(child)) {
            startHighlightAnimation(event.itemId, event.color);
            return;
        }
        layoutManager.scrollToPosition(position);
        OneShotPreDrawListener.add(recyclerView, () -> startHighlightAnimation(event.itemId, event.color));
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

    private void startHighlightAnimation(String itemId, @ColorInt int color) {
        progressor.start(itemId, 1500, color, new AccelerateDecelerateInterpolator(), (distance, velocity) -> {
            int position = indexOf(itemId);
            if (position >= 0) {
                notifyItemChanged(position);
            }
        });
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
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder viewHolder, int position) {
        State state = states.get(position);

        viewHolder.nameTextView.setText(state.name);
        viewHolder.iconImageView.setImageResource(state.drawableId);
        viewHolder.summaryTextView.setText(state.summary);
        viewHolder.summaryTextView.setVisibility(View.VISIBLE);

        if (state.selected) {
            viewHolder.badgeImageView.setVisibility(View.VISIBLE);
        } else {
            viewHolder.badgeImageView.setVisibility(View.GONE);
        }

        Integer highlightColor = progressor.getValue(state.itemId);
        applyHighlightEffect(viewHolder.highlightView, progressor.getVelocity(state.itemId),
                highlightColor == null ? Color.TRANSPARENT : highlightColor);

        viewHolder.itemView.setOnClickListener(v -> {
            if (onItemClicked != null) {
                onItemClicked.accept(state.itemId);
            }
        });

        viewHolder.itemView.setOnLongClickListener(v -> {
            if (onItemLongClicked != null) {
                onItemLongClicked.accept(state.itemId);
            }
            return true;
        });

        forwardViewActionsTo(viewHolder.nameTextView, viewHolder.itemView);
    }

    private void applyHighlightEffect(View view, Float velocity, @ColorInt int peakColor) {
        TypedValue typedValue = new TypedValue();
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

    @Override
    public int getItemCount() {
        return states.size();
    }

    public static class ItemViewHolder extends RecyclerView.ViewHolder {

        final View highlightView;
        final ImageView iconImageView;
        final ImageView badgeImageView;
        final TextView nameTextView;
        final TextView summaryTextView;

        ItemViewHolder(View view) {
            super(view);
            highlightView = view.findViewById(R.id.item_highlight);
            iconImageView = view.findViewById(R.id.item_icon);
            badgeImageView = view.findViewById(R.id.item_badge_image);
            nameTextView = view.findViewById(R.id.item_name);
            summaryTextView = view.findViewById(R.id.item_summary);
        }
    }

    public static class State {

        public final String itemId;
        public final String name;
        public final String summary;
        public final int drawableId;
        public final boolean selected;

        public State(String itemId, String name, String summary, @DrawableRes int drawableId, boolean selected) {
            this.itemId = itemId;
            this.name = name;
            this.summary = summary;
            this.drawableId = drawableId;
            this.selected = selected;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof State)) {
                return false;
            }
            State another = (State) o;
            return drawableId == another.drawableId
                    && Objects.equals(itemId, another.itemId)
                    && Objects.equals(name, another.name)
                    && Objects.equals(summary, another.summary)
                    && selected == another.selected;
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemId, name, summary, drawableId, selected);
        }
    }

    public static class ScrollToAndHighlightEvent {

        public final String itemId;
        public final int color;

        public ScrollToAndHighlightEvent(String itemId, @ColorInt int color) {
            this.itemId = itemId;
            this.color = color;
        }
    }
}
