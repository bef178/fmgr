package pd.droidapp.glry;

import android.graphics.Bitmap;

import java.util.function.Consumer;

import pd.droidapp.fmgr.repository.WorkerPool;
import pd.droidapp.fmgr.util.ImageCache;
import pd.droidapp.fmgr.util.ImageLoader;
import pd.util.FileOps;

import static pd.droidapp.fmgr.util.ImageLoader.DrawableEntry;
import static pd.droidapp.fmgr.util.ImageLoader.FitMode.FIT_CENTER;

public class ImageManager {

    private static final int MAX_CACHE_BYTES = 32 * 1024 * 1024;
    private static final float DOWNSAMPLE_THRESHOLD = 1.2f;

    public static final ImageManager singleton = new ImageManager();

    private final ImageCache cache = new ImageCache(MAX_CACHE_BYTES);
    private final ImageLoader imageLoader = new ImageLoader();

    public boolean supports(String mime) {
        return imageLoader.supports(mime);
    }

    public void clearCache() {
        cache.clear();
    }

    public int[] readImageResolution(String path, String mimeType) {
        return imageLoader.readImageSourceResolution(path, mimeType);
    }

    public void load(String path, String mimeType, int clientWidth, int clientHeight, Consumer<DrawableEntry> onLoaded) {
        WorkerPool.post(() -> {
            if (!supports(mimeType)) {
                onLoaded.accept(null);
                return;
            }
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
                onLoaded.accept(new DrawableEntry(cached.sourceWidth, cached.sourceHeight, cached.bitmap));
                return;
            }

            int[] targetResolution = calculateTargetResolution(path, mimeType, clientWidth, clientHeight);
            DrawableEntry drawableEntry = targetResolution != null
                    ? imageLoader.decodeToDrawable(path, mimeType, targetResolution[0], targetResolution[1], FIT_CENTER)
                    : null;

            Bitmap bitmap = drawableEntry != null
                    ? drawableEntry.getBitmap()
                    : null;
            if (bitmap != null) {
                cache.put(key, new ImageCache.Entry(mtime, drawableEntry.sourceWidth, drawableEntry.sourceHeight, bitmap));
            } else {
                cache.remove(key);
            }

            onLoaded.accept(drawableEntry);
        });
    }

    private int[] calculateTargetResolution(String path, String mimeType, int clientWidth, int clientHeight) {
        int[] resolution = imageLoader.readImageSourceResolution(path, mimeType);
        if (resolution == null) {
            return null;
        }
        if (!"image/svg+xml".equals(mimeType)
                && resolution[0] <= clientWidth * DOWNSAMPLE_THRESHOLD
                && resolution[1] <= clientHeight * DOWNSAMPLE_THRESHOLD) {
            return resolution;
        }
        float scale = Math.min((float) clientWidth / resolution[0], (float) clientHeight / resolution[1]);
        return new int[] {
                Math.max(1, Math.round(resolution[0] * scale)),
                Math.max(1, Math.round(resolution[1] * scale))};
    }

    public void loadActualSize(String path, String mimeType, Consumer<DrawableEntry> onLoaded) {
        WorkerPool.post(() -> {
            if (!supports(mimeType)) {
                onLoaded.accept(null);
                return;
            }
            int[] resolution = imageLoader.readImageSourceResolution(path, mimeType);
            if (resolution == null) {
                onLoaded.accept(null);
                return;
            }
            DrawableEntry drawableEntry = imageLoader.decodeToDrawable(
                    path, mimeType, resolution[0], resolution[1], FIT_CENTER);
            onLoaded.accept(drawableEntry);
        });
    }
}
