package pd.droidapp.fmgr.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.util.Consumer;

import java.util.List;
import java.util.Objects;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.animateCollapsed;
import static pd.droidapp.fmgr.util.Util.getDisplayPath;
import static pd.droidapp.fmgr.util.Util.listOf;

public class FavoriteBar {

    private final View selfView;

    private final ImageView triangleImageView;
    private final ViewGroup itemContainerView;

    private Consumer<String> onFavoriteItemClicked;
    private Consumer<String> onFavoriteItemEditClicked;
    private Consumer<String> onFavoriteItemStarClicked;

    private boolean collapsed;

    private State state;

    public FavoriteBar(View selfView) {
        this.selfView = selfView;

        triangleImageView = selfView.findViewById(R.id.favorites_triangle);
        itemContainerView = selfView.findViewById(R.id.favorites_list);

        selfView.findViewById(R.id.favorites_title_bar).setOnClickListener(v -> {
            collapsed = !collapsed;
            animateCollapsed(triangleImageView, itemContainerView, collapsed);
        });
    }

    public void whenFavoriteItemClicked(Consumer<String> onFavoriteItemClicked) {
        this.onFavoriteItemClicked = onFavoriteItemClicked;
    }

    public void whenFavoriteItemEditClicked(Consumer<String> onFavoriteItemEditClicked) {
        this.onFavoriteItemEditClicked = onFavoriteItemEditClicked;
    }

    public void whenFavoriteItemStarClicked(Consumer<String> onFavoriteItemStarClicked) {
        this.onFavoriteItemStarClicked = onFavoriteItemStarClicked;
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

        if (oldState == null || !Objects.equals(oldState.itemStates, state.itemStates)) {
            renderItems(state.itemStates);

            if (state.itemStates.isEmpty()) {
                selfView.setVisibility(View.GONE);
            } else {
                selfView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void renderItems(List<ItemState> itemStates) {
        itemContainerView.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(itemContainerView.getContext());
        for (ItemState itemState : itemStates) {
            itemContainerView.addView(createItemView(inflater, itemState));
        }
    }

    private View createItemView(LayoutInflater inflater, ItemState itemState) {
        View itemView = inflater.inflate(R.layout.favorite_item, itemContainerView, false);

        TextView nameText = itemView.findViewById(R.id.favorite_name);
        ImageButton editButton = itemView.findViewById(R.id.favorite_edit);
        TextView pathText = itemView.findViewById(R.id.favorite_path);
        ImageButton starIcon = itemView.findViewById(R.id.favorite_star);
        starIcon.setSelected(true);

        itemView.setOnClickListener(v -> {
            if (onFavoriteItemClicked != null) {
                onFavoriteItemClicked.accept(itemState.path);
            }
        });

        nameText.setText(itemState.name);

        editButton.setOnClickListener(v -> {
            if (onFavoriteItemEditClicked != null) {
                onFavoriteItemEditClicked.accept(itemState.path);
            }
        });

        pathText.setText(getDisplayPath(itemState.path));

        starIcon.setOnClickListener(v -> {
            if (onFavoriteItemStarClicked != null) {
                onFavoriteItemStarClicked.accept(itemState.path);
            }
        });

        return itemView;
    }

    public static class State {

        public final List<ItemState> itemStates;

        public State(ItemState... itemStates) {
            this.itemStates = listOf(itemStates);
        }
    }

    public static class ItemState {

        public final String path;
        public final String name;

        public ItemState(String path, String name) {
            this.path = path;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof ItemState)) {
                return false;
            }
            ItemState another = (ItemState) o;
            return path.equals(another.path)
                    && name.equals(another.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, name);
        }
    }
}
