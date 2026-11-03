package pd.droidapp.fmgr.util;

import android.graphics.Bitmap;
import android.util.LruCache;

public class ImageCache {

    private final LruCache<String, Entry> cache;

    public ImageCache(int maxBytes) {
        cache = new LruCache<>(maxBytes) {
            @Override
            protected int sizeOf(String key, Entry entry) {
                return entry.bitmap.getAllocationByteCount();
            }
        };
    }

    public String key(String path, int width, int height) {
        return path + '@' + width + 'x' + height;
    }

    public Entry get(String key) {
        return cache.get(key);
    }

    public void put(String key, Entry entry) {
        cache.put(key, entry);
    }

    public void remove(String key) {
        cache.remove(key);
    }

    public void clear() {
        cache.evictAll();
    }

    public static final class Entry {

        public final long mtime;
        public final int sourceWidth;
        public final int sourceHeight;
        public final Bitmap bitmap;

        public Entry(long mtime, int sourceWidth, int sourceHeight, Bitmap bitmap) {
            this.mtime = mtime;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.bitmap = bitmap;
        }

        public Entry(long mtime, Bitmap bitmap) {
            this(mtime, bitmap.getWidth(), bitmap.getHeight(), bitmap);
        }
    }
}
