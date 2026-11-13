package pd.droidapp.fmgr.view;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.util.Consumer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import pd.droidapp.fmgr.R;

public class FileGroupBar {

    private final TextView titleTextView;
    private final LinearLayout itemContainerView;
    private final List<FileItemBar> itemBars = new ArrayList<>();

    private Consumer<FileItemBar.State> onItemClicked;
    private Consumer<FileItemBar.State> onItemLongClicked;

    private State state;

    public FileGroupBar(View selfView) {
        titleTextView = selfView.findViewById(R.id.group_title);
        itemContainerView = selfView.findViewById(R.id.group_items);
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

        if (oldState == null || !Objects.equals(oldState.title, state.title)) {
            titleTextView.setText(state.title);
        }

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

    public static class State {

        public final String title;
        public final List<FileItemBar.State> itemStates;

        public State(String title, List<FileItemBar.State> itemStates) {
            this.title = title;
            this.itemStates = itemStates;
        }
    }
}
