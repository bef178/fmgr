package pd.droidapp.fmgr.view;

import android.view.View;
import android.widget.ImageView;

import java.util.function.IntConsumer;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.animateCollapsed;

public class LocationsBar {

    private final ImageView triangleImageView;
    private final View itemsView;

    private IntConsumer onLocationClicked;

    private boolean collapsed;

    public LocationsBar(View selfView) {
        triangleImageView = selfView.findViewById(R.id.locations_triangle);
        itemsView = selfView.findViewById(R.id.locations_list);

        triangleImageView.setOnClickListener(v -> toggleCollapsed());

        selfView.findViewById(R.id.location_local_files).setOnClickListener(v -> {
            if (onLocationClicked != null) {
                onLocationClicked.accept(v.getId());
            }
        });
    }

    public void whenLocationClicked(IntConsumer onLocationClicked) {
        this.onLocationClicked = onLocationClicked;
    }

    private void toggleCollapsed() {
        collapsed = !collapsed;
        animateCollapsed(triangleImageView, itemsView, collapsed);
    }
}
