package pd.droidapp.fmgr.popup;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pd.droidapp.fmgr.util.FileProperties;
import pd.util.DigestCodec;
import pd.util.FileOps;
import pd.util.FileStat;

import static pd.droidapp.fmgr.util.Util.toFileProperties;

class DedupWorker extends ProcessingWorker {

    private OnUpdatedListener onUpdated;

    private int scanned = 0;
    private List<FileProperties> completed = new LinkedList<>();
    private final Object lock = new Object();

    private final Map<Long, String> firstBySize = new HashMap<>();
    private final Set<String> sha256Requested = new HashSet<>();

    DedupWorker() {
        super(200);
    }

    public void whenUpdated(OnUpdatedListener onUpdated) {
        this.onUpdated = onUpdated;
    }

    public boolean start(String startDirectory) {
        return start(() -> FileOps.singleton.listDirectory(startDirectory, 32, false, cancelRequested,
                (action, src, dst, succeeded) -> {
                    if (action == FileOps.Action.MEET) {
                        synchronized (lock) {
                            scanned++;
                        }
                        if (!src.endsWith("/")) {
                            addFile(src);
                        }
                    }
                }));
    }

    private void addFile(String path) {
        FileStat stat = FileOps.singleton.stat(path);
        if (stat.isSymlink() || stat.size == null || stat.size <= 0) {
            return;
        }
        long size = stat.size;
        String first = firstBySize.putIfAbsent(size, path);
        if (first == null) {
            return;
        }
        if (sha256Requested.add(first)) {
            requestSha256sum(first, size);
        }
        requestSha256sum(path, size);
    }

    private void requestSha256sum(String path, long size) {
        if (cancelRequested.get()) {
            return;
        }
        String sha256sum;
        try (FileInputStream inputStream = new FileInputStream(path)) {
            sha256sum = DigestCodec.sha256().checksum(inputStream);
        } catch (IOException ignored) {
            return;
        }
        synchronized (lock) {
            if (!cancelRequested.get()) {
                FileProperties item = toFileProperties(path);
                item.size = size;
                item.sha256sum = sha256sum;
                completed.add(item);
            }
        }
    }

    @Override
    protected void reportUpdated() {
        int nowScanned;
        List<FileProperties> nowCompleted;
        synchronized (lock) {
            nowScanned = scanned;
            scanned = 0;
            nowCompleted = completed;
            completed = new LinkedList<>();
        }
        if (onUpdated != null) {
            try {
                onUpdated.accept(nowScanned, nowCompleted);
            } catch (Throwable ignored) {
            }
        }
    }

    public interface OnUpdatedListener {
        void accept(int scanned, List<FileProperties> completed);
    }
}
