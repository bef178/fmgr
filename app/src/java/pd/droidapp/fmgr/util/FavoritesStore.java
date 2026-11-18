package pd.droidapp.fmgr.util;

import android.content.Context;
import androidx.annotation.NonNull;

import java.util.List;

import pd.util.PathOps;

public class FavoritesStore {

    public static String getDefaultName(String path) {
        return PathOps.singleton.basename(path);
    }

    private final FavStoreImpl impl;

    public FavoritesStore(Context context) {
        impl = new FavStoreSqliteImpl(context);
    }

    public boolean contains(String path) {
        return impl.contains(path);
    }

    public FavItem get(String path) {
        return impl.get(path);
    }

    public List<FavItem> getAll() {
        return impl.getAll();
    }

    public void put(String path) {
        impl.put(path);
    }

    public void put(String path, String name) {
        impl.put(path, name);
    }

    public void remove(String path) {
        impl.remove(path);
    }

    public static class FavItem {

        public final String path;

        public final String name;

        FavItem(@NonNull String path, @NonNull String name) {
            this.path = path;
            this.name = name;
        }
    }
}
