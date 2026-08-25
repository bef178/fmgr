package pd.droidapp.fmgr.util;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import androidx.annotation.DrawableRes;

import java.util.LinkedList;
import java.util.List;
import java.util.function.BooleanSupplier;

import pd.droidapp.fmgr.R;

public class ActionBar {

    private final LinearLayout selfView;
    private final LinearLayout popupView;
    private final PopupWindow popupWindow;

    private final List<ActionButton> actionButtons = new LinkedList<>();
    private final List<ActionButton> popupActionButtons = new LinkedList<>();

    public ActionBar(LinearLayout selfView) {
        this.selfView = selfView;

        ImageButton moreButton = inflateButton(R.drawable.action_more);
        moreButton.setOnClickListener(v -> showActionPopup(moreButton));
        selfView.addView(moreButton);

        popupView = (LinearLayout) LayoutInflater.from(selfView.getContext())
                .inflate(R.layout.action_popup, selfView, false);
        popupWindow = new PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(16);
    }

    private ImageButton inflateButton(@DrawableRes int drawableId) {
        Context context = selfView.getContext();
        ImageButton button = (ImageButton) LayoutInflater.from(context).inflate(R.layout.action_button, selfView, false);
        button.setImageResource(drawableId);
        return button;
    }

    private void showActionPopup(View anchorView) {
        for (ActionButton button : popupActionButtons) {
            button.view.setVisibility(button.visible.getAsBoolean() ? View.VISIBLE : View.GONE);
        }

        popupWindow.showAsDropDown(anchorView, 0, 0, Gravity.END);
    }

    public void addButton(@DrawableRes int drawableId, BooleanSupplier enabled, Runnable action) {
        ImageButton button = inflateButton(drawableId);
        button.setOnClickListener(v -> action.run());
        button.setEnabled(enabled.getAsBoolean());
        selfView.addView(button, selfView.getChildCount() - 1);
        actionButtons.add(new ActionButton(button, null, enabled));
    }

    public void addPopupButton(@DrawableRes int drawableId, Runnable action) {
        addPopupButton(drawableId, () -> true, action);
    }

    public void addPopupButton(@DrawableRes int drawableId, BooleanSupplier visible, Runnable action) {
        ImageButton button = inflateButton(drawableId);
        button.setOnClickListener(v -> {
            action.run();
            popupWindow.dismiss();
        });
        popupView.addView(button);
        popupActionButtons.add(new ActionButton(button, visible, null));
    }

    public void invalidate() {
        for (ActionButton button : actionButtons) {
            button.view.setEnabled(button.enabled.getAsBoolean());
        }
    }

    private static class ActionButton {

        private final ImageButton view;
        private final BooleanSupplier visible;
        private final BooleanSupplier enabled;

        public ActionButton(ImageButton view, BooleanSupplier visible, BooleanSupplier enabled) {
            this.view = view;
            this.visible = visible;
            this.enabled = enabled;
        }
    }
}
