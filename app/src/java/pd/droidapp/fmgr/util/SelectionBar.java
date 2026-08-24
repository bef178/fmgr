package pd.droidapp.fmgr.util;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.LayoutRes;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

import pd.droidapp.fmgr.R;

public class SelectionBar {

    private final View selfView;
    private final TextView numSelectedTextView;
    private final LinearLayout buttonsView;

    private final List<ActionButton> actionButtons = new ArrayList<>();

    public final Set<File> selectedItems = new HashSet<>();

    public SelectionBar(View selfView) {
        this.selfView = selfView;
        numSelectedTextView = selfView.findViewById(R.id.x_selected);
        buttonsView = selfView.findViewById(R.id.selection_buttons);
    }

    public void addButton(@LayoutRes int layoutResId, IntPredicate visible, View.OnClickListener listener) {
        View button = LayoutInflater.from(selfView.getContext()).inflate(layoutResId, buttonsView, false);
        button.setOnClickListener(listener);
        buttonsView.addView(button);
        actionButtons.add(new ActionButton(button, visible));
    }

    public void invalidate() {
        if (selectedItems.isEmpty()) {
            selfView.setVisibility(View.GONE);
        } else {
            numSelectedTextView.setText(selfView.getContext().getString(R.string.x_selected, selectedItems.size()));
            selfView.setVisibility(View.VISIBLE);
        }
        int count = selectedItems.size();
        for (ActionButton action : actionButtons) {
            action.view.setVisibility(action.visible.test(count) ? View.VISIBLE : View.GONE);
        }
    }

    public boolean isSelected(File file) {
        return selectedItems.contains(file);
    }

    public boolean hasSelection() {
        return !selectedItems.isEmpty();
    }

    public void toggleSelected(File file) {
        if (selectedItems.contains(file)) {
            selectedItems.remove(file);
        } else {
            selectedItems.add(file);
        }
    }

    public void addAll(List<File> files) {
        selectedItems.addAll(files);
    }

    public void clear() {
        selectedItems.clear();
    }

    public List<File> copySelectedItems() {
        return new LinkedList<>(selectedItems);
    }

    private static class ActionButton {

        final View view;
        final IntPredicate visible;

        ActionButton(View view, IntPredicate visible) {
            this.view = view;
            this.visible = visible;
        }
    }
}
