package pd.droidapp.fmgr.view;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.function.IntConsumer;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.animateCollapsed;

public class LocationBar {

    private final ImageView triangleImageView;
    private final ViewGroup itemContainerView;

    private IntConsumer onLocationItemClicked;

    private boolean collapsed;

    public LocationBar(View selfView) {
        triangleImageView = selfView.findViewById(R.id.locations_triangle);
        itemContainerView = selfView.findViewById(R.id.locations_list);

        selfView.findViewById(R.id.locations_title_bar).setOnClickListener(v -> {
            collapsed = !collapsed;
            animateCollapsed(triangleImageView, itemContainerView, collapsed);
        });

        selfView.findViewById(R.id.location_local_files).setOnClickListener(v -> {
            if (onLocationItemClicked != null) {
                onLocationItemClicked.accept(v.getId());
            }
        });
    }

    public void whenLocationItemClicked(IntConsumer onLocationItemClicked) {
        this.onLocationItemClicked = onLocationItemClicked;
    }
}
