package pd.droidapp.fmgr.util;

import android.os.Environment;

import java.io.Serializable;
import java.util.Stack;

import pd.util.FileOps;
import pd.util.PathOps;

public class PathNavigator implements Serializable {

    private static final long serialVersionUID = -1;

    public static class Entry implements Serializable {

        public final String path;
        public int scrollPosition = -1;
        public int scrollOffset;

        public Entry(String path) {
            this.path = path;
        }
    }

    private Entry current;
    private final Stack<Entry> backStack = new Stack<>();
    private final Stack<Entry> forwardStack = new Stack<>();

    public String getCurrentDirectory() {
        return current == null ? null : current.path;
    }

    public Entry getCurrentEntry() {
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
        if (current != null && target.equals(current.path)) {
            return true;
        }
        if (current != null) {
            backStack.push(current);
        }
        forwardStack.clear();
        current = new Entry(target);
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

    private boolean isUsable(Entry entry) {
        return isAccessible(entry.path) && !entry.path.equals(getCurrentDirectory());
    }

    public boolean canGoUp() {
        String currentDirectory = getCurrentDirectory();
        if (currentDirectory == null) {
            return false;
        }
        String parent = PathOps.singleton.dirname(currentDirectory);
        return !parent.equals(currentDirectory) && isAccessible(parent)
                && !Environment.getExternalStorageDirectory().getPath().equals(currentDirectory);
    }

    public boolean goUp() {
        if (!canGoUp()) {
            return false;
        }
        String parent = PathOps.singleton.dirname(getCurrentDirectory());
        Entry recent = findRecentMatchedBackEntry(parent);
        if (navigateTo(parent)) {
            if (recent != null) {
                current.scrollPosition = recent.scrollPosition;
                current.scrollOffset = recent.scrollOffset;
            }
            return true;
        }
        return false;
    }

    private Entry findRecentMatchedBackEntry(String path) {
        for (int i = 0; i < backStack.size(); i++) {
            Entry entry = backStack.get(backStack.size() - 1 - i);
            if (entry.path.equals(path)) {
                return entry;
            }
        }
        return null;
    }
}
