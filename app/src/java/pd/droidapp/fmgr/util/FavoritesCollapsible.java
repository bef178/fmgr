package pd.droidapp.fmgr.util;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

import pd.droidapp.fmgr.R;
import pd.util.PathOps;

public class FavoritesCollapsible {

    private final View selfView;

    private final ImageView favTriangle;
    private final TextView favTitle;
    private final RecyclerView favItemsView;

    private final FavStore favStore;
    private final FavItemAdapter favItemAdapter;

    private boolean isFavItemsViewCollapsed = false;

    private Consumer<File> onFavDirectoryClickedListener;

    public FavoritesCollapsible(View selfView) {
        this.selfView = selfView;

        favTriangle = selfView.findViewById(R.id.favorites_triangle);
        favTitle = selfView.findViewById(R.id.favorites_title);
        favItemsView = selfView.findViewById(R.id.favorites_list);

        Context context = selfView.getContext();
        favStore = new FavStore(context);

        favItemAdapter = new FavItemAdapter();
        favItemAdapter.whenFavItemClicked(favItem -> {
            File file = new File(favItem.path);
            if (file.isDirectory()) {
                if (onFavDirectoryClickedListener != null) {
                    onFavDirectoryClickedListener.accept(file);
                }
            } else {
                Toast.makeText(selfView.getContext(), R.string.error_directory_not_accessible, Toast.LENGTH_SHORT).show();
            }
        });
        favItemAdapter.whenFavIconClicked(favItem -> {
            favStore.remove(favItem);
            invalidate();
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
                            favItemAdapter.invalidate(favStore.getAll());
                        }
                        return true;
                    });
            editPopup.show();
        });

        favTriangle.setOnClickListener(v -> toggleFavItemsView());

        favItemsView.setLayoutManager(new LinearLayoutManager(context));
        favItemsView.setAdapter(favItemAdapter);
    }

    public void whenFavDirectoryClicked(Consumer<File> onFavDirectoryClickedListener) {
        this.onFavDirectoryClickedListener = onFavDirectoryClickedListener;
    }

    public void invalidate() {
        List<FavItem> favItems = favStore.getAll();
        if (favItems.isEmpty()) {
            selfView.setVisibility(View.GONE);
            return;
        }
        selfView.setVisibility(View.VISIBLE);
        favTitle.setText(selfView.getContext().getString(R.string.home_favorites_title, favItems.size()));
        favItemAdapter.invalidate(favItems);
    }

    private void toggleFavItemsView() {
        isFavItemsViewCollapsed = !isFavItemsViewCollapsed;
        favItemsView.setVisibility(isFavItemsViewCollapsed ? View.GONE : View.VISIBLE);

        // rotate the triangle
        float targetRotation = isFavItemsViewCollapsed ? -90f : 0f;
        float currentRotation = favTriangle.getRotation();
        ValueAnimator animator = ValueAnimator.ofFloat(currentRotation, targetRotation);
        animator.setDuration(200);
        animator.addUpdateListener(animation -> {
            float rotation = (float) animation.getAnimatedValue();
            favTriangle.setRotation(rotation);
        });
        animator.start();
    }

    static class FavItem {

        public final String path;

        private String displayName;

        FavItem(String path) {
            this(path, null);
        }

        FavItem(String path, String displayName) {
            this.path = path;
            this.displayName = displayName;
        }

        String getDisplayName() {
            if (displayName == null || displayName.isEmpty()) {
                return getDefaultName();
            }
            return displayName;
        }

        void setDisplayName(String displayName) {
            if (displayName != null && !displayName.isEmpty()) {
                this.displayName = displayName;
            } else {
                this.displayName = null;
            }
        }

        String getDefaultName() {
            return PathOps.singleton.basename(path);
        }
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
            viewHolder.pathText.setText(Util.getDisplayPath(favItem.path));

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

        @SuppressLint("NotifyDataSetChanged")
        void invalidate(List<FavItem> favItems) {
            this.favItems = favItems;

            // must not many fav items
            notifyDataSetChanged();
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
