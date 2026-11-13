package pd.droidapp.fmgr.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;

public class FileGroupAdapter extends RecyclerView.Adapter<FileGroupAdapter.ViewHolder> {

    private Consumer<String> onItemClicked;
    private Consumer<String> onItemLongClicked;

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
                return FileGroupAdapter.this.states.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldStates.get(oldPos).id.equals(FileGroupAdapter.this.states.get(newPos).id);
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                State oldState = oldStates.get(oldPos);
                State newState = FileGroupAdapter.this.states.get(newPos);
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
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View groupView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.file_group, parent, false);
        ViewHolder viewHolder = new ViewHolder(groupView);
        viewHolder.groupBar.whenItemClicked(itemState -> {
            if (onItemClicked != null) {
                onItemClicked.accept(itemState.itemId);
            }
        });
        viewHolder.groupBar.whenItemLongClicked(itemState -> {
            if (onItemLongClicked != null) {
                onItemLongClicked.accept(itemState.itemId);
            }
        });
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        State state = states.get(position);
        viewHolder.groupBar.render(new FileGroupBar.State(state.title, state.itemStates));
    }

    @Override
    public int getItemCount() {
        return states.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        final FileGroupBar groupBar;

        ViewHolder(View view) {
            super(view);
            groupBar = new FileGroupBar(view);
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
