package pd.droidapp.fmgr.util;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

import pd.droidapp.fmgr.R;

public class FavBox {

    private final Context context;
    private final FavItemStore favItemStore;
    private final FavItemAdapter favItemAdapter;

    private final LinearLayout favLayout;
    private final ImageView favTriangle;
    private final TextView favTitle;
    private final RecyclerView favItemsView;

    private final EditPopup editPopup;
    private boolean isFavItemsViewCollapsed = false;

    private Consumer<File> onFavDirectoryClickedListener;

    public FavBox(Context context, View containerView) {
        this.context = context;

        favLayout = containerView.findViewById(R.id.favorites_layout);
        favTriangle = containerView.findViewById(R.id.favorites_triangle);
        favTitle = containerView.findViewById(R.id.favorites_title);
        favItemsView = containerView.findViewById(R.id.favorites_list);

        favItemStore = new FavItemStore(context);

        editPopup = new EditPopup(context, favLayout);

        favItemAdapter = new FavItemAdapter();
        favItemAdapter.whenFavItemClicked(favItem -> {
            File file = new File(favItem.path);
            if (file.exists() && file.isDirectory()) {
                if (onFavDirectoryClickedListener != null) {
                    onFavDirectoryClickedListener.accept(file);
                }
            }
        });
        favItemAdapter.whenFavIconClicked(favItem -> {
            favItemStore.remove(favItem);
            invalidate();
        });
        favItemAdapter.whenFavEditClicked(favItem -> editPopup.show(
                context.getString(R.string.edit_favorite_name),
                favItem.getDisplayName(),
                favItem.getDefaultName(),
                newName -> {
                    if (!newName.equals(favItem.getDisplayName())) {
                        favItem.setDisplayName(newName);
                        favItemStore.put(favItem);
                        favItemAdapter.invalidate(favItemStore.getAll());
                    }
                    editPopup.dismiss();
                }));

        favTriangle.setOnClickListener(v -> toggleFavItemsView());

        favItemsView.setLayoutManager(new LinearLayoutManager(context));
        favItemsView.setAdapter(favItemAdapter);
    }

    public void whenFavDirectoryClicked(Consumer<File> onFavDirectoryClickedListener) {
        this.onFavDirectoryClickedListener = onFavDirectoryClickedListener;
    }

    public void invalidate() {
        List<FavItem> favItems = favItemStore.getAll();
        if (favItems.isEmpty()) {
            favLayout.setVisibility(View.GONE);
            return;
        }
        favLayout.setVisibility(View.VISIBLE);
        favTitle.setText(context.getString(R.string.home_favorites_title, favItems.size()));
        favItemAdapter.invalidate(favItems);
    }

    private void toggleFavItemsView() {
        isFavItemsViewCollapsed = !isFavItemsViewCollapsed;
        favItemsView.setVisibility(isFavItemsViewCollapsed ? View.GONE : View.VISIBLE);

        // rotate the trangle
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

        public FavItem(String path) {
            this(path, getDefaultName(path));
        }

        private FavItem(String path, String displayName) {
            this.path = path;
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        /**
         * @param displayName null for no change; empty for default name
         */
        public void setDisplayName(String displayName) {
            if (displayName == null) {
                return;
            }
            if (displayName.isEmpty()) {
                displayName = getDefaultName();
            }
            this.displayName = displayName;
        }

        String getDefaultName() {
            return getDefaultName(path);
        }

        @NonNull
        @Override
        public String toString() {
            return displayName + ":" + path;
        }

        static String getDefaultName(String path) {
            if (path == null || path.isEmpty()) {
                return "";
            }
            int j = path.length() - 1;
            while (j >= 0 && path.charAt(j) == '/') {
                j--;
            }
            if (j < 0) {
                return "/";
            }
            int i = path.lastIndexOf('/', j);
            return path.substring(i + 1, j + 1);
        }

        public static FavItem parse(String s) {
            if (s == null) {
                return null;
            }
            int i = s.indexOf(":");
            if (i < 0) {
                return new FavItem(s);
            } else {
                return new FavItem(s.substring(i + 1), s.substring(0, i));
            }
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
                    .inflate(R.layout.fav_item, parent, false);
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
            viewHolder.pathText.setText(favItem.path);

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
