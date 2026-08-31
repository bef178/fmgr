package pd.droidapp.fmgr.util;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.annotation.StringRes;

import java.util.LinkedList;
import java.util.List;
import java.util.function.BooleanSupplier;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.ActionBar.ActionButton;

public class PopupButtonBar {

    private final LinearLayout buttonsView;
    private final List<ActionButton> actionButtons = new LinkedList<>();

    public PopupButtonBar(LinearLayout selfView) {
        buttonsView = selfView;
    }

    public void addButton(@StringRes int stringId,
                          BooleanSupplier visible,
                          BooleanSupplier enabled,
                          View.OnClickListener onClick) {
        Button button = (Button) LayoutInflater.from(buttonsView.getContext())
                .inflate(R.layout.popup_button, buttonsView, false);
        button.setText(stringId);
        button.setOnClickListener(onClick);
        buttonsView.addView(button);
        actionButtons.add(new ActionButton(button, visible, enabled));
    }

    public void invalidate() {
        for (ActionButton action : actionButtons) {
            action.view.setVisibility(action.visible.getAsBoolean() ? View.VISIBLE : View.GONE);
            action.view.setEnabled(action.enabled.getAsBoolean());
        }
    }
}
