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

import pd.droidapp.fmgr.MainActivity;
import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.popup.EditPopup;
import pd.droidapp.fmgr.util.FavoritesStore;
import pd.droidapp.fmgr.view.FavoritesBar;
import pd.util.FileOps;

import static pd.droidapp.fmgr.util.FavoritesStore.FavItem;
import static pd.droidapp.fmgr.view.FavoritesBar.FavItemState;

public class HomeFragment extends Fragment {

    private FavoritesBar favoritesBar;
    private FavoritesStore favoritesStore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.home_fragment, container, false);

        favoritesStore = new FavoritesStore(requireContext());

        favoritesBar = new FavoritesBar(view.findViewById(R.id.favorites_bar));
        favoritesBar.whenFavoriteClicked(path -> {
            if (FileOps.singleton.stat(path).isDirectory(true)) {
                MainActivity mainActivity = (MainActivity) requireActivity();
                mainActivity.navigateToDirectory(path);
            } else {
                Toast.makeText(requireContext(), R.string.error_directory_not_accessible, Toast.LENGTH_SHORT).show();
            }
        });
        favoritesBar.whenFavoriteRemoved(path -> {
            favoritesStore.remove(path);
            renderFavoritesBar();
        });
        favoritesBar.whenFavoriteEditClicked(path -> {
            FavItem favItem = Objects.requireNonNull(favoritesStore.get(path), "favItem");
            EditPopup editPopup = new EditPopup(getView(),
                    getString(R.string.edit_favorite_name),
                    favItem.name,
                    FavoritesStore.getDefaultName(favItem.path),
                    newName -> {
                        newName = newName.trim();
                        if (!newName.equals(favItem.name)) {
                            favoritesStore.put(favItem.path, newName);
                            renderFavoritesBar();
                        }
                        return true;
                    });
            editPopup.show();
        });

        LocationsCollapsible locationsCollapsible = new LocationsCollapsible(view.findViewById(R.id.locations_collapsible));
        locationsCollapsible.whenLocationClicked(() -> {
            MainActivity mainActivity = (MainActivity) requireActivity();
            mainActivity.navigateToBrowse();
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        renderFavoritesBar();
    }

    private void renderFavoritesBar() {
        favoritesBar.render(new FavoritesBar.State(favoritesStore.getAll().stream()
                .map(favItem -> new FavItemState(favItem.path, favItem.name))
                .toArray(FavItemState[]::new)));
    }
}
