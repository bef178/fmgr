package pd.droidapp.fmgr.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import pd.util.DigestCodec;
import pd.util.FileOps;

public class FileDupGrouper {

    private BiConsumer<String, FileProperties> onReport;

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private final Map<Long, String> firstBySize = new HashMap<>();
    private final Set<String> checksumRequested = new HashSet<>();

    private final Object lock = new Object();
    private int pendingChecksums = 0;

    private ThreadPoolExecutor checksumThread;

    public void whenReport(BiConsumer<String, FileProperties> onReport) {
        this.onReport = onReport;
    }

    public boolean start(String startDirectory) {
        if (!state.compareAndSet(State.IDLE, State.RUNNING)) {
            return false;
        }

        checksumThread = new ThreadPoolExecutor(
                0, 1, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

        Thread workerThread = new Thread(() -> {
            try {
                scan(startDirectory);
                synchronized (lock) {
                    while (pendingChecksums > 0 && !cancelled.get()) {
                        try {
                            lock.wait();
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
                state.compareAndSet(State.RUNNING, State.FAILED);
            } finally {
                state.compareAndSet(State.RUNNING, State.COMPLETED);
                state.compareAndSet(State.CANCELLING, State.CANCELLED);
                checksumThread.shutdownNow();
            }
        });
        workerThread.start();
        return true;
    }

    private void scan(String startDirectory) {
        FileOps.singleton.listDirectory(startDirectory, 32, true, cancelled,
                (action, src, dst, succeeded) -> {
                    if (action == FileOps.Action.MEET) {
                        if (onReport != null) {
                            try {
                                onReport.accept(src, null);
                            } catch (Throwable ignored) {
                            }
                        }
                        if (!src.endsWith("/")) {
                            addFile(src);
                        }
                    }
                });
    }

    private void addFile(String path) {
        long size;
        try {
            size = Files.size(Paths.get(path));
        } catch (IOException ignored) {
            return; // unreadable or vanished file: not groupable
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
                boolean deliver;
                synchronized (lock) {
                    deliver = !cancelled.get() && md5sum != null;
                }
                if (deliver && onReport != null) {
                    try {
                        onReport.accept(null, new FileProperties(path, size, md5sum));
                    } catch (Throwable ignored) {
                    }
                }
                synchronized (lock) {
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

    public boolean isRunning() {
        State state = this.state.get();
        return state == State.RUNNING || state == State.CANCELLING;
    }

    public boolean isCompleted() {
        return state.get() == State.COMPLETED;
    }

    public void cancel() {
        while (true) {
            State current = state.get();
            if (current == State.RUNNING) {
                if (state.compareAndSet(State.RUNNING, State.CANCELLING)) {
                    cancelled.set(true);
                    return;
                }
            } else if (current == State.IDLE) {
                if (state.compareAndSet(State.IDLE, State.CANCELLED)) {
                    return;
                }
            } else {
                return;
            }
        }
    }

    public boolean isCancelled() {
        State state = this.state.get();
        return state == State.CANCELLING || state == State.CANCELLED;
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

    enum State {
        IDLE, RUNNING, CANCELLING, CANCELLED, COMPLETED, FAILED
    }
}
