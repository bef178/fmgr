package pd.droidapp.fmgr.popup;

import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.DrawableRes;

import java.util.LinkedList;
import java.util.List;
import java.util.function.BooleanSupplier;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.ActionBar.ActionButton;

public class StatusBar {

    private final LinearLayout selfView;
    private final ImageView iconView;
    private final TextView textView;

    private final List<ActionButton> actionButtons = new LinkedList<>();

    public StatusBar(LinearLayout selfView) {
        this.selfView = selfView;
        iconView = selfView.findViewById(R.id.status_icon);
        textView = selfView.findViewById(R.id.status_text);
    }

    public void addButton(@DrawableRes int drawableId, BooleanSupplier visible, BooleanSupplier enabled, Runnable action) {
        ImageButton button = (ImageButton) LayoutInflater.from(selfView.getContext())
                .inflate(R.layout.status_button, selfView, false);
        button.setImageResource(drawableId);
        button.setOnClickListener(v -> action.run());
        selfView.addView(button);

        actionButtons.add(new ActionButton(button, visible, enabled));
    }

    public void markReady(@DrawableRes int drawableId) {
        iconView.clearAnimation();
        iconView.setImageResource(drawableId);
    }

    public void markRunning() {
        iconView.setImageResource(R.drawable.baseline_refresh_24);
        RotateAnimation rotateAnim = new RotateAnimation(0, 360,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnim.setDuration(1000);
        rotateAnim.setRepeatCount(Animation.INFINITE);
        iconView.startAnimation(rotateAnim);
    }

    public void markDone() {
        iconView.clearAnimation();
        iconView.setImageResource(R.drawable.baseline_done_24);
    }

    public void markStopped() {
        iconView.clearAnimation();
        iconView.setImageResource(R.drawable.baseline_close_24);
    }

    public void setText(CharSequence value) {
        textView.setText(value);
    }

    public void invalidateButtons() {
        for (ActionButton actionButton : actionButtons) {
            actionButton.view.setVisibility(actionButton.visible.getAsBoolean() ? View.VISIBLE : View.GONE);
            actionButton.view.setEnabled(actionButton.enabled.getAsBoolean());
        }
    }
}
