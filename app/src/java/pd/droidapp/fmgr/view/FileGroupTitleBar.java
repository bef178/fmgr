package pd.droidapp.fmgr.view;

import android.animation.ValueAnimator;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.util.Consumer;

import java.util.Objects;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.animateCollapsed;
import static pd.droidapp.fmgr.util.Util.renderText;

public class FileGroupTitleBar {

    private final ImageView triangleImageView;
    private final TextView titleTextView;
    private final TextView indexTextView;

    private Runnable onClicked;
    private Consumer<Boolean> onAnimationEnded;

    private ValueAnimator collapseAnimator;

    private State state;

    public FileGroupTitleBar(View selfView) {
        triangleImageView = selfView.findViewById(R.id.group_triangle);
        titleTextView = selfView.findViewById(R.id.group_title);
        indexTextView = selfView.findViewById(R.id.group_index);

        selfView.findViewById(R.id.group_title_bar).setOnClickListener(v -> {
            if (collapseAnimator != null && collapseAnimator.isRunning()) {
                return;
            }
            if (onClicked != null) {
                onClicked.run();
            }
        });
    }

    public void whenClicked(Runnable onClicked) {
        this.onClicked = onClicked;
    }

    public void whenAnimationEnded(Consumer<Boolean> onAnimationEnded) {
        this.onAnimationEnded = onAnimationEnded;
    }

    public void render(CollapseEvent event) {
        if (event != null) {
            collapseAnimator = animateCollapsed(triangleImageView, event.futureCollapsed, () -> {
                if (onAnimationEnded != null) {
                    onAnimationEnded.accept(event.futureCollapsed);
                }
            });
        }
    }

    /**
     * null state for no change
     */
    public void render(State state) {
        if (state == null) {
            return;
        }

        if (collapseAnimator != null && collapseAnimator.isRunning()) {
            collapseAnimator.cancel();
        }

        State oldState = this.state;
        this.state = state;

        if (oldState == null || !Objects.equals(oldState.title, state.title)) {
            titleTextView.setText(state.title);
        }

        if (oldState == null || !Objects.equals(oldState.index, state.index)) {
            renderText(indexTextView, state.index);
        }

        triangleImageView.setRotation(state.collapsed ? -90f : 0f);
    }

    public static class CollapseEvent {

        public final boolean futureCollapsed;

        public CollapseEvent(boolean futureCollapsed) {
            this.futureCollapsed = futureCollapsed;
        }
    }

    public static class State {

        public final String title;
        public final String index;
        public final boolean collapsed;

        public State(String title, String index, boolean collapsed) {
            this.title = title;
            this.index = index;
            this.collapsed = collapsed;
        }
    }
}
