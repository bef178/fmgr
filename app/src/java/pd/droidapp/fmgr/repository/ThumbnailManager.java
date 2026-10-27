package pd.droidapp.fmgr.repository;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.LruCache;

import com.caverock.androidsvg.RenderOptions;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

import pd.util.FileOps;

public class ThumbnailManager {

    private static final int MAX_CACHE_BYTES = 16 * 1024 * 1024;
    private static final float SVG_FALLBACK_SIZE = 512f;

    public static final ThumbnailManager singleton = new ThumbnailManager();

    private final LruCache<String, Entry> cache = new LruCache<>(MAX_CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Entry entry) {
            return entry.bitmap.getAllocationByteCount();
        }
    };

    private boolean supports(String mime) {
        if (mime == null) {
            return false;
        }
        switch (mime) {
            case "image/jpeg":
            case "image/png":
            case "image/webp":
            case "image/gif":
            case "image/bmp":
            case "image/heif":
            case "image/svg+xml":
                return true;
            case "image/avif":
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
            default:
                return false;
        }
    }

    public void load(String path, String mimeType, int sizePx, Consumer<Bitmap> onLoaded) {
        if (!supports(mimeType)) {
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

            Entry cached = cacheGet(path, mtime);
            if (cached != null) {
                // cache hit
                onLoaded.accept(cached.bitmap);
                return;
            }

            Bitmap bitmap = null;
            try {
                bitmap = createThumbnail(path, mimeType, sizePx);
            } catch (Throwable ignored) {
            }
            if (bitmap != null) {
                cachePut(path, mtime, bitmap);
            } else {
                cacheRemove(path);
            }
            onLoaded.accept(bitmap);
        });
    }

    private Entry cacheGet(String path, Long mtime) {
        Entry cached = cache.get(path);
        if (cached != null && cached.mtime == mtime) {
            return cached;
        }
        return null;
    }

    private void cachePut(String path, Long mtime, Bitmap bitmap) {
        cache.put(path, new Entry(mtime, bitmap));
    }

    private void cacheRemove(String path) {
        cache.remove(path);
    }

    private Bitmap createThumbnail(String path, String mimeType, int sizePx) throws IOException, SVGParseException {
        if ("image/svg+xml".equals(mimeType)) {
            return createSvgThumbnail(path, sizePx);
        }
        ImageDecoder.Source source = ImageDecoder.createSource(new File(path));
        return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            int width = info.getSize().getWidth();
            int height = info.getSize().getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            float scale = (float) sizePx / Math.min(width, height);
            int scaledWidth = Math.max(1, Math.round(width * scale));
            int scaledHeight = Math.max(1, Math.round(height * scale));
            decoder.setTargetSize(scaledWidth, scaledHeight);

            int side = Math.min(sizePx, Math.min(scaledWidth, scaledHeight));
            int left = (scaledWidth - side) / 2;
            int top = (scaledHeight - side) / 2;
            decoder.setCrop(new Rect(left, top, left + side, top + side));
        });
    }

    private Bitmap createSvgThumbnail(String path, int sizePx) throws IOException, SVGParseException {
        SVG svg;
        try (InputStream input = new FileInputStream(path)) {
            svg = SVG.getFromInputStream(input);
        }

        float width = svg.getDocumentWidth();
        float height = svg.getDocumentHeight();
        if (width <= 0 || height <= 0) {
            RectF viewBox = svg.getDocumentViewBox();
            if (viewBox != null && viewBox.width() > 0 && viewBox.height() > 0) {
                width = viewBox.width();
                height = viewBox.height();
            } else {
                width = height = SVG_FALLBACK_SIZE;
            }
        }

        float scale = (float) sizePx / Math.min(width, height);
        int scaledWidth = Math.max(1, Math.round(width * scale));
        int scaledHeight = Math.max(1, Math.round(height * scale));

        int side = Math.min(sizePx, Math.min(scaledWidth, scaledHeight));
        int left = (scaledWidth - side) / 2;
        int top = (scaledHeight - side) / 2;

        Bitmap bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(-left, -top);
        svg.renderToCanvas(canvas, new RenderOptions().viewPort(0f, 0f, scaledWidth, scaledHeight));
        return bitmap;
    }

    private static final class Entry {

        final long mtime;
        final Bitmap bitmap;

        Entry(long mtime, Bitmap bitmap) {
            this.mtime = mtime;
            this.bitmap = bitmap;
        }
    }
}
