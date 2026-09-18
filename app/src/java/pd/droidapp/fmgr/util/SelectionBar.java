package pd.droidapp.fmgr.util;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.LayoutRes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.ActionBar.ActionButton;

public class SelectionBar {

    private final View selfView;
    private final TextView numSelectedTextView;
    private final LinearLayout buttonsView;

    private final List<ActionButton> actionButtons = new ArrayList<>();

    private final Set<String> selectedPaths = new LinkedHashSet<>();

    public SelectionBar(View selfView) {
        this.selfView = selfView;
        numSelectedTextView = selfView.findViewById(R.id.x_selected);
        buttonsView = selfView.findViewById(R.id.selection_buttons);
    }

    public void addButton(@LayoutRes int layoutResId, IntPredicate visible, View.OnClickListener listener) {
        View button = LayoutInflater.from(selfView.getContext()).inflate(layoutResId, buttonsView, false);
        button.setOnClickListener(listener);
        buttonsView.addView(button);
        actionButtons.add(new ActionButton(button, () -> visible.test(selectedPaths.size()), null));
    }

    public void invalidate() {
        int count = selectedPaths.size();
        if (count == 0) {
            selfView.setVisibility(View.GONE);
        } else {
            numSelectedTextView.setText(selfView.getContext().getString(R.string.x_selected, count));
            selfView.setVisibility(View.VISIBLE);
        }
        for (ActionButton action : actionButtons) {
            action.view.setVisibility(action.visible.getAsBoolean() ? View.VISIBLE : View.GONE);
        }
    }

    public void addProps(Collection<FileProperties> items) {
        for (FileProperties item : items) {
            selectedPaths.add(item.path);
        }
    }

    public void add(Collection<String> paths) {
        selectedPaths.addAll(paths);
    }

    public void removeProps(Collection<FileProperties> items) {
        for (FileProperties item : items) {
            selectedPaths.remove(item.path);
        }
    }

    public void clear() {
        selectedPaths.clear();
    }

    public boolean isEmpty() {
        return selectedPaths.isEmpty();
    }

    public int size() {
        return selectedPaths.size();
    }

    public Collection<String> getAll() {
        return selectedPaths;
    }

    public String getFirst() {
        return selectedPaths.iterator().next();
    }

    public boolean hasSelected(FileProperties item) {
        return selectedPaths.contains(item.path);
    }

    public void toggleSelected(FileProperties item) {
        if (selectedPaths.contains(item.path)) {
            selectedPaths.remove(item.path);
        } else {
            selectedPaths.add(item.path);
        }
    }
}
