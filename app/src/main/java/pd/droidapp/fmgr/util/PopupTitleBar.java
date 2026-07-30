package pd.droidapp.fmgr.util;

import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.StringRes;

import pd.droidapp.fmgr.R;

public class PopupTitleBar {

    private final TextView titleTextView;
    private final ImageButton closeButton;

    public PopupTitleBar(View selfView) {
        titleTextView = selfView.findViewById(R.id.popup_title);
        closeButton = selfView.findViewById(R.id.popup_close);
    }

    public void setTitle(@StringRes int textResId) {
        titleTextView.setText(textResId);
    }

    public void setTitle(CharSequence text) {
        titleTextView.setText(text);
    }

    public void whenCloseButtonClicked(View.OnClickListener listener) {
        closeButton.setOnClickListener(listener);
    }

    public void enableCloseButton(boolean enabled) {
        closeButton.setEnabled(enabled);
    }

    public boolean isCloseButtonEnabled() {
        return closeButton.isEnabled();
    }
}
