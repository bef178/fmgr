package pd.droidapp.fmgr.fav;

import androidx.annotation.NonNull;

import java.io.File;

class FavItem {

    public final String path;

    private String displayName;

    public FavItem(String path) {
        this(path, getDefaultName(path));
    }

    private FavItem(String path, String displayName) {
        this.path = path;
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * @param displayName null for no change; empty for default name
     */
    public void setDisplayName(String displayName) {
        if (displayName == null) {
            return;
        }
        if (displayName.isEmpty()) {
            displayName = getDefaultName();
        }
        this.displayName = displayName;
    }

    String getDefaultName() {
        return getDefaultName(path);
    }

    @NonNull
    @Override
    public String toString() {
        return displayName + ":" + path;
    }

    static String getDefaultName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int j = path.length() - 1;
        while (j >= 0 && path.charAt(j) == '/') {
            j--;
        }
        if (j < 0) {
            return "/";
        }
        int i = path.lastIndexOf('/', j);
        return path.substring(i + 1, j + 1);
    }

    public static FavItem parse(String s) {
        if (s == null) {
            return null;
        }
        int i = s.indexOf(":");
        if (i < 0) {
            return new FavItem(s);
        } else {
            return new FavItem(s.substring(i + 1), s.substring(0, i));
        }
    }
}
