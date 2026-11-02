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

    public Bitmap get(String path, long mtime) {
        Entry cached = cache.get(path);
        if (cached != null && cached.mtime == mtime) {
            return cached.bitmap;
        }
        return null;
    }

    public void put(String path, long mtime, Bitmap bitmap) {
        cache.put(path, new Entry(mtime, bitmap));
    }

    public void remove(String path) {
        cache.remove(path);
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
