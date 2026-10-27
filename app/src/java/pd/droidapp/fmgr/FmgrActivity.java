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

import com.google.android.material.bottomnavigation.BottomNavigationView;
import pd.droidapp.fmgr.fragment.FirstFragment;
import pd.droidapp.fmgr.fragment.ProfileFragment;

public class FmgrActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNavigation;

    private FirstFragment firstFragment;
    private long lastBackPressedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // keep the app light regardless of system dark mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fmgr_activity);

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
                        return new ProfileFragment();
                    default:
                        return new FirstFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        });

        viewPager.setOffscreenPageLimit(1);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                switch (position) {
                    case 1:
                        bottomNavigation.setSelectedItemId(R.id.navigation_profile);
                        break;
                    default:
                        bottomNavigation.setSelectedItemId(R.id.navigation_home);
                        break;
                }
            }
        });

        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_home) {
                viewPager.setCurrentItem(0);
                return true;
            } else if (itemId == R.id.navigation_profile) {
                viewPager.setCurrentItem(1);
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

        if (current == 0 && firstFragment != null && firstFragment.handleSystemBack()) {
            return;
        }

        // fall back to home fragment
        if (current != 0) {
            viewPager.setCurrentItem(0);
            return;
        }

        // press again to exit
        long now = System.currentTimeMillis();
        if (now - lastBackPressedAt < 2000) {
            finish();
            return;
        }
        lastBackPressedAt = now;
        Toast.makeText(this, R.string.press_back_again_to_exit, Toast.LENGTH_SHORT).show();
    }

    public void setFirstFragment(FirstFragment fragment) {
        this.firstFragment = fragment;
    }
}
