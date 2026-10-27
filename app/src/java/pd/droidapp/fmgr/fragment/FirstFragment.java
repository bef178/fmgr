package pd.droidapp.fmgr.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;

import pd.droidapp.fmgr.FmgrActivity;
import pd.droidapp.fmgr.R;

public class FirstFragment extends Fragment {

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        FmgrActivity fmgrActivity = (FmgrActivity) requireActivity();
        fmgrActivity.setFirstFragment(this);
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
        view.post(this::preloadBrowseFragment);
    }

    private void preloadBrowseFragment() {
        if (findBrowseFragment() != null) {
            return;
        }
        BrowseFragment browseFragment = new BrowseFragment();
        getChildFragmentManager().beginTransaction()
                .add(R.id.browse_container, browseFragment)
                .hide(browseFragment)
                .setMaxLifecycle(browseFragment, Lifecycle.State.STARTED)
                .commitNow();
        browseFragment.navigateToDirectory(null);
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
        transaction.setCustomAnimations(0, R.anim.slide_out_left);
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

        Animation slideIn = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_in_right);
        slideIn.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                findBrowseFragment().navigateToDirectory(path);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        browseFragment.getView().startAnimation(slideIn);
    }

    public boolean handleSystemBack() {
        BrowseFragment browseFragment = findBrowseFragment();
        if (browseFragment == null || browseFragment.isHidden()) {
            return false;
        }
        if (!browseFragment.navigateUp()) {
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
