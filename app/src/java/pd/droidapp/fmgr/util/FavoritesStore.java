package pd.droidapp.fmgr.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import pd.util.PathOps;

// TODO save to sqlite
public class FavoritesStore {

    private static final String PREFS_FAV = "favorites";
    private static final String PREFS_ITEM_PREFIX = "fav_item_";

    public static String getDefaultName(String path) {
        return PathOps.singleton.basename(path);
    }

    private final SharedPreferences sharedPreferences;

    public FavoritesStore(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_FAV, Context.MODE_PRIVATE);
    }

    private String buildPrefsKey(String path) {
        return PREFS_ITEM_PREFIX + path;
    }

    public boolean contains(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        return sharedPreferences.contains(buildPrefsKey(path));
    }

    public FavItem get(String path) {
        String name = sharedPreferences.getString(buildPrefsKey(path), null);
        if (name == null) {
            return null;
        }
        return new FavItem(path, name);
    }

    public List<FavItem> getAll() {
        return sharedPreferences.getAll().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(PREFS_ITEM_PREFIX))
                .map(entry -> new FavItem(
                        entry.getKey().substring(PREFS_ITEM_PREFIX.length()),
                        (String) entry.getValue()))
                .sorted(Comparator.comparing(f -> f.path))
                .collect(Collectors.toList());
    }

    public void put(String path) {
        put(path, null);
    }

    public void put(String path, String name) {
        if (name == null || name.isEmpty()) {
            name = getDefaultName(path);
        }
        sharedPreferences.edit()
                .putString(buildPrefsKey(path), name)
                .apply();
    }

    public void remove(String path) {
        sharedPreferences.edit().remove(buildPrefsKey(path)).apply();
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
