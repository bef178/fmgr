package pd.droidapp.fmgr.view;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.renderButtonStates;

public class SelectionBar {

    private final View selfView;
    private final TextView xSelectedTextView;
    private final LinearLayout buttonsView;

    private IntConsumer onButtonClicked;

    private State state;

    public SelectionBar(View selfView) {
        this.selfView = selfView;
        xSelectedTextView = selfView.findViewById(R.id.x_selected);
        buttonsView = selfView.findViewById(R.id.selection_buttons);
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

        if (oldState == null || oldState.numSelected != state.numSelected) {
            if (state.numSelected == 0) {
                selfView.setVisibility(View.GONE);
                return;
            }
            xSelectedTextView.setText(selfView.getContext().getString(R.string.x_selected, state.numSelected));
            selfView.setVisibility(View.VISIBLE);
        }

        if (oldState == null || !Objects.equals(oldState.buttonStates, state.buttonStates)) {
            renderButtonStates(state.buttonStates, buttonsView, R.layout.selection_button, buttonId -> {
                if (onButtonClicked != null) {
                    onButtonClicked.accept(buttonId);
                }
            });
        }
    }

    public static class State {

        public final int numSelected;
        public final List<ButtonState> buttonStates;

        public State(int numSelected, ButtonState... buttonStates) {
            this.numSelected = numSelected;
            this.buttonStates = buttonStates == null || buttonStates.length == 0
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(Arrays.asList(buttonStates));
        }
    }
}
