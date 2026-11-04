package pd.droidapp.fmgr.util;

import android.os.Environment;

import java.io.Serializable;
import java.util.Stack;

import pd.util.FileOps;
import pd.util.PathOps;

public class PathNavigator implements Serializable {

    private static final long serialVersionUID = -1;

    public static final String CAPPING_DIRECTORY = Environment.getExternalStorageDirectory().getPath();

    private DirectoryScrollState current;
    private final Stack<DirectoryScrollState> backStack = new Stack<>();
    private final Stack<DirectoryScrollState> forwardStack = new Stack<>();

    public String getCurrentDirectory() {
        return current == null ? null : current.directory;
    }

    public DirectoryScrollState getCurrentScrollState() {
        return current;
    }

    public boolean isCurrentDirectoryAccessible() {
        return isAccessible(getCurrentDirectory());
    }

    private boolean isAccessible(String path) {
        return path != null && FileOps.singleton.stat(path).exists(true);
    }

    public boolean navigateTo(String target) {
        if (!isAccessible(target)) {
            return false;
        }
        if (current != null && target.equals(current.directory)) {
            return true;
        }
        if (current != null) {
            backStack.push(current);
        }
        forwardStack.clear();
        current = new DirectoryScrollState(target);
        return true;
    }

    public boolean canGoBack() {
        return backStack.stream().anyMatch(this::isUsable);
    }

    public boolean goBack() {
        if (!canGoBack()) {
            return false;
        }

        while (!isUsable(backStack.peek())) {
            backStack.pop();
        }
        forwardStack.push(current);
        current = backStack.pop();
        return true;
    }

    public boolean canGoForward() {
        return forwardStack.stream().anyMatch(this::isUsable);
    }

    public boolean goForward() {
        if (!canGoForward()) {
            return false;
        }

        while (!isUsable(forwardStack.peek())) {
            forwardStack.pop();
        }
        backStack.push(current);
        current = forwardStack.pop();
        return true;
    }

    private boolean isUsable(DirectoryScrollState directoryScrollState) {
        return isAccessible(directoryScrollState.directory) && !directoryScrollState.directory.equals(getCurrentDirectory());
    }

    public boolean canGoUp() {
        return getAccessibleUpDirectory(getCurrentDirectory()) != null;
    }

    public boolean goUp() {
        String d = getAccessibleUpDirectory(getCurrentDirectory());
        if (d == null) {
            return false;
        }
        DirectoryScrollState recent = findRecentMatchedBackScrollState(d);
        if (navigateTo(d)) {
            if (recent != null) {
                current.scrollPosition = recent.scrollPosition;
                current.scrollOffset = recent.scrollOffset;
            }
            return true;
        }
        return false;
    }

    private String getAccessibleUpDirectory(String directory) {
        if (directory == null) {
            return null;
        }
        String path = directory;
        while (true) {
            if (CAPPING_DIRECTORY.equals(path)) {
                return null;
            }
            String parent = PathOps.singleton.dirname(path);
            if (parent.equals(path)) {
                return null;
            }
            if (isAccessible(parent)) {
                return parent;
            }
            path = parent;
        }
    }

    private DirectoryScrollState findRecentMatchedBackScrollState(String path) {
        for (int i = 0; i < backStack.size(); i++) {
            DirectoryScrollState directoryScrollState = backStack.get(backStack.size() - 1 - i);
            if (directoryScrollState.directory.equals(path)) {
                return directoryScrollState;
            }
        }
        return null;
    }
}
