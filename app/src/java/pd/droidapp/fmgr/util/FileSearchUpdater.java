package pd.droidapp.fmgr.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import pd.droidapp.fmgr.util.FileScanner.State;

import pd.util.PathOps;

class FileSearchUpdater {

    private FileScanUpdater nameScanner;
    private FileScanUpdater contentScanner;

    private Runnable onSearchStarted;
    private OnSearchUpdatedListener onSearchUpdated;
    private Runnable onSearchStopped;

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private final Set<String> allMatched = Collections.synchronizedSet(new HashSet<>());

    public void whenSearchStarted(Runnable onSearchStarted) {
        this.onSearchStarted = onSearchStarted;
    }

    public void whenSearchUpdated(OnSearchUpdatedListener onSearchUpdated) {
        this.onSearchUpdated = onSearchUpdated;
    }

    public void whenSearchStopped(Runnable onSearchStopped) {
        this.onSearchStopped = onSearchStopped;
    }

    public boolean start(String startDirectory, String query) {
        if (query == null || query.isEmpty()) {
            return false;
        }

        if (!state.compareAndSet(State.IDLE, State.RUNNING)) {
            return false;
        }

        nameScanner = new FileScanUpdater();
        nameScanner.whenReached(path -> PathOps.singleton.basename(path).contains(query));
        nameScanner.whenScanStarted(() -> {
            if (onSearchStarted != null) {
                try {
                    onSearchStarted.run();
                } catch (Throwable ignored) {
                }
            }
        });
        nameScanner.whenScanUpdated((scanned, matched) -> {
            allMatched.addAll(matched);
            if (onSearchUpdated != null) {
                try {
                    onSearchUpdated.accept(scanned, matched);
                } catch (Throwable ignored) {
                }
            }
        });
        nameScanner.whenScanStopped(() -> {
            if (nameScanner.isCompleted() && state.get() == State.RUNNING) {
                if (!contentScanner.start(startDirectory)) {
                    markCancelled();
                }
            } else if (state.get() == State.CANCELLING) {
                markCancelled();
            } else {
                // name scan itself failed
                markFailed();
            }
        });

        contentScanner = new FileScanUpdater();
        contentScanner.whenReached(path -> {
            if (path.endsWith("/") || allMatched.contains(path)) {
                return false;
            }
            File file = new File(path);
            return (isTextFile(file) || isSmallAnonymousFile(file))
                    && fileContainsText(file, query);
        });
        contentScanner.whenScanUpdated((scanned, matched) -> {
            if (onSearchUpdated != null) {
                try {
                    onSearchUpdated.accept(scanned, matched);
                } catch (Throwable ignored) {
                }
            }
        });
        contentScanner.whenScanStopped(() -> {
            if (state.get() == State.CANCELLING) {
                markCancelled();
            } else if (contentScanner.isCompleted()) {
                markCompleted();
            } else {
                markFailed();
            }
        });

        if (!nameScanner.start(startDirectory)) {
            markCancelled();
            return false;
        }

        return true;
    }

    public boolean isCompleted() {
        return state.get() == State.COMPLETED;
    }

    public void cancel() {
        if (state.compareAndSet(State.RUNNING, State.CANCELLING)) {
            if (nameScanner != null) {
                nameScanner.cancel();
            }
            if (contentScanner != null) {
                contentScanner.cancel();
            }
        }
    }

    public boolean isCancelled() {
        State state = this.state.get();
        return state == State.CANCELLING || state == State.CANCELLED;
    }

    public boolean isStopped() {
        State state = this.state.get();
        return state == State.COMPLETED || state == State.CANCELLED || state == State.FAILED;
    }

    private void markCancelled() {
        goToStop(State.CANCELLED);
    }

    private void markCompleted() {
        goToStop(State.COMPLETED);
    }

    private void markFailed() {
        goToStop(State.FAILED);
    }

    // settles the terminal state: the natural label wins if the search is still
    // RUNNING, otherwise a concurrent cancel has already claimed CANCELLING -
    // exactly one CAS succeeds, so the label is unique and the callback fires once
    private void goToStop(State stop) {
        if (state.compareAndSet(State.RUNNING, stop)
                || state.compareAndSet(State.CANCELLING, State.CANCELLED)) {
            if (onSearchStopped != null) {
                try {
                    onSearchStopped.run();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private boolean isTextFile(File file) {
        String lowerName = file.getName().toLowerCase();
        return lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".json") ||
                lowerName.endsWith(".xml") || lowerName.endsWith(".html") || lowerName.endsWith(".css") ||
                lowerName.endsWith(".js") || lowerName.endsWith(".java") || lowerName.endsWith(".kt") ||
                lowerName.endsWith(".py") || lowerName.endsWith(".c") || lowerName.endsWith(".cpp") ||
                lowerName.endsWith(".h") || lowerName.endsWith(".hpp") || lowerName.endsWith(".sh") ||
                lowerName.endsWith(".yaml") || lowerName.endsWith(".yml") || lowerName.endsWith(".properties") ||
                lowerName.endsWith(".gradle") || lowerName.endsWith(".csv") || lowerName.endsWith(".log");
    }

    private boolean isSmallAnonymousFile(File file) {
        return !file.getName().startsWith(".") && file.length() < 1024 * 1024;
    }

    private boolean fileContainsText(File file, String query) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isCancelled()) {
                    return false;
                }
                if (line.contains(query)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    interface OnSearchUpdatedListener {
        void accept(int scanned, List<String> matched);
    }
}
