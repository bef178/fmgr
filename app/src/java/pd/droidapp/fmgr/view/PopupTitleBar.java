package pd.droidapp.fmgr.view;

import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;

import java.util.Objects;

import pd.droidapp.fmgr.R;

public class PopupTitleBar {

    private final TextView titleTextView;
    private final ImageButton closeButton;

    private Runnable onCloseButtonClicked;

    private State state;

    public PopupTitleBar(View selfView) {
        titleTextView = selfView.findViewById(R.id.popup_title);
        closeButton = selfView.findViewById(R.id.popup_close);
        closeButton.setOnClickListener(v -> {
            if (onCloseButtonClicked != null) {
                onCloseButtonClicked.run();
            }
        });
    }

    public void whenCloseButtonClicked(Runnable onCloseButtonClicked) {
        this.onCloseButtonClicked = onCloseButtonClicked;
    }

    public State getState() {
        return state;
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
            titleTextView.setText(state.text);
        }

        if (oldState == null || !Objects.equals(oldState.buttonState, state.buttonState)) {
            closeButton.setId(state.buttonState.id);
            closeButton.setImageResource(state.buttonState.drawableId);
            closeButton.setVisibility(state.buttonState.visible ? View.VISIBLE : View.GONE);
            closeButton.setEnabled(state.buttonState.enabled);
        }
    }

    public static class State {

        public final String text;
        public final ButtonState buttonState;

        public State(String text) {
            this(text, new ButtonState(R.drawable.action_close));
        }

        private State(String text, @NonNull ButtonState buttonState) {
            this.text = text;
            this.buttonState = buttonState;
        }

        public State copyWithButtonEnabled(boolean enabled) {
            return new State(text, new ButtonState(buttonState.drawableId, buttonState.visible, enabled));
        }
    }
}
