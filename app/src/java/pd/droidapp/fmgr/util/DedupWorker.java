package pd.droidapp.fmgr.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import pd.util.DigestCodec;
import pd.util.FileOps;

class DedupWorker extends ProcessingWorker {

    private OnUpdatedListener onUpdated;

    private int scanned = 0;
    private List<FileProperties> completed = new LinkedList<>();
    private int pendingChecksums = 0;
    private final Object lock = new Object();

    private final Map<Long, String> firstBySize = new HashMap<>();
    private final Set<String> checksumRequested = new HashSet<>();

    private ThreadPoolExecutor checksumThread;

    DedupWorker() {
        super(200);
    }

    public void whenUpdated(OnUpdatedListener onUpdated) {
        this.onUpdated = onUpdated;
    }

    public boolean start(String startDirectory) {
        return start(() -> {
            checksumThread = new ThreadPoolExecutor(
                    0, 1, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
            try {
                FileOps.singleton.listDirectory(startDirectory, 32, true, cancelRequested,
                        (action, src, dst, succeeded) -> {
                            if (action == FileOps.Action.MEET) {
                                synchronized (lock) {
                                    scanned++;
                                }
                                if (!src.endsWith("/")) {
                                    addFile(src);
                                }
                            }
                        });
                synchronized (lock) {
                    while (pendingChecksums > 0 && !cancelRequested.get()) {
                        try {
                            lock.wait();
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            } finally {
                checksumThread.shutdownNow();
            }
        });
    }

    private void addFile(String path) {
        long size;
        try {
            size = Files.size(Paths.get(path));
        } catch (IOException ignored) {
            return;
        }
        if (size <= 0) {
            return;
        }
        String first = firstBySize.putIfAbsent(size, path);
        if (first == null) {
            return; // checksum deferred until a sibling arrives
        }
        if (checksumRequested.add(first)) {
            requestChecksum(first, size);
        }
        if (checksumRequested.add(path)) {
            requestChecksum(path, size);
        }
    }

    private void requestChecksum(String path, long size) {
        synchronized (lock) {
            pendingChecksums++;
        }
        try {
            checksumThread.execute(() -> {
                String md5sum = md5sum(path);
                synchronized (lock) {
                    if (!cancelRequested.get() && md5sum != null) {
                        completed.add(new FileProperties(path, size, md5sum));
                    }
                    pendingChecksums--;
                    lock.notifyAll();
                }
            });
        } catch (RejectedExecutionException ignored) {
            synchronized (lock) {
                pendingChecksums--;
                lock.notifyAll();
            }
        }
    }

    private static String md5sum(String path) {
        try (FileInputStream inputStream = new FileInputStream(path)) {
            return DigestCodec.md5().checksum(inputStream);
        } catch (IOException ignored) {
            return null;
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

    public static class FileProperties {

        public final String path;
        public final long size;
        public final String md5sum;

        FileProperties(String path, long size, String md5sum) {
            this.path = path;
            this.size = size;
            this.md5sum = md5sum;
        }
    }

    public interface OnUpdatedListener {
        void accept(int scanned, List<FileProperties> completed);
    }
}
