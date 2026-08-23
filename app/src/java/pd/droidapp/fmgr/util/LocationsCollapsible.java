package pd.droidapp.fmgr.util;

import android.animation.ValueAnimator;
import android.view.View;
import android.widget.ImageView;

import pd.droidapp.fmgr.R;

public class LocationsCollapsible {

    private final ImageView locationsTriangle;
    private final View locationsItemsView;

    private Runnable onLocationClickedListener;

    private boolean isLocationsItemsViewCollapsed = false;

    public LocationsCollapsible(View selfView) {
        locationsTriangle = selfView.findViewById(R.id.locations_triangle);
        locationsItemsView = selfView.findViewById(R.id.locations_list);

        locationsTriangle.setOnClickListener(v -> toggleLocationsItemsView());

        selfView.findViewById(R.id.locations_local_files).setOnClickListener(v -> {
            if (onLocationClickedListener != null) {
                onLocationClickedListener.run();
            }
        });
    }

    public void whenLocationClicked(Runnable onLocationClickedListener) {
        this.onLocationClickedListener = onLocationClickedListener;
    }

    private void toggleLocationsItemsView() {
        isLocationsItemsViewCollapsed = !isLocationsItemsViewCollapsed;
        locationsItemsView.setVisibility(isLocationsItemsViewCollapsed ? View.GONE : View.VISIBLE);

        // rotate the triangle
        float targetRotation = isLocationsItemsViewCollapsed ? -90f : 0f;
        float currentRotation = locationsTriangle.getRotation();
        ValueAnimator animator = ValueAnimator.ofFloat(currentRotation, targetRotation);
        animator.setDuration(200);
        animator.addUpdateListener(animation -> {
            float rotation = (float) animation.getAnimatedValue();
            locationsTriangle.setRotation(rotation);
        });
        animator.start();
    }
}
