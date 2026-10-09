package pd.droidapp.fmgr.view;

import android.animation.ValueAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.util.Consumer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.animateCollapsed;
import static pd.droidapp.fmgr.util.Util.renderText;

public class FileGroupBar {

    private final ImageView triangleImageView;
    private final TextView titleTextView;
    private final TextView indexTextView;
    private final LinearLayout itemContainerView;
    private final List<FileItemBar> itemBars = new ArrayList<>();

    private Consumer<State> onTitleClicked;
    private Consumer<FileItemBar.State> onItemClicked;
    private Consumer<FileItemBar.State> onItemLongClicked;

    private ValueAnimator collapseAnimator;

    private State state;

    public FileGroupBar(View selfView) {
        triangleImageView = selfView.findViewById(R.id.group_triangle);
        titleTextView = selfView.findViewById(R.id.group_title);
        indexTextView = selfView.findViewById(R.id.group_index);
        itemContainerView = selfView.findViewById(R.id.group_items);

        selfView.findViewById(R.id.group_title_bar).setOnClickListener(v -> {
            if (collapseAnimator != null && collapseAnimator.isRunning()) {
                return;
            }
            if (onTitleClicked != null) {
                onTitleClicked.accept(state);
            }
        });
    }

    public void whenTitleClicked(Consumer<State> onTitleClicked) {
        this.onTitleClicked = onTitleClicked;
    }

    public void whenItemClicked(Consumer<FileItemBar.State> onItemClicked) {
        this.onItemClicked = onItemClicked;
    }

    public void whenItemLongClicked(Consumer<FileItemBar.State> onItemLongClicked) {
        this.onItemLongClicked = onItemLongClicked;
    }

    /**
     * null state for no change
     */
    public void render(State state) {
        if (state == null || state == this.state) {
            return;
        }

        State oldState = this.state;
        this.state = state;

        if (collapseAnimator != null && collapseAnimator.isRunning()) {
            collapseAnimator.cancel();
        }

        if (oldState == null || !Objects.equals(oldState.title, state.title)) {
            titleTextView.setText(state.title);
        }

        if (oldState == null || !Objects.equals(oldState.index, state.index)) {
            renderText(indexTextView, state.index);
        }

        triangleImageView.setRotation(state.collapsed ? -90f : 0f);
        itemContainerView.setVisibility(state.collapsed ? View.GONE : View.VISIBLE);

        renderItems(state.itemStates);
    }

    private void renderItems(List<FileItemBar.State> itemStates) {
        int nowCount = itemContainerView.getChildCount();
        int requiredCount = itemStates.size();

        LayoutInflater layoutInflater = LayoutInflater.from(itemContainerView.getContext());
        for (int i = 0; i < requiredCount; i++) {
            FileItemBar itemBar;
            if (i < nowCount) {
                itemBar = itemBars.get(i);
            } else {
                View itemView = layoutInflater.inflate(R.layout.file_item, itemContainerView, false);
                itemContainerView.addView(itemView);
                itemBar = new FileItemBar(itemView);
                itemBar.whenClicked(itemState -> {
                    if (onItemClicked != null) {
                        onItemClicked.accept(itemState);
                    }
                });
                itemBar.whenLongClicked(itemState -> {
                    if (onItemLongClicked != null) {
                        onItemLongClicked.accept(itemState);
                    }
                });
                itemBars.add(itemBar);
            }

            itemBar.render(itemStates.get(i));
        }
        if (nowCount > requiredCount) {
            itemContainerView.removeViews(requiredCount, nowCount - requiredCount);
            itemBars.subList(requiredCount, itemBars.size()).clear();
        }
    }

    public void render(CollapseEvent event) {
        if (event == null) {
            return;
        }
        collapseAnimator = animateCollapsed(triangleImageView, event.futureCollapsed,
                () -> itemContainerView.setVisibility(event.futureCollapsed ? View.GONE : View.VISIBLE));
    }

    public static class State {

        public final String groupId;
        public final String title;
        public final String index;
        public final boolean collapsed;
        public final List<FileItemBar.State> itemStates;

        public State(String groupId, String title, String index, boolean collapsed, List<FileItemBar.State> itemStates) {
            this.groupId = groupId;
            this.title = title;
            this.index = index;
            this.collapsed = collapsed;
            this.itemStates = itemStates;
        }
    }

    public static class CollapseEvent {

        public final boolean futureCollapsed;

        public CollapseEvent(boolean futureCollapsed) {
            this.futureCollapsed = futureCollapsed;
        }
    }
}
