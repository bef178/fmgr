package pd.droidapp.fmgr.view;

import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.DrawableRes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.renderButtonStates;

public class StatusBar {

    private final int initDrawableId;

    private final ImageView iconView;
    private final TextView textView;
    private final LinearLayout buttonsView;

    private IntConsumer onButtonClicked;

    private State state;

    public StatusBar(LinearLayout selfView, @DrawableRes int initDrawableId) {
        this.initDrawableId = initDrawableId;
        iconView = selfView.findViewById(R.id.status_icon);
        textView = selfView.findViewById(R.id.status_text);
        buttonsView = selfView.findViewById(R.id.status_buttons);
    }

    public void whenButtonClicked(IntConsumer onButtonClicked) {
        this.onButtonClicked = onButtonClicked;
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

        if (oldState == null || !Objects.equals(oldState.text, state.text)) {
            textView.setText(state.text);
        }

        if (oldState == null || oldState.iconState != state.iconState) {
            renderIconState(state.iconState);
        }

        if (oldState == null || !Objects.equals(oldState.buttonStates, state.buttonStates)) {
            renderButtonStates(state.buttonStates, buttonsView, R.layout.status_button, buttonId -> {
                if (onButtonClicked != null) {
                    onButtonClicked.accept(buttonId);
                }
            });
        }
    }

    private void renderIconState(IconState iconState) {
        iconView.clearAnimation();
        switch (iconState) {
            case RUNNING:
                iconView.setImageResource(R.drawable.baseline_refresh_24);
                RotateAnimation rotateAnim = new RotateAnimation(0, 360,
                        Animation.RELATIVE_TO_SELF, 0.5f,
                        Animation.RELATIVE_TO_SELF, 0.5f);
                rotateAnim.setDuration(1000);
                rotateAnim.setRepeatCount(Animation.INFINITE);
                iconView.startAnimation(rotateAnim);
                break;
            case COMPLETED:
                iconView.setImageResource(R.drawable.baseline_done_24);
                break;
            case STOPPED:
                iconView.setImageResource(R.drawable.baseline_close_24);
                break;
            default:
                iconView.setImageResource(initDrawableId);
                break;
        }
    }

    public static class State {

        public final IconState iconState;
        public final String text;
        public final List<ButtonState> buttonStates;

        public State(IconState iconState, String text) {
            this(iconState, text, (ButtonState[]) null);
        }

        public State(IconState iconState, String text, ButtonState... buttonStates) {
            this.iconState = iconState;
            this.text = text;
            this.buttonStates = buttonStates == null || buttonStates.length == 0
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(Arrays.asList(buttonStates));
        }
    }

    public enum IconState {
        IDLE, RUNNING, COMPLETED, STOPPED
    }
}
