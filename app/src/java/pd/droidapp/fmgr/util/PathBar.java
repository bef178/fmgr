package pd.droidapp.fmgr.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.util.Consumer;

import java.io.File;
import java.util.Objects;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.getDisplayPath;

public class PathBar {

    private final FavStore favStore;
    private File directory;
    private Consumer<File> onBreadcrumbClickedListener;

    private final View selfView;
    private final LinearLayout breadcrumbsContainerView;
    private final ImageButton favIcon;

    public PathBar(View selfView) {
        this.selfView = selfView;
        favStore = new FavStore(selfView.getContext());

        breadcrumbsContainerView = selfView.findViewById(R.id.breadcrumb_container);

        favIcon = selfView.findViewById(R.id.fav_icon);
        favIcon.setOnClickListener(v -> toggleFavorite());
    }

    public void invalidate(File directory) {
        if (!Objects.equals(directory, this.directory)) {
            this.directory = directory;
        }
        invalidate();
    }

    public void invalidate() {
        selfView.setVisibility(directory != null ? View.VISIBLE : View.GONE);
        breadcrumbsContainerView.removeAllViews();

        favIcon.setSelected(directory != null && favStore.contains(directory));

        Context context = breadcrumbsContainerView.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        File f = directory;
        while (f != null) {
            if (getDisplayPath(f).equals("/") || f.getName().isEmpty()) {
                // the root deserves a breadcrumb
                breadcrumbsContainerView.addView(createBreadcrumbView(inflater, f, "/"), 0);
                break;
            } else {
                if (breadcrumbsContainerView.getChildCount() > 0) {
                    breadcrumbsContainerView.addView(createSeparatorTextView(context), 0);
                }
                breadcrumbsContainerView.addView(createBreadcrumbView(inflater, f, f.getName()), 0);
            }
            f = f.getParentFile();
        }
    }

    public File getCurrentDirectory() {
        return directory;
    }

    public void whenBreadcrumbClicked(Consumer<File> onBreadcrumbClickedListener) {
        this.onBreadcrumbClickedListener = onBreadcrumbClickedListener;
    }

    private void toggleFavorite() {
        if (directory == null) {
            return;
        }
        Context context = favIcon.getContext();
        if (favStore.contains(directory)) {
            favStore.remove(directory);
            Toast.makeText(context, R.string.removed_from_favorites, Toast.LENGTH_SHORT).show();
        } else {
            favStore.put(directory);
            Toast.makeText(context, R.string.added_to_favorites, Toast.LENGTH_SHORT).show();
        }
        favIcon.setSelected(favStore.contains(directory));
    }

    private TextView createBreadcrumbView(LayoutInflater inflater, File f, String displayName) {
        TextView textView = (TextView) inflater.inflate(R.layout.breadcrumb, breadcrumbsContainerView, false);
        textView.setText(displayName);
        textView.setOnClickListener(v -> {
            if (onBreadcrumbClickedListener != null) {
                onBreadcrumbClickedListener.accept(f);
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
