package pd.droidapp.fmgr.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;

public class PopupFileItemAdapter extends RecyclerView.Adapter<PopupFileItemAdapter.ItemViewHolder> {

    private Consumer<String> onItemClicked;
    private Consumer<String> onItemLongClicked;

    private List<PopupFileItemBar.State> states = new ArrayList<>();

    public void whenItemClicked(Consumer<String> onItemClicked) {
        this.onItemClicked = onItemClicked;
    }

    public void whenItemLongClicked(Consumer<String> onItemLongClicked) {
        this.onItemLongClicked = onItemLongClicked;
    }

    public void render(List<PopupFileItemBar.State> states) {
        List<PopupFileItemBar.State> oldStates = this.states;
        this.states = new ArrayList<>(states);

        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldStates.size();
            }

            @Override
            public int getNewListSize() {
                return PopupFileItemAdapter.this.states.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldStates.get(oldPos).itemId.equals(PopupFileItemAdapter.this.states.get(newPos).itemId);
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                return oldStates.get(oldPos).equals(PopupFileItemAdapter.this.states.get(newPos));
            }
        }).dispatchUpdatesTo(this);
    }

    public void append(List<PopupFileItemBar.State> states) {
        if (states.isEmpty()) {
            return;
        }
        int start = this.states.size();
        this.states.addAll(states);
        notifyItemRangeInserted(start, states.size());
    }

    public void update(int position, PopupFileItemBar.State state) {
        if (this.states.get(position).equals(state)) {
            return;
        }
        this.states.set(position, state);
        notifyItemChanged(position);
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.file_item, parent, false);
        ItemViewHolder viewHolder = new ItemViewHolder(view);
        if (onItemClicked != null) {
            viewHolder.itemBar.whenClicked(state -> onItemClicked.accept(state.itemId));
        }
        if (onItemLongClicked != null) {
            viewHolder.itemBar.whenLongClicked(state -> onItemLongClicked.accept(state.itemId));
        }
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder viewHolder, int position) {
        viewHolder.itemBar.render(states.get(position));
    }

    @Override
    public int getItemCount() {
        return states.size();
    }

    public static class ItemViewHolder extends RecyclerView.ViewHolder {

        final PopupFileItemBar itemBar;

        ItemViewHolder(View view) {
            super(view);
            itemBar = new PopupFileItemBar(view);
        }
    }
}
