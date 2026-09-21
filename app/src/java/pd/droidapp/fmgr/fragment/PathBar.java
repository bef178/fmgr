package pd.droidapp.fmgr.fragment;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.util.Consumer;

import pd.droidapp.fmgr.R;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.getDisplayPath;

public class PathBar {

    private Consumer<String> onBreadcrumbClickedListener;
    private Runnable onFavIconClickedListener;

    private final View selfView;
    private final LinearLayout breadcrumbsContainerView;
    private final ImageButton favIcon;

    public PathBar(View selfView) {
        this.selfView = selfView;

        breadcrumbsContainerView = selfView.findViewById(R.id.breadcrumb_container);

        favIcon = selfView.findViewById(R.id.fav_icon);
        favIcon.setOnClickListener(v -> {
            if (onFavIconClickedListener != null) {
                onFavIconClickedListener.run();
            }
        });
    }

    public void set(String directory, boolean isFavorite) {
        selfView.setVisibility(directory != null ? View.VISIBLE : View.GONE);
        breadcrumbsContainerView.removeAllViews();

        favIcon.setSelected(isFavorite);

        Context context = breadcrumbsContainerView.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        String path = directory;
        while (path != null) {
            if (getDisplayPath(path).equals("/")) {
                // the root deserves a breadcrumb
                breadcrumbsContainerView.addView(createBreadcrumbView(inflater, path, "/"), 0);
                break;
            } else {
                if (breadcrumbsContainerView.getChildCount() > 0) {
                    breadcrumbsContainerView.addView(createSeparatorTextView(context), 0);
                }
                breadcrumbsContainerView.addView(createBreadcrumbView(inflater, path, PathOps.singleton.basename(path)), 0);
            }
            path = PathOps.singleton.dirname(path);
        }
    }

    public void whenBreadcrumbClicked(Consumer<String> onBreadcrumbClickedListener) {
        this.onBreadcrumbClickedListener = onBreadcrumbClickedListener;
    }

    public void whenFavIconClicked(Runnable onFavIconClickedListener) {
        this.onFavIconClickedListener = onFavIconClickedListener;
    }

    private TextView createBreadcrumbView(LayoutInflater inflater, String path, String displayName) {
        TextView textView = (TextView) inflater.inflate(R.layout.breadcrumb, breadcrumbsContainerView, false);
        textView.setText(displayName);
        textView.setOnClickListener(v -> {
            if (onBreadcrumbClickedListener != null) {
                onBreadcrumbClickedListener.accept(path);
            }
        });
        return textView;
    }

    private TextView createSeparatorTextView(Context context) {
        TextView textView = new TextView(context);
        textView.setText("/");
        textView.setTextSize(12);
        textView.setTextColor(context.getColor(android.R.color.darker_gray));
        return textView;
    }
}
