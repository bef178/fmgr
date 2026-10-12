package pd.droidapp.fmgr.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Objects;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.popup.EditPopup;
import pd.droidapp.fmgr.util.FavoritesStore;
import pd.droidapp.fmgr.view.FavoriteBar;
import pd.droidapp.fmgr.view.LocationBar;
import pd.util.FileOps;

import static pd.droidapp.fmgr.util.FavoritesStore.FavItem;

public class HomeFragment extends Fragment {

    private FavoriteBar favoriteBar;
    private FavoritesStore favoritesStore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.home_fragment, container, false);

        view.findViewById(R.id.home_header_icon).setSelected(true);

        favoritesStore = new FavoritesStore(requireContext());

        favoriteBar = new FavoriteBar(view.findViewById(R.id.favorites_bar));
        favoriteBar.whenFavoriteItemClicked(path -> {
            if (FileOps.singleton.stat(path).isDirectory(true)) {
                ((FirstFragment) requireParentFragment()).showBrowseFragment(path);
            } else {
                Toast.makeText(requireContext(), R.string.error_directory_not_accessible, Toast.LENGTH_SHORT).show();
            }
        });
        favoriteBar.whenFavoriteItemStarClicked(path -> {
            favoritesStore.remove(path);
            renderFavoritesBar();
        });
        favoriteBar.whenFavoriteItemEditClicked(path -> {
            FavItem favItem = Objects.requireNonNull(favoritesStore.get(path), "favItem");
            EditPopup editPopup = new EditPopup(getView(),
                    R.string.edit_favorite_name,
                    R.string.rename,
                    favItem.name,
                    FavoritesStore.getDefaultName(favItem.path)) {
                @Override
                protected boolean onStart(String newName) {
                    newName = newName.trim();
                    if (!newName.equals(favItem.name)) {
                        favoritesStore.put(favItem.path, newName);
                        renderFavoritesBar();
                    }
                    return true;
                }
            };
            editPopup.show();
        });

        new LocationBar(view.findViewById(R.id.locations_bar))
                .whenLocationItemClicked(id -> ((FirstFragment) requireParentFragment()).showBrowseFragment(null));

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        renderFavoritesBar();
    }

    private void renderFavoritesBar() {
        favoriteBar.render(new FavoriteBar.State(favoritesStore.getAll().stream()
                .map(favItem -> new FavoriteBar.ItemState(favItem.path, favItem.name))
                .toArray(FavoriteBar.ItemState[]::new)));
    }
}
