package pd.droidapp.fmgr.view;

import android.view.View;
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

    private final ImageView iconImageView;
    private final ImageView badgeImageView;
    private final ProgressBar badgeProgressView;
    private final TextView nameTextView;
    private final TextView summaryTextView;
    private final TextView indexTextView;

    private Consumer<State> onClicked;
    private Consumer<State> onLongClicked;

    private State state;

    public FileItemBar(View selfView) {
        iconImageView = selfView.findViewById(R.id.item_icon);
        badgeImageView = selfView.findViewById(R.id.item_badge_image);
        badgeProgressView = selfView.findViewById(R.id.item_badge_progress);
        nameTextView = selfView.findViewById(R.id.item_name);
        summaryTextView = selfView.findViewById(R.id.item_summary);
        indexTextView = selfView.findViewById(R.id.item_index);

        selfView.setOnClickListener(v -> {
            if (onClicked != null) {
                onClicked.accept(state);
            }
        });
        selfView.setOnLongClickListener(v -> {
            if (onLongClicked == null) {
                return false;
            }
            onLongClicked.accept(state);
            return true;
        });
        forwardViewActionsTo(nameTextView, selfView);
    }

    public void whenClicked(Consumer<State> onClicked) {
        this.onClicked = onClicked;
    }

    public void whenLongClicked(Consumer<State> onLongClicked) {
        this.onLongClicked = onLongClicked;
    }

    /**
     * null state for no change
     */
    public void render(State state) {
        if (state == null) {
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

        if (oldState == null || !Objects.equals(oldState.name, state.name)) {
            nameTextView.setText(state.name);
        }

        if (oldState == null || !Objects.equals(oldState.summary, state.summary)) {
            renderText(summaryTextView, state.summary);
        }

        if (oldState == null || !Objects.equals(oldState.index, state.index)) {
            renderText(indexTextView, state.index);
        }
    }

    private void renderBadge(boolean selected, ProcessingStatus status) {
        if (status == null) {
            status = ProcessingStatus.NONE;
        }

        if (status == ProcessingStatus.RUNNING) {
            badgeProgressView.setBackgroundResource(R.drawable.ic_none_orange_16);
            badgeProgressView.setVisibility(View.VISIBLE);
            badgeImageView.setVisibility(View.GONE);
            return;
        }

        badgeProgressView.setVisibility(View.GONE);

        switch (status) {
            case NONE:
                if (selected) {
                    badgeImageView.setImageResource(R.drawable.i_check_16);
                    badgeImageView.setVisibility(View.VISIBLE);
                } else {
                    badgeImageView.setVisibility(View.GONE);
                }
                break;
            case ABORTED:
                badgeImageView.setImageResource(R.drawable.ic_prohibition_orange_16);
                badgeImageView.setVisibility(View.VISIBLE);
                break;
            case COMPLETED:
                badgeImageView.setImageResource(R.drawable.ic_check_green_16);
                badgeImageView.setVisibility(View.VISIBLE);
                break;
            case FAILED:
                badgeImageView.setImageResource(R.drawable.ic_cross_red_16);
                badgeImageView.setVisibility(View.VISIBLE);
                break;
            default:
                badgeImageView.setVisibility(View.GONE);
                break;
        }
    }

    public static class State {

        public final String path;
        public final String name;
        public final String summary;
        public final String index;
        public final int drawableId;
        public final boolean selected;
        public final ProcessingStatus processingStatus;

        public State(String path, String name, String summary, String index, @DrawableRes int drawableId, boolean selected, ProcessingStatus processingStatus) {
            this.path = path;
            this.name = name;
            this.summary = summary;
            this.index = index;
            this.drawableId = drawableId;
            this.selected = selected;
            this.processingStatus = processingStatus;
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
                    && Objects.equals(path, another.path)
                    && Objects.equals(name, another.name)
                    && Objects.equals(summary, another.summary)
                    && Objects.equals(index, another.index)
                    && selected == another.selected
                    && processingStatus == another.processingStatus;
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, name, summary, index, drawableId, selected, processingStatus);
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
