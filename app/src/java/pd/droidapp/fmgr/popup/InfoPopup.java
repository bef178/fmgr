package pd.droidapp.fmgr.popup;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.fileheader.FileMimeUtil;
import pd.droidapp.fmgr.repository.ThumbnailManager;
import pd.droidapp.fmgr.repository.WorkerPool;
import pd.droidapp.fmgr.util.ImageLoader;
import pd.droidapp.fmgr.view.ButtonScrollView;
import pd.droidapp.fmgr.view.ButtonState;
import pd.droidapp.fmgr.view.PopupBottomBar;
import pd.droidapp.fmgr.view.PopupTitleBar;
import pd.util.FileOps;
import pd.util.FileStat;

import static pd.droidapp.fmgr.util.FileIconUtil.getDrawableId;
import static pd.droidapp.fmgr.util.Util.getDisplayPath;
import static pd.droidapp.fmgr.util.Util.scrollToEndAndBack;
import static pd.droidapp.fmgr.util.Util.themeColor;

public class InfoPopup extends ProcessingPopup {

    private static final DateTimeFormatter INFO_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);

    private final String path;

    // views
    private final ImageView iconImageView;
    private final TextView pathTextView;
    private final ButtonScrollView pathScrollView;
    private final LinearLayout rowsView;
    private final ImageView progressImageView;
    private final CircularProgressDrawable progressDrawable;

    public InfoPopup(View containerView, String path) {
        super(containerView);
        this.path = path;

        iconImageView = contentView.findViewById(R.id.info_popup_icon);
        pathTextView = contentView.findViewById(R.id.info_popup_path);
        pathScrollView = contentView.findViewById(R.id.info_popup_path_scroll);
        rowsView = contentView.findViewById(R.id.info_popup_rows);
        progressImageView = contentView.findViewById(R.id.info_popup_progress);

        progressDrawable = new CircularProgressDrawable(context);
        progressDrawable.setStyle(CircularProgressDrawable.DEFAULT);
        progressDrawable.setColorSchemeColors(
                themeColor(context, com.google.android.material.R.attr.colorPrimary));
        progressImageView.setImageDrawable(progressDrawable);

        bottomBar.whenButtonClicked(id -> {
            if (id == R.drawable.ic_checkmark_24) {
                selfDialog.dismiss();
            }
        });
    }

    @Override
    protected void inflateContentView() {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) contentView.getLayoutParams();
        params.width = context.getResources().getDimensionPixelSize(R.dimen.processing_popup_width);
        params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
        contentView.setLayoutParams(params);
        LayoutInflater.from(context).inflate(R.layout.info_popup_content, contentView, true);
    }

    @Override
    protected void setupDialogWindow(@NonNull Window window) {
        window.setGravity(Gravity.CENTER);
    }

    @Override
    protected void onDismissing(Runnable continueDismiss) {
        continueDismiss.run();
    }

    @Override
    protected void onDismissed() {
        progressDrawable.stop();
    }

    @Override
    protected void onShow() {
        titleBar.render(new PopupTitleBar.State(context.getString(R.string.info)));
        bottomBar.render(new PopupBottomBar.State(
                new ButtonState(R.drawable.ic_checkmark_24)));

        progressDrawable.start();

        WorkerPool.post(() -> {
            FileStat stat = FileOps.singleton.stat(path);
            boolean isDirectory = stat.isDirectory(true);
            Long size = isDirectory ? null : stat.size;
            Long numChildren = isDirectory ? FileOps.singleton.getNumDirectoryEntries(path) : null;
            Long mtime = stat.mtime;
            String mimeType = isDirectory ? null : FileMimeUtil.mimeByContent(path);
            ImageLoader imageLoader = new ImageLoader();
            int[] resolution = null;
            if (!isDirectory && mimeType != null && imageLoader.supports(mimeType)) {
                resolution = imageLoader.readImageSourceResolution(path, mimeType);
            }
            int[] resolvedResolution = resolution;
            selfView.post(() -> {
                if (!selfDialog.isShowing()) {
                    return;
                }
                iconImageView.setImageResource(getDrawableId(context, path, isDirectory, mimeType));
                int sizePx = Math.max(1, iconImageView.getLayoutParams().width);
                ThumbnailManager.singleton.load(path, mimeType, sizePx, sizePx, bitmap ->
                        iconImageView.post(() -> {
                            if (!selfDialog.isShowing() || bitmap == null) {
                                return;
                            }
                            iconImageView.setImageBitmap(bitmap);
                        }));
                pathTextView.setText(getDisplayPath(path));
                String type = isDirectory
                        ? context.getString(R.string.mime_directory)
                        : mimeType;
                renderRows(type, size, numChildren, resolvedResolution, mtime);
            });
        });
    }

    private void renderRows(String type, Long size, Long numChildren, int[] resolution, Long mtime) {
        Map<Integer, String> rows = new LinkedHashMap<>();
        if (type != null) {
            rows.put(R.string.info_type, type);
        }
        if (size != null) {
            rows.put(R.string.info_size, String.format(Locale.ROOT, "%,d", size));
        } else if (numChildren != null) {
            rows.put(R.string.info_size, context.getString(R.string.x_items, numChildren));
        }
        if (resolution != null) {
            rows.put(R.string.info_resolution, resolution[0] + " x " + resolution[1]);
        }
        if (mtime != null) {
            rows.put(R.string.info_modified, INFO_DATE_FORMAT.format(Instant.ofEpochMilli(mtime)));
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        for (Map.Entry<Integer, String> row : rows.entrySet()) {
            View rowView = inflater.inflate(R.layout.info_popup_item, rowsView, false);
            ((TextView) rowView.findViewById(R.id.info_popup_key)).setText(row.getKey());
            ((TextView) rowView.findViewById(R.id.info_popup_value)).setText(row.getValue());
            rowsView.addView(rowView);
        }
        progressDrawable.stop();
        rowsView.setVisibility(View.VISIBLE);
        progressImageView.setVisibility(View.GONE);
        scrollToEndAndBack(pathScrollView);
    }
}
