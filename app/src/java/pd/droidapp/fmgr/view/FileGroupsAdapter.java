package pd.droidapp.fmgr.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;

public class FileGroupsAdapter extends RecyclerView.Adapter<FileGroupsAdapter.FileGroupViewHolder> {

    private Consumer<String> onItemClicked;
    private Consumer<String> onItemLongClicked;
    private final Set<String> allCollapsed = new HashSet<>();

    private List<State> states = new ArrayList<>();

    public void whenItemClicked(Consumer<String> onItemClicked) {
        this.onItemClicked = onItemClicked;
    }

    public void whenItemLongClicked(Consumer<String> onItemLongClicked) {
        this.onItemLongClicked = onItemLongClicked;
    }

    public void render(List<State> states) {
        List<State> oldStates = this.states;
        this.states = new ArrayList<>(states);
        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldStates.size();
            }

            @Override
            public int getNewListSize() {
                return FileGroupsAdapter.this.states.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldStates.get(oldPos).id.equals(FileGroupsAdapter.this.states.get(newPos).id);
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                State oldState = oldStates.get(oldPos);
                State newState = FileGroupsAdapter.this.states.get(newPos);
                return oldPos == newPos
                        && oldState.title.equals(newState.title)
                        && oldState.itemStates.equals(newState.itemStates);
            }
        }).dispatchUpdatesTo(this);
    }

    public void update(State state) {
        int position = indexOf(states, state.id);
        if (position < 0) {
            return;
        }
        states.set(position, state);
        notifyItemChanged(position);
    }

    private static int indexOf(List<State> states, String id) {
        for (int i = 0; i < states.size(); i++) {
            if (states.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    public void updateOrAppend(Iterable<State> states) {
        for (State state : states) {
            int position = indexOf(this.states, state.id);
            if (position < 0) {
                this.states.add(state);
                notifyItemInserted(this.states.size() - 1);
            } else {
                this.states.set(position, state);
                notifyItemChanged(position);
            }
        }
    }

    @NonNull
    @Override
    public FileGroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View groupView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.file_group, parent, false);
        return new FileGroupViewHolder(groupView);
    }

    @Override
    public void onBindViewHolder(@NonNull FileGroupViewHolder viewHolder, int position) {
        State state = states.get(position);
        String id = state.id;
        boolean collapsed = this.allCollapsed.contains(id);

        viewHolder.titleBar.whenClicked(() -> {
            boolean futureCollapsed = !this.allCollapsed.contains(id);
            if (futureCollapsed) {
                this.allCollapsed.add(id);
            } else {
                this.allCollapsed.remove(id);
            }
            viewHolder.titleBar.render(new FileGroupTitleBar.CollapseEvent(futureCollapsed));
        });
        viewHolder.titleBar.whenAnimationEnded(nowCollapsed ->
                viewHolder.itemsView.setVisibility(nowCollapsed ? View.GONE : View.VISIBLE));
        viewHolder.titleBar.render(new FileGroupTitleBar.State(state.title, String.valueOf(position + 1), collapsed));

        viewHolder.itemsView.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        renderItems(viewHolder, state);
    }

    private void renderItems(FileGroupViewHolder viewHolder, State state) {
        int nowCount = viewHolder.itemsView.getChildCount();
        int requiredCount = state.itemStates.size();

        LayoutInflater layoutInflater = LayoutInflater.from(viewHolder.itemView.getContext());
        for (int i = 0; i < requiredCount; i++) {
            FileItemBar itemBar;
            if (i < nowCount) {
                itemBar = viewHolder.itemBars.get(i);
            } else {
                View itemView = layoutInflater.inflate(R.layout.file_item, viewHolder.itemsView, false);
                viewHolder.itemsView.addView(itemView);
                itemBar = new FileItemBar(itemView);
                itemBar.whenClicked(itemState -> {
                    if (onItemClicked != null) {
                        onItemClicked.accept(itemState.path);
                    }
                });
                itemBar.whenLongClicked(itemState -> {
                    if (onItemLongClicked != null) {
                        onItemLongClicked.accept(itemState.path);
                    }
                });
                viewHolder.itemBars.add(itemBar);
            }

            itemBar.render(state.itemStates.get(i));
        }
        if (nowCount > requiredCount) {
            viewHolder.itemsView.removeViews(requiredCount, nowCount - requiredCount);
            viewHolder.itemBars.subList(requiredCount, viewHolder.itemBars.size()).clear();
        }
    }

    @Override
    public int getItemCount() {
        return states.size();
    }

    public static class FileGroupViewHolder extends RecyclerView.ViewHolder {

        final FileGroupTitleBar titleBar;
        final LinearLayout itemsView;
        final List<FileItemBar> itemBars = new ArrayList<>();

        FileGroupViewHolder(View view) {
            super(view);
            titleBar = new FileGroupTitleBar(view);
            itemsView = view.findViewById(R.id.group_items);
        }
    }

    public static class State {

        public final String id;
        public final String title;
        public final List<FileItemBar.State> itemStates;

        public State(String id, String title, FileItemBar.State... itemStates) {
            this.id = id;
            this.title = title;
            this.itemStates = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(itemStates)));
        }
    }
}
