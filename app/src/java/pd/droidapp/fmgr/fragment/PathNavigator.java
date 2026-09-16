package pd.droidapp.fmgr.fragment;

import android.os.Environment;

import java.io.Serializable;
import java.util.Stack;

import pd.util.FileOps;
import pd.util.PathOps;

public class PathNavigator implements Serializable {

    private static final long serialVersionUID = -1;

    private String currentDirectory;
    private final Stack<String> backStack = new Stack<>();
    private final Stack<String> forwardStack = new Stack<>();

    public String getCurrentDirectory() {
        return currentDirectory;
    }

    public boolean isCurrentDirectoryAccessible() {
        return isAccessible(currentDirectory);
    }

    private boolean isAccessible(String path) {
        return path != null && FileOps.singleton.stat(path).exists(true);
    }

    public boolean navigateTo(String target) {
        if (!isAccessible(target)) {
            return false;
        }
        if (target.equals(currentDirectory)) {
            return true;
        }
        if (currentDirectory != null) {
            backStack.push(currentDirectory);
        }
        forwardStack.clear();
        currentDirectory = target;
        return true;
    }

    public boolean canGoBack() {
        return backStack.stream().anyMatch(this::isAccessible);
    }

    public boolean goBack() {
        if (!canGoBack()) {
            return false;
        }

        while (!isAccessible(backStack.peek())) {
            backStack.pop();
        }
        forwardStack.push(currentDirectory);
        currentDirectory = backStack.pop();
        return true;
    }

    public boolean canGoForward() {
        return forwardStack.stream().anyMatch(this::isAccessible);
    }

    public boolean goForward() {
        if (!canGoForward()) {
            return false;
        }

        while (!isAccessible(forwardStack.peek())) {
            forwardStack.pop();
        }
        backStack.push(currentDirectory);
        currentDirectory = forwardStack.pop();
        return true;
    }

    public boolean canGoUp() {
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
        return navigateTo(PathOps.singleton.dirname(currentDirectory));
    }
}
