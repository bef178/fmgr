package pd.droidapp.fmgr.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import pd.util.FileOps;
import pd.util.PathOps;

class SearchWorker extends ProcessingWorker {

    private OnUpdatedListener onUpdated;
    private final Set<String> allNameMatched = new HashSet<>();
    private List<String> matched = new LinkedList<>();
    private int scanned = 0;
    private final Object lock = new Object();

    public void whenUpdated(OnUpdatedListener onUpdated) {
        this.onUpdated = onUpdated;
    }

    public boolean start(String startDirectory, String query) {
        if (query == null || query.isEmpty()) {
            return false;
        }
        return start(() -> {
            scanNames(startDirectory, query);
            if (isCancelled()) {
                return;
            }
            scanContents(startDirectory, query);
        });
    }

    private void scanNames(String startDirectory, String query) {
        FileOps.singleton.listDirectory(startDirectory, 32, true, cancelRequested,
                (action, src, dst, succeeded) -> {
                    if (action == FileOps.Action.MEET) {
                        boolean hit = PathOps.singleton.basename(src).contains(query);
                        if (hit) {
                            allNameMatched.add(src);
                        }
                        accumulate(src, hit);
                    }
                });
    }

    private void scanContents(String startDirectory, String query) {
        FileOps.singleton.listDirectory(startDirectory, 32, true, cancelRequested,
                (action, src, dst, succeeded) -> {
                    if (action == FileOps.Action.MEET) {
                        boolean hit = !src.endsWith("/")
                                && !allNameMatched.contains(src)
                                && (isTextFile(src) || isSmallAnonymousFile(src))
                                && fileContainsText(src, query);
                        accumulate(src, hit);
                    }
                });
    }

    private void accumulate(String path, boolean hit) {
        synchronized (lock) {
            scanned++;
            if (hit) {
                matched.add(path);
            }
        }
    }

    private boolean isTextFile(String path) {
        String lowerName = Paths.get(path).getFileName().toString().toLowerCase();
        return lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".json") ||
                lowerName.endsWith(".xml") || lowerName.endsWith(".html") || lowerName.endsWith(".css") ||
                lowerName.endsWith(".js") || lowerName.endsWith(".java") || lowerName.endsWith(".kt") ||
                lowerName.endsWith(".py") || lowerName.endsWith(".c") || lowerName.endsWith(".cpp") ||
                lowerName.endsWith(".h") || lowerName.endsWith(".hpp") || lowerName.endsWith(".sh") ||
                lowerName.endsWith(".yaml") || lowerName.endsWith(".yml") || lowerName.endsWith(".properties") ||
                lowerName.endsWith(".gradle") || lowerName.endsWith(".csv") || lowerName.endsWith(".log");
    }

    private boolean isSmallAnonymousFile(String path) {
        if (PathOps.singleton.basename(path).startsWith(".")) {
            return false;
        }
        try {
            return Files.size(Paths.get(path)) < 1024 * 1024;
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean fileContainsText(String path, String query) {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isCancelled()) {
                    return false;
                }
                if (line.contains(query)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    @Override
    protected void reportUpdated() {
        int nowScanned;
        List<String> nowMatched;
        synchronized (lock) {
            nowScanned = scanned;
            scanned = 0;
            nowMatched = matched;
            matched = new LinkedList<>();
        }
        if (onUpdated != null) {
            try {
                onUpdated.accept(nowScanned, nowMatched);
            } catch (Throwable ignored) {
            }
        }
    }

    public interface OnUpdatedListener {
        void accept(int scanned, List<String> matched);
    }
}
