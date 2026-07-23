package pd.droidapp.fmgr.util;

import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import pd.droidapp.fmgr.R;

public class StatusBar {

    private final View selfView;
    private final ImageView iconView;
    private final TextView textView;

    public StatusBar(View selfView) {
        this.selfView = selfView;
        iconView = selfView.findViewById(R.id.status_icon);
        textView = selfView.findViewById(R.id.status_text);
    }

    public void setText(CharSequence value) {
        textView.setText(value);
    }

    public void markRunning(CharSequence value) {
        selfView.setVisibility(View.VISIBLE);
        iconView.setImageResource(R.drawable.baseline_refresh_24);
        RotateAnimation rotateAnim = new RotateAnimation(0, 360,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnim.setDuration(1000);
        rotateAnim.setRepeatCount(Animation.INFINITE);
        iconView.startAnimation(rotateAnim);
        textView.setText(value);
    }

    public void markDone() {
        iconView.clearAnimation();
        iconView.setImageResource(R.drawable.baseline_done_24);
    }

    public void hide() {
        selfView.setVisibility(View.GONE);
        iconView.clearAnimation();
        iconView.setImageResource(android.R.color.transparent);
        textView.setText("");
    }
}
