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
import pd.droidapp.fmgr.fav.FavItemStore;

public class PathBar {

    private final Context context;
    private final FavItemStore favItemStore;
    private File directory;
    private Consumer<File> onBreadcrumbClickedListener;

    private final LinearLayout breadcrumbLayout;
    private final ImageButton favIcon;

    public PathBar(Context context, View containerView) {
        this.context = context;
        favItemStore = new FavItemStore(context);

        breadcrumbLayout = containerView.findViewById(R.id.breadcrumb_layout);

        favIcon = containerView.findViewById(R.id.fav_icon);
        favIcon.setOnClickListener(v -> toggleFavorite());
    }

    public void invalidate(File directory) {
        if (!Objects.equals(directory, this.directory)) {
            this.directory = directory;
        }
        invalidate();
    }

    public void invalidate() {
        breadcrumbLayout.removeAllViews();

        favIcon.setSelected(favItemStore.contains(directory));

        LayoutInflater inflater = LayoutInflater.from(context);
        File f = directory;
        while (f != null) {
            if (f.getName().isEmpty()) {
                // the root directory deserves a breadcrumb
                breadcrumbLayout.addView(createBreadcrumbItemView(inflater, f), 0);
            } else {
                if (breadcrumbLayout.getChildCount() > 0) {
                    breadcrumbLayout.addView(createSeparatorTextView(context), 0);
                }
                breadcrumbLayout.addView(createBreadcrumbItemView(inflater, f), 0);
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
        if (favItemStore.contains(directory)) {
            favItemStore.remove(directory);
            Toast.makeText(context, R.string.removed_from_favorites, Toast.LENGTH_SHORT).show();
        } else {
            favItemStore.put(directory);
            Toast.makeText(context, R.string.added_to_favorites, Toast.LENGTH_SHORT).show();
        }
        favIcon.setSelected(favItemStore.contains(directory));
    }

    private TextView createBreadcrumbItemView(LayoutInflater inflater, File f) {
        TextView textView = (TextView) inflater.inflate(R.layout.breadcrumb_item, breadcrumbLayout, false);
        textView.setText(f.getName().isEmpty() ? "/" : f.getName());
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
