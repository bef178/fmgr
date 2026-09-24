package pd.droidapp.fmgr.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Objects;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.animateCollapsed;
import static pd.droidapp.fmgr.util.Util.getDisplayPath;
import static pd.droidapp.fmgr.util.Util.listOf;

public class FavoritesBar {

    private final View selfView;

    private final ImageView triangleImageView;
    private final TextView titleTextView;
    private final RecyclerView itemsView;
    private final ItemsAdapter itemsAdapter;

    private Consumer<String> onFavoriteClicked;
    private Consumer<String> onFavoriteRemoved;
    private Consumer<String> onFavoriteEditClicked;

    private boolean collapsed;

    private State state;

    public FavoritesBar(View selfView) {
        this.selfView = selfView;

        triangleImageView = selfView.findViewById(R.id.favorites_triangle);
        titleTextView = selfView.findViewById(R.id.favorites_title);
        itemsView = selfView.findViewById(R.id.favorites_list);
        itemsAdapter = new ItemsAdapter();

        triangleImageView.setOnClickListener(v -> toggleCollapsed());
        itemsAdapter.whenFavItemClicked(favItemState -> {
            if (onFavoriteClicked != null) {
                onFavoriteClicked.accept(favItemState.path);
            }
        });
        itemsAdapter.whenFavIconClicked(favItemState -> {
            if (onFavoriteRemoved != null) {
                onFavoriteRemoved.accept(favItemState.path);
            }
        });
        itemsAdapter.whenFavEditClicked(favItemState -> {
            if (onFavoriteEditClicked != null) {
                onFavoriteEditClicked.accept(favItemState.path);
            }
        });
        itemsView.setLayoutManager(new LinearLayoutManager(selfView.getContext()));
        itemsView.setAdapter(itemsAdapter);
    }

    public void whenFavoriteClicked(Consumer<String> onFavoriteClicked) {
        this.onFavoriteClicked = onFavoriteClicked;
    }

    public void whenFavoriteRemoved(Consumer<String> onFavoriteRemoved) {
        this.onFavoriteRemoved = onFavoriteRemoved;
    }

    public void whenFavoriteEditClicked(Consumer<String> onFavoriteEditClicked) {
        this.onFavoriteEditClicked = onFavoriteEditClicked;
    }

    /**
     * null state for no change
     */
    public void render(State state) {
        if (state == null) {
            return;
        }

        State oldState = this.state;
        this.state = state;

        if (oldState == null || !Objects.equals(oldState.favItemStates, state.favItemStates)) {
            itemsAdapter.set(state.favItemStates);

            int size = state.favItemStates.size();
            if (size == 0) {
                selfView.setVisibility(View.GONE);
            } else {
                selfView.setVisibility(View.VISIBLE);
                titleTextView.setText(selfView.getContext().getString(R.string.home_favorites_title, size));
            }
        }
    }

    private void toggleCollapsed() {
        collapsed = !collapsed;
        animateCollapsed(triangleImageView, itemsView, collapsed);
    }

    public static class FavItemState {

        public final String path;
        public final String name;

        public FavItemState(String path, String name) {
            this.path = path;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof FavItemState)) {
                return false;
            }
            FavItemState another = (FavItemState) o;
            return path.equals(another.path)
                    && name.equals(another.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, name);
        }
    }

    public static class State {

        public final List<FavItemState> favItemStates;

        public State(FavItemState... favItemStates) {
            this.favItemStates = listOf(favItemStates);
        }
    }

    static class ItemsAdapter extends RecyclerView.Adapter<ItemsAdapter.ItemViewHolder> {

        private List<FavItemState> items;
        private Consumer<FavItemState> onFavItemClickedListener;
        private Consumer<FavItemState> onFavIconClickedListener;
        private Consumer<FavItemState> onFavEditClickedListener;

        @NonNull
        @Override
        public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.favorite_item, parent, false);
            return new ItemViewHolder(itemView);
        }

        public void whenFavItemClicked(Consumer<FavItemState> onFavItemClickedListener) {
            this.onFavItemClickedListener = onFavItemClickedListener;
        }

        public void whenFavIconClicked(Consumer<FavItemState> onFavIconClickedListener) {
            this.onFavIconClickedListener = onFavIconClickedListener;
        }

        public void whenFavEditClicked(Consumer<FavItemState> onFavEditClickedListener) {
            this.onFavEditClickedListener = onFavEditClickedListener;
        }

        @Override
        public void onBindViewHolder(@NonNull ItemViewHolder viewHolder, int position) {
            FavItemState item = items.get(position);

            viewHolder.itemView.setOnClickListener(v -> {
                if (onFavItemClickedListener != null) {
                    onFavItemClickedListener.accept(item);
                }
            });

            viewHolder.nameText.setText(item.name);

            viewHolder.editButton.setOnClickListener(v -> {
                if (onFavEditClickedListener != null) {
                    onFavEditClickedListener.accept(item);
                }
            });

            viewHolder.pathText.setText(getDisplayPath(item.path));

            viewHolder.starIcon.setOnClickListener(v -> {
                if (onFavIconClickedListener != null) {
                    onFavIconClickedListener.accept(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items == null ? 0 : items.size();
        }

        void set(List<FavItemState> newItems) {
            List<FavItemState> oldItems = items;
            items = newItems;
            DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return oldItems == null ? 0 : oldItems.size();
                }

                @Override
                public int getNewListSize() {
                    return items.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return oldItems.get(oldItemPosition).path.equals(items.get(newItemPosition).path);
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return oldItems.get(oldItemPosition).equals(items.get(newItemPosition));
                }
            }).dispatchUpdatesTo(this);
        }

        static class ItemViewHolder extends RecyclerView.ViewHolder {

            TextView nameText;
            ImageButton editButton;
            TextView pathText;
            ImageButton starIcon;

            ItemViewHolder(View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.favorite_name);
                editButton = itemView.findViewById(R.id.favorite_edit);
                pathText = itemView.findViewById(R.id.favorite_path);
                starIcon = itemView.findViewById(R.id.favorite_star);
            }
        }
    }
}
