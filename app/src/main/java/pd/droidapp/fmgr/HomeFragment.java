package pd.droidapp.fmgr;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import pd.droidapp.fmgr.fav.FavBox;
import pd.droidapp.fmgr.fav.FavItemStore;

public class HomeFragment extends Fragment {

    private FavBox favBox;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.home_fragment, container, false);

        favBox = new FavBox(requireContext(), view);
        favBox.whenClickedFavItem(file -> {
            if (file.exists() && file.isDirectory()) {
                MainActivity mainActivity = (MainActivity) requireActivity();
                mainActivity.navigateToDirectory(file);
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        favBox.invalidate();
    }
}
