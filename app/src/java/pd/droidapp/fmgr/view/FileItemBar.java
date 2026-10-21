package pd.droidapp.fmgr.view;

import android.graphics.Color;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.core.util.Consumer;

import java.util.Objects;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.forwardViewActionsTo;
import static pd.droidapp.fmgr.util.Util.renderText;

public class FileItemBar {

    private final View selfView;
    private final View highlightView;
    private final ImageView iconImageView;
    private final ImageView badgeImageView;
    private final ProgressBar badgeProgressView;
    private final HorizontalScrollView nameScrollView;
    private final TextView nameTextView;
    private final TextView summaryTextView;
    private final TextView indexTextView;

    private State state;

    public FileItemBar(View selfView) {
        this.selfView = selfView;
        highlightView = selfView.findViewById(R.id.item_highlight);
        iconImageView = selfView.findViewById(R.id.item_icon);
        badgeImageView = selfView.findViewById(R.id.item_badge_image);
        badgeProgressView = selfView.findViewById(R.id.item_badge_progress);
        nameScrollView = selfView.findViewById(R.id.item_name_scroll);
        nameTextView = selfView.findViewById(R.id.item_name);
        summaryTextView = selfView.findViewById(R.id.item_summary);
        indexTextView = selfView.findViewById(R.id.item_index);
    }

    public void whenClicked(Consumer<State> onClicked) {
        selfView.setOnClickListener(v -> onClicked.accept(state));
        forwardViewActionsTo(nameTextView, selfView);
    }

    public void whenLongClicked(Consumer<State> onLongClicked) {
        selfView.setOnLongClickListener(v -> {
            onLongClicked.accept(state);
            return true;
        });
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

        if (oldState == null || oldState.drawableId != state.drawableId) {
            iconImageView.setImageResource(state.drawableId);
        }

        if (oldState == null
                || oldState.selected != state.selected
                || oldState.processingStatus != state.processingStatus) {
            renderBadge(state.selected, state.processingStatus);
        }

        if (oldState != null && !oldState.itemId.equals(state.itemId)) {
            nameScrollView.scrollTo(0, 0);
        }

        if (oldState == null || !Objects.equals(oldState.name, state.name)) {
            nameTextView.setText(state.name);
        }

        if (oldState == null || !Objects.equals(oldState.summary, state.summary)) {
            renderText(summaryTextView, state.summary);
        }

        if (oldState == null || !Objects.equals(oldState.index, state.index)) {
            renderText(indexTextView, state.index);
        }

        if (oldState == null || oldState.highlightColor != state.highlightColor) {
            highlightView.setBackgroundColor(state.highlightColor);
        }
    }

    private void renderBadge(boolean selected, ProcessingStatus status) {
        if (status == null) {
            status = ProcessingStatus.NONE;
        }

        if (status == ProcessingStatus.RUNNING) {
            badgeProgressView.setBackgroundResource(R.drawable.ic_badge_none_orange_16);
            badgeProgressView.setVisibility(View.VISIBLE);
            badgeImageView.setVisibility(View.GONE);
            return;
        }

        badgeProgressView.setVisibility(View.GONE);

        switch (status) {
            case NONE:
                if (selected) {
                    badgeImageView.setImageResource(R.drawable.ic_badge_checkmark_primary_16);
                    badgeImageView.setVisibility(View.VISIBLE);
                } else {
                    badgeImageView.setVisibility(View.GONE);
                }
                break;
            case ABORTED:
                badgeImageView.setImageResource(R.drawable.ic_badge_blockmark_orange_16);
                badgeImageView.setVisibility(View.VISIBLE);
                break;
            case COMPLETED:
                badgeImageView.setImageResource(R.drawable.ic_badge_checkmark_green_16);
                badgeImageView.setVisibility(View.VISIBLE);
                break;
            case FAILED:
                badgeImageView.setImageResource(R.drawable.ic_badge_xmark_red_16);
                badgeImageView.setVisibility(View.VISIBLE);
                break;
            default:
                badgeImageView.setVisibility(View.GONE);
                break;
        }
    }

    public static class State {

        public final String itemId;
        public final String name;
        public final String summary;
        public final String index;
        public final int drawableId;
        public final boolean selected;
        public final ProcessingStatus processingStatus;
        public final int highlightColor;

        private State(String itemId, String name, String summary, String index, @DrawableRes int drawableId, boolean selected, ProcessingStatus processingStatus, int highlightColor) {
            this.itemId = itemId;
            this.name = name;
            this.summary = summary;
            this.index = index;
            this.drawableId = drawableId;
            this.selected = selected;
            this.processingStatus = processingStatus;
            this.highlightColor = highlightColor;
        }

        public State(String itemId, String name, String summary, String index, @DrawableRes int drawableId, boolean selected) {
            this(itemId, name, summary, index, drawableId, selected, null, Color.TRANSPARENT);
        }

        public State(String itemId, String name, String summary, String index, @DrawableRes int drawableId, boolean selected, ProcessingStatus processingStatus) {
            this(itemId, name, summary, index, drawableId, selected, processingStatus, Color.TRANSPARENT);
        }

        public State copyOfHighlightColor(int highlightColor) {
            if (highlightColor == this.highlightColor) {
                return this;
            }
            return new State(itemId, name, summary, index, drawableId, selected, processingStatus, highlightColor);
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
                    && Objects.equals(index, another.index)
                    && selected == another.selected
                    && processingStatus == another.processingStatus
                    && highlightColor == another.highlightColor;
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemId, name, summary, index, drawableId, selected, processingStatus, highlightColor);
        }
    }

    public enum ProcessingStatus {
        NONE,
        RUNNING,
        ABORTED,
        COMPLETED,
        FAILED,
    }
}
