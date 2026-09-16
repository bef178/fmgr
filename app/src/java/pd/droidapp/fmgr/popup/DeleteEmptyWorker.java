package pd.droidapp.fmgr.popup;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import pd.droidapp.fmgr.util.FileProperties;
import pd.util.FileOps;
import pd.util.FileStat;

import static pd.droidapp.fmgr.util.Util.toFileProperties;

class DeleteEmptyWorker extends ProcessingWorker {

    private OnUpdatedListener onUpdated;
    private int scanned = 0;
    private List<FileProperties> matched = new LinkedList<>();
    private final Object lock = new Object();

    public void whenUpdated(OnUpdatedListener onUpdated) {
        this.onUpdated = onUpdated;
    }

    public boolean start(String startDirectory) {
        return start(() -> FileOps.singleton.listDirectory(startDirectory, 32, false, cancelRequested,
                (action, src, dst, succeeded) -> {
                    if (action == FileOps.Action.MEET) {
                        boolean hit = isEmptyDirectoryOrZeroLengthFileOrDanglingSymlink(src);
                        synchronized (lock) {
                            scanned++;
                            if (hit) {
                                matched.add(toFileProperties(src));
                            }
                        }
                    }
                }));
    }

    private boolean isEmptyDirectoryOrZeroLengthFileOrDanglingSymlink(String path) {
        FileStat stat = FileOps.singleton.stat(path);
        if (stat.isDanglingSymlink()) {
            return true;
        }
        if (stat.isDirectory(false)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(Paths.get(path))) {
                return !children.iterator().hasNext();
            } catch (IOException ignored) {
                // an unreadable directory counts as empty
                return true;
            }
        }
        return stat.size != null && stat.size == 0;
    }

    @Override
    protected void reportUpdated() {
        int nowScanned;
        List<FileProperties> nowMatched;
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
        void accept(int scanned, List<FileProperties> matched);
    }
}
