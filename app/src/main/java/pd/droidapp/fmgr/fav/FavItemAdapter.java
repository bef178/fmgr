package pd.droidapp.fmgr.fav;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.function.BiConsumer;

import pd.droidapp.fmgr.R;

public class FavItemAdapter extends RecyclerView.Adapter<FavItemViewHolder> {

    private List<FavItem> favItems;
    private BiConsumer<FavItemViewHolder, FavItem> onBindViewListener;

    @NonNull
    @Override
    public FavItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.favorite_item, parent, false);
        return new FavItemViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull FavItemViewHolder viewHolder, int position) {
        FavItem favItem = favItems.get(position);
        if (onBindViewListener != null) {
            onBindViewListener.accept(viewHolder, favItem);
        }
    }

    @Override
    public int getItemCount() {
        return favItems == null ? 0 : favItems.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    void invalidate(List<FavItem> favItems) {
        this.favItems = favItems;

        // must not many fav items
        notifyDataSetChanged();
    }

    void whenBindFavItemView(BiConsumer<FavItemViewHolder, FavItem> onBindViewListener) {
        this.onBindViewListener = onBindViewListener;
    }
}
