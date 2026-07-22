package pd.droidapp.fmgr.util;

import android.content.Context;
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

    private final Context context;
    private final View componentView;
    private final TextView numSelectedTextView;
    private final LinearLayout buttonsView;

    private final List<ActionButton> actionButtons = new ArrayList<>();

    public final Set<File> selectedFiles = new HashSet<>();

    public SelectionBar(Context context, View componentView) {
        this.context = context;
        this.componentView = componentView;
        numSelectedTextView = componentView.findViewById(R.id.x_selected);
        buttonsView = componentView.findViewById(R.id.selection_buttons);
    }

    public void addButton(@LayoutRes int layoutResId, IntPredicate visible, View.OnClickListener listener) {
        View button = LayoutInflater.from(context).inflate(layoutResId, buttonsView, false);
        button.setOnClickListener(listener);
        buttonsView.addView(button);
        actionButtons.add(new ActionButton(button, visible));
    }

    public void invalidate() {
        if (selectedFiles.isEmpty()) {
            componentView.setVisibility(View.GONE);
        } else {
            numSelectedTextView.setText(context.getString(R.string.x_selected, selectedFiles.size()));
            componentView.setVisibility(View.VISIBLE);
        }
        int count = selectedFiles.size();
        for (ActionButton action : actionButtons) {
            action.view.setVisibility(action.visible.test(count) ? View.VISIBLE : View.GONE);
        }
    }

    public boolean isSelected(File file) {
        return selectedFiles.contains(file);
    }

    public boolean hasSelection() {
        return !selectedFiles.isEmpty();
    }

    public void toggleSelected(File file) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file);
        } else {
            selectedFiles.add(file);
        }
    }

    public void addAll(List<File> files) {
        selectedFiles.addAll(files);
    }

    public void clear() {
        selectedFiles.clear();
    }

    public List<File> copySelectedFiles() {
        return new LinkedList<>(selectedFiles);
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
