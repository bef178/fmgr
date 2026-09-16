package pd.droidapp.fmgr.fragment;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.popup.EditPopup;
import pd.droidapp.fmgr.util.FavStore;
import pd.util.FileOps;

import static pd.droidapp.fmgr.util.FavStore.FavItem;
import static pd.droidapp.fmgr.util.Util.animateCollapsed;
import static pd.droidapp.fmgr.util.Util.getDisplayPath;

public class FavoritesCollapsible {

    private final View selfView;

    private final ImageView favTriangle;
    private final TextView favTitle;
    private final RecyclerView favItemsView;

    private final FavStore favStore;
    private final FavItemAdapter favItemAdapter;

    private boolean isFavItemsViewCollapsed = false;

    private Consumer<String> onFavDirectoryClickedListener;

    public FavoritesCollapsible(View selfView) {
        this.selfView = selfView;

        favTriangle = selfView.findViewById(R.id.favorites_triangle);
        favTitle = selfView.findViewById(R.id.favorites_title);
        favItemsView = selfView.findViewById(R.id.favorites_list);

        Context context = selfView.getContext();
        favStore = new FavStore(context);

        favItemAdapter = new FavItemAdapter();
        favItemAdapter.whenFavItemClicked(favItem -> {
            if (FileOps.singleton.stat(favItem.path).isDirectory(true)) {
                if (onFavDirectoryClickedListener != null) {
                    onFavDirectoryClickedListener.accept(favItem.path);
                }
            } else {
                Toast.makeText(selfView.getContext(), R.string.error_directory_not_accessible, Toast.LENGTH_SHORT).show();
            }
        });
        favItemAdapter.whenFavIconClicked(favItem -> {
            favStore.remove(favItem);
            favItemAdapter.remove(favItem);
            invalidateHeader();
        });
        favItemAdapter.whenFavEditClicked(favItem -> {
            EditPopup editPopup = new EditPopup(selfView,
                    context.getString(R.string.edit_favorite_name),
                    favItem.getDisplayName(),
                    favItem.getDefaultName(),
                    newName -> {
                        newName = newName.trim();
                        if (!newName.equals(favItem.getDisplayName())) {
                            favItem.setDisplayName(newName);
                            favStore.put(favItem);
                            favItemAdapter.invalidate(favItem);
                        }
                        return true;
                    });
            editPopup.show();
        });

        favTriangle.setOnClickListener(v -> toggleFavItemsView());

        favItemsView.setLayoutManager(new LinearLayoutManager(context));
        favItemsView.setAdapter(favItemAdapter);
    }

    public void whenFavDirectoryClicked(Consumer<String> onFavDirectoryClickedListener) {
        this.onFavDirectoryClickedListener = onFavDirectoryClickedListener;
    }

    public void invalidate() {
        favItemAdapter.set(favStore.getAll());
        invalidateHeader();
    }

    private void invalidateHeader() {
        int size = favItemAdapter.getItemCount();
        if (size == 0) {
            selfView.setVisibility(View.GONE);
            return;
        }
        selfView.setVisibility(View.VISIBLE);
        favTitle.setText(selfView.getContext().getString(R.string.home_favorites_title, size));
    }

    private void toggleFavItemsView() {
        isFavItemsViewCollapsed = !isFavItemsViewCollapsed;
        animateCollapsed(favTriangle, favItemsView, isFavItemsViewCollapsed);
    }

    static class FavItemAdapter extends RecyclerView.Adapter<FavItemAdapter.FavItemViewHolder> {

        private List<FavItem> favItems;
        private Consumer<FavItem> onFavItemClickedListener;
        private Consumer<FavItem> onFavIconClickedListener;
        private Consumer<FavItem> onFavEditClickedListener;

        @NonNull
        @Override
        public FavItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.favorite_item, parent, false);
            return new FavItemViewHolder(itemView);
        }

        public void whenFavItemClicked(Consumer<FavItem> onFavItemClickedListener) {
            this.onFavItemClickedListener = onFavItemClickedListener;
        }

        public void whenFavIconClicked(Consumer<FavItem> onFavIconClickedListener) {
            this.onFavIconClickedListener = onFavIconClickedListener;
        }

        public void whenFavEditClicked(Consumer<FavItem> onFavEditClickedListener) {
            this.onFavEditClickedListener = onFavEditClickedListener;
        }

        @Override
        public void onBindViewHolder(@NonNull FavItemViewHolder viewHolder, int position) {
            FavItem favItem = favItems.get(position);

            viewHolder.nameText.setText(favItem.getDisplayName());
            viewHolder.pathText.setText(getDisplayPath(favItem.path));

            viewHolder.itemView.setOnClickListener(v -> {
                if (onFavItemClickedListener != null) {
                    onFavItemClickedListener.accept(favItem);
                }
            });

            viewHolder.favIcon.setImageResource(R.drawable.round_star_24);
            viewHolder.favIcon.setOnClickListener(v -> {
                if (onFavIconClickedListener != null) {
                    onFavIconClickedListener.accept(favItem);
                }
            });

            viewHolder.nameEditButton.setOnClickListener(v -> {
                if (onFavEditClickedListener != null) {
                    onFavEditClickedListener.accept(favItem);
                }
            });
        }

        @Override
        public int getItemCount() {
            return favItems == null ? 0 : favItems.size();
        }

        void set(List<FavItem> newItems) {
            List<FavItem> oldItems = favItems;
            favItems = newItems;
            dispatchDiff(oldItems);
        }

        private void dispatchDiff(List<FavItem> oldItems) {
            DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return oldItems == null ? 0 : oldItems.size();
                }

                @Override
                public int getNewListSize() {
                    return favItems.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return oldItems.get(oldItemPosition).path.equals(favItems.get(newItemPosition).path);
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return oldItems.get(oldItemPosition).getDisplayName()
                            .equals(favItems.get(newItemPosition).getDisplayName());
                }
            }).dispatchUpdatesTo(this);
        }

        void invalidate(FavItem favItem) {
            int index = indexOf(favItem);
            if (index >= 0) {
                notifyItemChanged(index);
            }
        }

        void remove(FavItem favItem) {
            int index = indexOf(favItem);
            if (index < 0) {
                return;
            }
            favItems.remove(index);
            notifyItemRemoved(index);
        }

        private int indexOf(FavItem favItem) {
            for (int i = 0; i < favItems.size(); i++) {
                if (favItems.get(i).path.equals(favItem.path)) {
                    return i;
                }
            }
            return -1;
        }

        static class FavItemViewHolder extends RecyclerView.ViewHolder {

            TextView nameText;
            ImageButton nameEditButton;
            TextView pathText;
            ImageButton favIcon;

            FavItemViewHolder(View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.favorite_name);
                pathText = itemView.findViewById(R.id.favorite_path);
                nameEditButton = itemView.findViewById(R.id.favorite_name_edit);
                favIcon = itemView.findViewById(R.id.fav_star);
            }
        }
    }
}
