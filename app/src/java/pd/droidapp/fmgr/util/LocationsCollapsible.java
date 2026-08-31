package pd.droidapp.fmgr.util;

import android.view.View;
import android.widget.ImageView;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.animateCollapsed;

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
        animateCollapsed(locationsTriangle, locationsItemsView, isLocationsItemsViewCollapsed);
    }
}
