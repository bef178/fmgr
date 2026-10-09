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

public class PopupFileItemBar {

    private final View selfView;
    private final ImageView iconImageView;
    private final ImageView badgeIconView;
    private final ProgressBar badgeProgressView;
    private final TextView pathTextView;
    private final TextView indexTextView;

    private State state;

    public PopupFileItemBar(View selfView) {
        this.selfView = selfView;
        iconImageView = selfView.findViewById(R.id.item_icon);
        badgeIconView = selfView.findViewById(R.id.item_badge_image);
        badgeProgressView = selfView.findViewById(R.id.item_badge_progress);
        pathTextView = selfView.findViewById(R.id.item_name);
        indexTextView = selfView.findViewById(R.id.item_index);
        indexTextView.setVisibility(View.VISIBLE);
    }

    public void whenClicked(Consumer<State> onClicked) {
        selfView.setOnClickListener(v -> onClicked.accept(state));
        forwardViewActionsTo(pathTextView, selfView);
    }

    public void whenLongClicked(Consumer<State> onLongClicked) {
        selfView.setOnLongClickListener(v -> {
            onLongClicked.accept(state);
            return true;
        });
    }

    public void render(State state) {
        if (state == null || state == this.state) {
            return;
        }

        State oldState = this.state;
        this.state = state;

        if (oldState == null || oldState.drawableId != state.drawableId) {
            iconImageView.setImageResource(state.drawableId);
        }

        if (oldState == null || oldState.badgeState != state.badgeState) {
            renderBadge(state.badgeState);
        }

        if (oldState == null || !Objects.equals(oldState.displayPath, state.displayPath)) {
            pathTextView.setText(state.displayPath);
        }

        if (oldState == null || oldState.index != state.index) {
            indexTextView.setText(String.valueOf(state.index));
        }
    }

    private void renderBadge(BadgeState badgeState) {
        if (badgeState == BadgeState.RUNNING) {
            badgeProgressView.setBackgroundResource(R.drawable.ic_none_orange_16);
            badgeProgressView.setVisibility(View.VISIBLE);
            badgeIconView.setVisibility(View.GONE);
            return;
        }

        badgeProgressView.setVisibility(View.GONE);
        switch (badgeState) {
            case SELECTED:
                badgeIconView.setImageResource(R.drawable.i_check_16);
                badgeIconView.setVisibility(View.VISIBLE);
                break;
            case STOPPED:
                badgeIconView.setImageResource(R.drawable.ic_prohibition_orange_16);
                badgeIconView.setVisibility(View.VISIBLE);
                break;
            case DONE:
                badgeIconView.setImageResource(R.drawable.ic_check_green_16);
                badgeIconView.setVisibility(View.VISIBLE);
                break;
            case FAILED:
                badgeIconView.setImageResource(R.drawable.ic_cross_red_16);
                badgeIconView.setVisibility(View.VISIBLE);
                break;
            default:
                badgeIconView.setVisibility(View.GONE);
                break;
        }
    }

    public static class State {

        public final String itemId;
        public final String displayPath;
        public final int index;
        public final int drawableId;
        public final BadgeState badgeState;

        public State(String itemId, String displayPath, int index, @DrawableRes int drawableId, BadgeState badgeState) {
            this.itemId = itemId;
            this.displayPath = displayPath;
            this.index = index;
            this.drawableId = drawableId;
            this.badgeState = badgeState;
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
                    && index == another.index
                    && Objects.equals(itemId, another.itemId)
                    && Objects.equals(displayPath, another.displayPath)
                    && badgeState == another.badgeState;
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemId, displayPath, index, drawableId, badgeState);
        }
    }

    public enum BadgeState {
        NONE,
        SELECTED,
        RUNNING,
        STOPPED,
        DONE,
        FAILED,
    }
}
