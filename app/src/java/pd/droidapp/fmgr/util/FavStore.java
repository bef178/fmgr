package pd.droidapp.fmgr.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import pd.util.PathOps;

// TODO save to sqlite
public class FavStore {

    private static final String PREFS_FAV = "favorites";
    private static final String PREFS_ITEM_PREFIX = "fav_item_";

    private final SharedPreferences sharedPreferences;

    public FavStore(Context context) {
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
        put(new FavItem(path));
    }

    public void put(FavItem favItem) {
        sharedPreferences.edit()
                .putString(buildPrefsKey(favItem.path), favItem.getDisplayName())
                .apply();
    }

    public void remove(FavItem favItem) {
        remove(favItem.path);
    }

    public void remove(String path) {
        sharedPreferences.edit().remove(buildPrefsKey(path)).apply();
    }

    public static class FavItem {

        public final String path;

        private String displayName;

        FavItem(String path) {
            this(path, null);
        }

        FavItem(String path, String displayName) {
            this.path = path;
            this.displayName = displayName;
        }

        public String getDisplayName() {
            if (displayName == null || displayName.isEmpty()) {
                return getDefaultName();
            }
            return displayName;
        }

        public void setDisplayName(String displayName) {
            if (displayName != null && !displayName.isEmpty()) {
                this.displayName = displayName;
            } else {
                this.displayName = null;
            }
        }

        public String getDefaultName() {
            return PathOps.singleton.basename(path);
        }
    }
}
