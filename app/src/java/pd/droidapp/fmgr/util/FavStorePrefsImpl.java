package pd.droidapp.fmgr.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import pd.droidapp.fmgr.util.FavoritesStore.FavItem;

import static pd.droidapp.fmgr.util.FavoritesStore.getDefaultName;

class FavStorePrefsImpl implements FavStoreImpl {

    private static final String PREFS_FAV = "favorites";
    private static final String PREFS_ITEM_PREFIX = "fav_item_";

    private final SharedPreferences sharedPreferences;

    public FavStorePrefsImpl(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_FAV, Context.MODE_PRIVATE);
    }

    private String buildPrefsKey(String path) {
        return PREFS_ITEM_PREFIX + path;
    }

    @Override
    public boolean contains(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        return sharedPreferences.contains(buildPrefsKey(path));
    }

    @Override
    public FavItem get(String path) {
        String name = sharedPreferences.getString(buildPrefsKey(path), null);
        if (name == null) {
            return null;
        }
        return new FavItem(path, name);
    }

    @Override
    public List<FavItem> getAll() {
        return sharedPreferences.getAll().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(PREFS_ITEM_PREFIX))
                .map(entry -> new FavItem(
                        entry.getKey().substring(PREFS_ITEM_PREFIX.length()),
                        (String) entry.getValue()))
                .sorted(Comparator.comparing(f -> f.path))
                .collect(Collectors.toList());
    }

    @Override
    public void put(String path) {
        put(path, null);
    }

    @Override
    public void put(String path, String name) {
        if (name == null || name.isEmpty()) {
            name = getDefaultName(path);
        }
        sharedPreferences.edit()
                .putString(buildPrefsKey(path), name)
                .apply();
    }

    @Override
    public void remove(String path) {
        sharedPreferences.edit().remove(buildPrefsKey(path)).apply();
    }
}
