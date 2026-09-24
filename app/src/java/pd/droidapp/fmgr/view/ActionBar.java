package pd.droidapp.fmgr.view;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.listOf;
import static pd.droidapp.fmgr.util.Util.renderButtonStates;

public class ActionBar {

    private final LinearLayout buttonsView;
    private final ImageButton moreButton;
    private final LinearLayout popupButtonsView;
    private final PopupWindow popupWindow;

    private IntConsumer onButtonClicked;

    private State state;

    public ActionBar(LinearLayout selfView) {
        buttonsView = selfView.findViewById(R.id.action_buttons);
        moreButton = selfView.findViewById(R.id.action_more);
        popupButtonsView = (LinearLayout) LayoutInflater.from(selfView.getContext())
                .inflate(R.layout.action_bar_popup, selfView, false);
        popupWindow = new PopupWindow(popupButtonsView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);

        moreButton.setOnClickListener(v -> {
            moreButton.setSelected(true);
            popupWindow.showAsDropDown(moreButton, 0, 0, Gravity.END);
        });
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(16);
        popupWindow.setOnDismissListener(() -> moreButton.setSelected(false));
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
            renderButtonStates(state.buttonStates, buttonsView, R.layout.action_button, buttonId -> {
                if (onButtonClicked != null) {
                    onButtonClicked.accept(buttonId);
                }
            });
        }

        if (oldState == null || !Objects.equals(oldState.popupButtonStates, state.popupButtonStates)) {
            if (state.popupButtonStates.isEmpty()) {
                popupWindow.dismiss();
                moreButton.setEnabled(false);
            } else {
                moreButton.setEnabled(true);
            }
            renderButtonStates(state.popupButtonStates, popupButtonsView, R.layout.action_button, buttonId -> {
                if (onButtonClicked != null) {
                    onButtonClicked.accept(buttonId);
                }
                popupWindow.dismiss();
            });
        }
    }

    public static class State {

        public final List<ButtonState> buttonStates;
        public final List<ButtonState> popupButtonStates;

        public State(ButtonState[] buttonStates, ButtonState... popupButtonStates) {
            this.buttonStates = listOf(buttonStates);
            this.popupButtonStates = listOf(popupButtonStates);
        }
    }
}
