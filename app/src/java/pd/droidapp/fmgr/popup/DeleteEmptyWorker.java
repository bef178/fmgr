package pd.droidapp.fmgr.popup;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import pd.util.FileOps;

class DeleteEmptyWorker extends ProcessingWorker {

    private OnUpdatedListener onUpdated;
    private int scanned = 0;
    private List<String> matched = new LinkedList<>();
    private final Object lock = new Object();

    public void whenUpdated(OnUpdatedListener onUpdated) {
        this.onUpdated = onUpdated;
    }

    public boolean start(String startDirectory) {
        return start(() -> FileOps.singleton.listDirectory(startDirectory, 32, true, cancelRequested,
                (action, src, dst, succeeded) -> {
                    if (action == FileOps.Action.MEET) {
                        boolean acceptable = isEmptyDirectoryOrZeroLengthFile(src);
                        synchronized (lock) {
                            scanned++;
                            if (acceptable) {
                                matched.add(src);
                            }
                        }
                    }
                }));
    }

    private boolean isEmptyDirectoryOrZeroLengthFile(String path) {
        Path entry = Paths.get(path);
        try {
            if (path.endsWith("/")) {
                try (DirectoryStream<Path> children = Files.newDirectoryStream(entry)) {
                    return !children.iterator().hasNext();
                }
            }
            return Files.size(entry) == 0;
        } catch (IOException ignored) {
            // an unreadable or vanished entry counts as empty
            return true;
        }
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
