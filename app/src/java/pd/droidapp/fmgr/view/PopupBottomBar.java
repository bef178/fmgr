package pd.droidapp.fmgr.view;

import android.widget.LinearLayout;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.listOf;
import static pd.droidapp.fmgr.util.Util.renderButtonStates;

public class PopupBottomBar {

    private final LinearLayout buttonsView;

    private IntConsumer onButtonClicked;

    private State state;

    public PopupBottomBar(LinearLayout selfView) {
        buttonsView = selfView;
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

        if (oldState == null || !Objects.equals(oldState.buttonStates, state.buttonStates)) {
            renderButtonStates(state.buttonStates, buttonsView, R.layout.popup_button, buttonId -> {
                if (onButtonClicked != null) {
                    onButtonClicked.accept(buttonId);
                }
            });
        }
    }

    public static class State {

        public final List<ButtonState> buttonStates;

        public State(ButtonState... buttonStates) {
            this.buttonStates = listOf(buttonStates);
        }
    }
}
