package pd.droidapp.fmgr.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;

import pd.droidapp.fmgr.MainActivity;
import pd.droidapp.fmgr.R;

public class FirstFragment extends Fragment {

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        MainActivity mainActivity = (MainActivity) requireActivity();
        mainActivity.setFirstFragment(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.first_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (findHomeFragment() == null) {
            getChildFragmentManager().beginTransaction()
                    .add(R.id.home_container, new HomeFragment())
                    .commit();
        }
    }

    void showHomeFragment() {
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
        BrowseFragment browseFragment = findBrowseFragment();
        if (browseFragment != null) {
            transaction.hide(browseFragment);
            transaction.setMaxLifecycle(browseFragment, Lifecycle.State.STARTED);
        }
        Fragment homeFragment = findHomeFragment();
        if (homeFragment != null) {
            transaction.show(homeFragment);
            transaction.setMaxLifecycle(homeFragment, Lifecycle.State.RESUMED);
        }
        transaction.commitNow();
    }

    void showBrowseFragment(String path) {
        BrowseFragment browseFragment = findBrowseFragment();
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left);
        if (browseFragment == null) {
            browseFragment = new BrowseFragment();
            transaction.add(R.id.browse_container, browseFragment);
        } else if (browseFragment.isHidden()) {
            transaction.show(browseFragment);
        }
        transaction.setMaxLifecycle(browseFragment, Lifecycle.State.RESUMED);

        Fragment homeFragment = findHomeFragment();
        if (homeFragment != null) {
            transaction.hide(homeFragment);
            transaction.setMaxLifecycle(homeFragment, Lifecycle.State.STARTED);
        }
        transaction.commitNow();
        if (path != null) {
            browseFragment.navigateToDirectory(path);
        }
    }

    public boolean handleSystemBack() {
        BrowseFragment browseFragment = findBrowseFragment();
        if (browseFragment == null || browseFragment.isHidden()) {
            return false;
        }
        if (!browseFragment.navigateBack()) {
            showHomeFragment();
        }
        return true;
    }

    private Fragment findHomeFragment() {
        return getChildFragmentManager().findFragmentById(R.id.home_container);
    }

    private BrowseFragment findBrowseFragment() {
        return (BrowseFragment) getChildFragmentManager().findFragmentById(R.id.browse_container);
    }
}
