package pd.droidapp.fmgr.fav;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

// TODO save to sqlite
public class FavItemStore {

    private static final String PREFS_FAV = "favorites";
    private static final String PREFS_ITEM_PREFIX = "fav_item_";

    private final SharedPreferences sharedPreferences;

    public FavItemStore(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_FAV, Context.MODE_PRIVATE);
    }

    private String buildPrefsKey(String path) {
        return PREFS_ITEM_PREFIX + path;
    }

    public boolean contains(File file) {
        return sharedPreferences.contains(buildPrefsKey(file.getAbsolutePath()));
    }

    List<FavItem> getAll() {
        return sharedPreferences.getAll().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(PREFS_ITEM_PREFIX))
                .map(entry -> FavItem.parse((String) entry.getValue()))
                .sorted(Comparator.comparing(f -> f.path))
                .collect(Collectors.toList());
    }

    public void put(File file) {
        put(new FavItem(file.getAbsolutePath()));
    }

    void put(FavItem favItem) {
        sharedPreferences.edit()
                .putString(buildPrefsKey(favItem.path), favItem.toString())
                .apply();
    }

    public void remove(File file) {
        remove(file.getAbsolutePath());
    }

    void remove(FavItem favItem) {
        remove(favItem.path);
    }

    private void remove(String path) {
        sharedPreferences.edit().remove(buildPrefsKey(path)).apply();
    }
}
