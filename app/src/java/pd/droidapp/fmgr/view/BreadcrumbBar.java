package pd.droidapp.fmgr.view;

import android.content.Context;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.util.Consumer;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import pd.droidapp.fmgr.R;
import pd.util.PathOps;

public class BreadcrumbBar {

    private static final String CAPPING_DIRECTORY = Environment.getExternalStorageDirectory().getPath();

    private Consumer<String> onBreadcrumbClicked;
    private Runnable onFavIconClicked;

    private final LinearLayout selfView;
    private final LinearLayout breadcrumbsView;
    private final ImageButton favIcon;

    private State state;

    public BreadcrumbBar(LinearLayout selfView) {
        this.selfView = selfView;
        breadcrumbsView = selfView.findViewById(R.id.breadcrumb_container);
        favIcon = selfView.findViewById(R.id.fav_icon);

        selfView.setVisibility(View.GONE);
        favIcon.setSelected(false);
        favIcon.setOnClickListener(v -> {
            if (onFavIconClicked != null) {
                onFavIconClicked.run();
            }
        });
    }

    public void whenBreadcrumbClicked(Consumer<String> onBreadcrumbClicked) {
        this.onBreadcrumbClicked = onBreadcrumbClicked;
    }

    public void whenFavIconClicked(Runnable onFavIconClicked) {
        this.onFavIconClicked = onFavIconClicked;
    }

    /**
     * null state for no change
     */
    public void render(State state) {
        if (state == null) {
            return;
        } else if (state.path == null || state.path.isEmpty()) {
            this.state = state;
            selfView.setVisibility(View.GONE);
            favIcon.setSelected(state.favorite);
            return;
        }

        State oldState = this.state;
        this.state = state;

        if (!Objects.equals(oldState == null ? null : oldState.path, state.path)) {
            List<String> crumbPaths = new LinkedList<>();
            String path = state.path;
            while (true) {
                crumbPaths.add(0, path);
                if (CAPPING_DIRECTORY.equals(path)) {
                    break;
                }
                String parent = PathOps.singleton.dirname(path);
                if (parent.equals(path)) {
                    break;
                }
                path = parent;
            }

            breadcrumbsView.removeAllViews();
            Context context = breadcrumbsView.getContext();
            LayoutInflater inflater = LayoutInflater.from(context);
            for (int i = 0; i < crumbPaths.size(); i++) {
                if (i > 0 && !CAPPING_DIRECTORY.equals(crumbPaths.get(i - 1))) {
                    TextView textView = createCrumbSeparatorView(context);
                    breadcrumbsView.addView(textView);
                }
                TextView textView = createCrumbSegmentView(inflater, crumbPaths.get(i));
                breadcrumbsView.addView(textView);
            }
        }

        if ((oldState != null && oldState.favorite) != state.favorite) {
            favIcon.setSelected(state.favorite);
        }

        selfView.setVisibility(View.VISIBLE);
    }

    private TextView createCrumbSegmentView(LayoutInflater inflater, String path) {
        TextView textView = (TextView) inflater.inflate(R.layout.breadcrumb_item, breadcrumbsView, false);
        textView.setText(CAPPING_DIRECTORY.equals(path) ? "/" : PathOps.singleton.basename(path));
        textView.setOnClickListener(v -> {
            if (onBreadcrumbClicked != null) {
                onBreadcrumbClicked.accept(path);
            }
        });
        return textView;
    }

    private TextView createCrumbSeparatorView(Context context) {
        TextView textView = new TextView(context);
        textView.setText("/");
        textView.setTextSize(12);
        textView.setTextColor(context.getColor(android.R.color.darker_gray));
        return textView;
    }

    public static class State {

        public final String path;
        public final boolean favorite;

        public State(String path, boolean favorite) {
            this.path = path;
            this.favorite = favorite;
        }
    }
}
