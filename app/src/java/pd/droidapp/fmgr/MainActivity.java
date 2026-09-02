package pd.droidapp.fmgr;

import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import java.io.File;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNavigation;

    private BrowseFragment browseFragment;
    private int indexWithinHome;
    private long lastBackPressedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // keep the app light regardless of system dark mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        viewPager = findViewById(R.id.view_pager);
        bottomNavigation = findViewById(R.id.bottom_navigation);

        // disable viewpager swiping
        viewPager.setUserInputEnabled(false);

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 1:
                        return new BrowseFragment();
                    case 2:
                        return new ProfileFragment();
                    default:
                        return new HomeFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });

        // create BrowseFragment before it is ever navigated to
        viewPager.setOffscreenPageLimit(1);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                switch (position) {
                    case 1:
                        indexWithinHome = 1;
                        bottomNavigation.setSelectedItemId(R.id.navigation_home);
                        break;
                    case 2:
                        bottomNavigation.setSelectedItemId(R.id.navigation_profile);
                        break;
                    default:
                        indexWithinHome = 0;
                        bottomNavigation.setSelectedItemId(R.id.navigation_home);
                        break;
                }
            }
        });

        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_home) {
                if (viewPager.getCurrentItem() == 2) {
                    viewPager.setCurrentItem(indexWithinHome);
                }
                return true;
            } else if (itemId == R.id.navigation_profile) {
                viewPager.setCurrentItem(2);
                return true;
            }
            return false;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onSystemBackPressed();
            }
        });
    }

    private void onSystemBackPressed() {
        int current = viewPager.getCurrentItem();

        // on browse fragment: try nav back
        if (current == 1 && browseFragment != null && browseFragment.navigateBack()) {
            return;
        }

        // fall back to home fragment
        if (current != 0) {
            viewPager.setCurrentItem(0);
            return;
        }

        // already on home fragment: press again to exit
        long now = System.currentTimeMillis();
        if (now - lastBackPressedAt < 2000) {
            finish();
            return;
        }
        lastBackPressedAt = now;
        Toast.makeText(this, R.string.press_back_again_to_exit, Toast.LENGTH_SHORT).show();
    }

    public void navigateToHome() {
        viewPager.setCurrentItem(0);
    }

    public void navigateToBrowse() {
        viewPager.setCurrentItem(1);
    }

    public void navigateToDirectory(File directory) {
        if (browseFragment != null && browseFragment.getView() != null) {
            browseFragment.navigateToDirectory(directory);
        }
        viewPager.setCurrentItem(1, true);
    }

    void setBrowseFragment(BrowseFragment fragment) {
        this.browseFragment = fragment;
    }
}
