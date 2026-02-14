package pd.droidapp.fmgr;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class FavoritesManager {

    private static final String PREFS_NAME = "favorites";
    private static final String PREFS_KEY_PREFIX = "fav_";

    private final SharedPreferences prefs;

    public FavoritesManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void addFavorite(File file) {
        String path = file.getAbsolutePath();
        prefs.edit().putString(PREFS_KEY_PREFIX + path, path).apply();
    }

    public void removeFavorite(File file) {
        String path = file.getAbsolutePath();
        prefs.edit().remove(PREFS_KEY_PREFIX + path).apply();
    }

    public boolean isFavorite(File file) {
        String path = file.getAbsolutePath();
        return prefs.contains(PREFS_KEY_PREFIX + path);
    }

    public List<FavItem> getFavorites() {
        return prefs.getAll().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(PREFS_KEY_PREFIX))
                .map(entry -> new FavItem(new File((String) entry.getValue())))
                .sorted(Comparator.comparing(f -> f.file.getPath()))
                .collect(Collectors.toList());
    }

    public static class FavItem {

        public final File file;

        public FavItem(File file) {
            this.file = file;
        }

        public String getDisplayName() {
            String path = file.getAbsolutePath();
            if (path.equals("/")) {
                return "/";
            }
            return file.getName();
        }
    }
}
