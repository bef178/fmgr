package pd.droidapp.fmgr.util;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.LayoutRes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.ActionBar.ActionButton;

public class SelectionBar {

    private final View selfView;
    private final TextView numSelectedTextView;
    private final LinearLayout buttonsView;

    private final List<ActionButton> actionButtons = new ArrayList<>();

    public SelectionBar(View selfView) {
        this.selfView = selfView;
        numSelectedTextView = selfView.findViewById(R.id.x_selected);
        buttonsView = selfView.findViewById(R.id.selection_buttons);
    }

    public void addButton(@LayoutRes int layoutId, BooleanSupplier visible, View.OnClickListener listener) {
        View button = LayoutInflater.from(selfView.getContext()).inflate(layoutId, buttonsView, false);
        button.setOnClickListener(listener);
        buttonsView.addView(button);
        actionButtons.add(new ActionButton(button, visible, null));
    }

    public void render(int numSelected) {
        if (numSelected == 0) {
            selfView.setVisibility(View.GONE);
        } else {
            numSelectedTextView.setText(selfView.getContext().getString(R.string.x_selected, numSelected));
            selfView.setVisibility(View.VISIBLE);
        }
        for (ActionButton action : actionButtons) {
            action.view.setVisibility(action.visible.getAsBoolean() ? View.VISIBLE : View.GONE);
        }
    }
}
