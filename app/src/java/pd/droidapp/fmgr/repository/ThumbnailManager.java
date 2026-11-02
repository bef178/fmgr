package pd.droidapp.fmgr.repository;

import android.graphics.Bitmap;

import java.util.function.Consumer;

import pd.droidapp.fmgr.util.ImageCache;
import pd.droidapp.fmgr.util.ImageLoader;
import pd.util.FileOps;

import static pd.droidapp.fmgr.util.ImageLoader.FitMode.CENTER_CROP;

public class ThumbnailManager {

    private static final int MAX_CACHE_BYTES = 16 * 1024 * 1024;

    public static final ThumbnailManager singleton = new ThumbnailManager();

    private final ImageCache cache = new ImageCache(MAX_CACHE_BYTES);
    private final ImageLoader imageLoader = new ImageLoader();

    public void load(String path, String mimeType, int targetWidth, int targetHeight, Consumer<Bitmap> onLoaded) {
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

            Bitmap cached = cache.get(path, mtime);
            if (cached != null) {
                // cache hit
                onLoaded.accept(cached);
                return;
            }

            Bitmap bitmap = null;
            try {
                bitmap = imageLoader.decodeToBitmap(path, mimeType, targetWidth, targetHeight, CENTER_CROP);
            } catch (Throwable ignored) {
            }
            if (bitmap != null) {
                cache.put(path, mtime, bitmap);
            } else {
                cache.remove(path);
            }
            onLoaded.accept(bitmap);
        });
    }
}
