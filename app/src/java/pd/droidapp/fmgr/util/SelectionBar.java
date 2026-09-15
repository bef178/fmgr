package pd.droidapp.fmgr.util;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.LayoutRes;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

import pd.droidapp.fmgr.R;

public class SelectionBar {

    private final View selfView;
    private final TextView numSelectedTextView;
    private final LinearLayout buttonsView;

    private final List<ActionButton> actionButtons = new ArrayList<>();

    private final Set<File> selectedItems = new LinkedHashSet<>();

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
        int count = selectedItems.size();
        if (count == 0) {
            selfView.setVisibility(View.GONE);
        } else {
            numSelectedTextView.setText(selfView.getContext().getString(R.string.x_selected, count));
            selfView.setVisibility(View.VISIBLE);
        }
        for (ActionButton action : actionButtons) {
            action.view.setVisibility(action.visible.test(count) ? View.VISIBLE : View.GONE);
        }
    }

    public void addFiles(List<File> files) {
        selectedItems.addAll(files);
    }

    public void add(Collection<String> paths) {
        for (String path : paths) {
            selectedItems.add(new File(path));
        }
    }

    public void remove(Collection<String> paths) {
        Set<File> files = new HashSet<>();
        for (String path : paths) {
            files.add(new File(path));
        }
        selectedItems.removeAll(files);
    }

    public void clear() {
        selectedItems.clear();
    }

    public boolean isEmpty() {
        return selectedItems.isEmpty();
    }

    public int size() {
        return selectedItems.size();
    }

    public File getFirst() {
        return selectedItems.iterator().next();
    }

    public boolean hasSelected(File file) {
        return selectedItems.contains(file);
    }

    public boolean hasSelected(String path) {
        return selectedItems.contains(new File(path));
    }

    public void toggleSelected(File file) {
        if (selectedItems.contains(file)) {
            selectedItems.remove(file);
        } else {
            selectedItems.add(file);
        }
    }

    public void toggleSelected(String path) {
        toggleSelected(new File(path));
    }

    public Collection<File> getSelectedItems() {
        return selectedItems;
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
