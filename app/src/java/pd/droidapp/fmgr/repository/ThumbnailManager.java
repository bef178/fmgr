package pd.droidapp.fmgr.repository;

import android.graphics.Bitmap;

import java.util.function.Consumer;

import pd.droidapp.fmgr.util.ImageCache;
import pd.droidapp.fmgr.util.ImageLoader;
import pd.util.FileOps;

import static pd.droidapp.fmgr.util.ImageLoader.DrawableEntry;
import static pd.droidapp.fmgr.util.ImageLoader.FitMode.CENTER_CROP;

public class ThumbnailManager {

    private static final int MAX_CACHE_BYTES = 16 * 1024 * 1024;

    public static final ThumbnailManager singleton = new ThumbnailManager();

    private final ImageCache cache = new ImageCache(MAX_CACHE_BYTES);
    private final ImageLoader imageLoader = new ImageLoader();

    public void load(String path, String mimeType, int clientWidth, int clientHeight, Consumer<Bitmap> onLoaded) {
        if (!imageLoader.supports(mimeType)) {
            return;
        }
        WorkerPool.post(() -> {
            Long mtime = null;
            try {
                mtime = FileOps.singleton.stat(path).mtime;
            } catch (Throwable ignored) {
            }
            if (mtime == null) {
                onLoaded.accept(null);
                return;
            }

            String key = cache.key(path, clientWidth, clientHeight);
            ImageCache.Entry cached = cache.get(key);
            if (cached != null && cached.mtime == mtime) {
                // cache hit
                onLoaded.accept(cached.bitmap);
                return;
            }

            DrawableEntry drawableEntry = imageLoader.decodeToBitmapDrawable(path, mimeType, clientWidth, clientHeight, CENTER_CROP);
            Bitmap bitmap = drawableEntry != null
                    ? drawableEntry.getBitmap()
                    : null;
            if (bitmap != null) {
                cache.put(key, new ImageCache.Entry(mtime, bitmap));
            } else {
                cache.remove(key);
            }
            onLoaded.accept(bitmap);
        });
    }
}
