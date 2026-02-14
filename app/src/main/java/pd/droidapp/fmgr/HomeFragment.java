package pd.droidapp.fmgr;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class HomeFragment extends Fragment {

    private View favoritesLayout;
    private ImageView favoritesTrangle;
    private TextView favoritesTitle;
    private View favoritesList;

    private FavoritesManager favoritesManager;
    private FavItemAdapter favItemAdapter;

    private boolean isCollapsed = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.home_fragment, container, false);

        favoritesManager = new FavoritesManager(requireContext());
        favItemAdapter = new FavItemAdapter();

        favoritesLayout = view.findViewById(R.id.favorites_layout);

        favoritesTrangle = view.findViewById(R.id.favorites_trangle);
        favoritesTrangle.setOnClickListener(v -> toggleCollapseFavorites());

        favoritesTitle = view.findViewById(R.id.favorites_title);

        favoritesList = view.findViewById(R.id.favorites_list);

        RecyclerView recyclerView = view.findViewById(R.id.favorites_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(favItemAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        favItemAdapter.invalidate();
    }

    private void toggleCollapseFavorites() {
        isCollapsed = !isCollapsed;
        favoritesList.setVisibility(isCollapsed ? View.GONE : View.VISIBLE);

        // rotate the trangle
        float targetRotation = isCollapsed ? -90f : 0f;
        float currentRotation = favoritesTrangle.getRotation();
        ValueAnimator animator = ValueAnimator.ofFloat(currentRotation, targetRotation);
        animator.setDuration(200);
        animator.addUpdateListener(animation -> {
            float rotation = (float) animation.getAnimatedValue();
            favoritesTrangle.setRotation(rotation);
        });
        animator.start();
    }

    private class FavItemAdapter extends RecyclerView.Adapter<FavItemAdapter.FavItemViewHolder> {

        private List<FavoritesManager.FavItem> favItems;

        @NonNull
        @Override
        public FavItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.favorite_item, parent, false);
            return new FavItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FavItemViewHolder viewHolder, int position) {
            FavoritesManager.FavItem item = favItems.get(position);

            viewHolder.nameTextView.setText(item.getDisplayName());
            viewHolder.pathTextView.setText(item.file.getAbsolutePath());

            viewHolder.favIcon.setImageResource(R.drawable.round_star_24);
            viewHolder.favIcon.setOnClickListener(v -> {
                favoritesManager.removeFavorite(item.file);
                invalidate();
            });

            viewHolder.itemView.setOnClickListener(v -> {
                // Navigate to this directory
                if (item.file.exists() && item.file.isDirectory()) {
                    MainActivity activity = (MainActivity) requireActivity();
                    activity.navigateToDirectory(item.file);
                }
            });
        }

        @Override
        public int getItemCount() {
            return favItems == null ? 0 : favItems.size();
        }

        private void invalidate() {
            favItems = favoritesManager.getFavorites();
            notifyDataSetChanged();

            if (favItems.isEmpty()) {
                favoritesLayout.setVisibility(View.GONE);
            } else {
                favoritesLayout.setVisibility(View.VISIBLE);
            }

            favoritesTitle.setText(getString(R.string.home_favorites_title, favItems.size()));
        }

        class FavItemViewHolder extends RecyclerView.ViewHolder {

            TextView nameTextView;
            TextView pathTextView;
            ImageButton favIcon;

            FavItemViewHolder(View itemView) {
                super(itemView);
                nameTextView = itemView.findViewById(R.id.favorite_name);
                pathTextView = itemView.findViewById(R.id.favorite_path);
                favIcon = itemView.findViewById(R.id.fav_star);
            }
        }
    }
}
