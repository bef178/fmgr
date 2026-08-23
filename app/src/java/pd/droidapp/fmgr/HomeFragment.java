package pd.droidapp.fmgr;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import pd.droidapp.fmgr.util.FavoritesCollapsible;
import pd.droidapp.fmgr.util.LocationsCollapsible;

public class HomeFragment extends Fragment {

    private FavoritesCollapsible favoritesCollapsible;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.home_fragment, container, false);

        favoritesCollapsible = new FavoritesCollapsible(view.findViewById(R.id.favorites_collapsible));
        favoritesCollapsible.whenFavDirectoryClicked(file -> {
            MainActivity mainActivity = (MainActivity) requireActivity();
            mainActivity.navigateToDirectory(file);
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
        favoritesCollapsible.invalidate();
    }
}
