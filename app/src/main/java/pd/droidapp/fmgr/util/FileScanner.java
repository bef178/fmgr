package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

import pd.util.PathExtension;

public class FileScanner {

    private final int updateInterval;
    private final int maxDepth;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private Thread scanThread;
    private Timer scanTimer;
    private final AtomicInteger scanned = new AtomicInteger(0);
    private List<File> accumulated = new LinkedList<>();
    private final Object lock = started;

    private Function<File, Boolean> onDirectoryReached;
    private Function<File, Boolean> onFileReached;
    private Runnable onScanStarted;
    private BiConsumer<Integer, List<File>> onScanUpdated;
    private Runnable onScanStopped;

    public FileScanner() {
        this.updateInterval = 1000;
        this.maxDepth = 32;
    }

    /**
     * Called on each file found during the scan.
     * The file will be accumulated if the callback returns `true`.
     * Follows symlinks.
     */
    public void whenFileReached(Function<File, Boolean> onFileReached) {
        this.onFileReached = onFileReached;
    }

    public void whenDirectoryReached(Function<File, Boolean> onDirectoryReached) {
        this.onDirectoryReached = onDirectoryReached;
    }

    public void whenScanStarted(Runnable onScanStarted) {
        this.onScanStarted = onScanStarted;
    }

    public void whenScanUpdated(BiConsumer<Integer, List<File>> onScanUpdated) {
        this.onScanUpdated = onScanUpdated;
    }

    public void whenScanStopped(Runnable onScanStopped) {
        this.onScanStopped = onScanStopped;
    }

    public boolean start(File startDirectory) {
        if (!started.compareAndSet(false, true)) {
            return false;
        }

        startTimer();

        scanThread = new Thread(() -> {
            doScan(startDirectory);
            if (!cancelled.get()) {
                completed.set(true);
            }
        });
        scanThread.start();
        return true;
    }

    private void doScan(File startDirectory) {
        Stack<Frame> stack = new Stack<>();
        stack.push(new Frame(startDirectory, 0));
        while (!cancelled.get() && !stack.isEmpty()) {
            Frame frame = stack.pop();
            File[] children = frame.file.listFiles();
            if (children == null) {
                continue;
            }

            Arrays.sort(children, (a, b) -> PathExtension.compare(a.getPath(), b.getPath()));

            // push directories in reverse so they are visited in sorted order
            for (int i = children.length - 1; i >= 0; i--) {
                if (cancelled.get()) {
                    return;
                }
                File child = children[i];
                if (child.isDirectory() && frame.depth < maxDepth) {
                    if (onDirectoryReached != null && onDirectoryReached.apply(child)) {
                        synchronized (lock) {
                            accumulated.add(child);
                        }
                    }
                    stack.push(new Frame(child, frame.depth + 1));
                }
            }
            for (File child : children) {
                if (cancelled.get()) {
                    return;
                }
                if (child.isFile()) {
                    scanned.incrementAndGet();
                    if (onFileReached != null && onFileReached.apply(child)) {
                        synchronized (lock) {
                            accumulated.add(child);
                        }
                    }
                }
            }
        }
    }

    private List<File> dump() {
        synchronized (lock) {
            List<File> batch = accumulated;
            accumulated = new LinkedList<>();
            return batch;
        }
    }

    private void startTimer() {
        scanTimer = new Timer();
        if (onScanStarted != null) {
            scanTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    onScanStarted.run();
                }
            }, 0);
        }
        scanTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (onScanUpdated != null) {
                    onScanUpdated.accept(scanned.getAndSet(0), dump());
                }
                if (cancelled.get() || completed.get()) {
                    clearTimer();
                    if (onScanStopped != null) {
                        onScanStopped.run();
                    }
                }
            }
        }, updateInterval, updateInterval);
    }

    private void clearTimer() {
        if (scanTimer != null) {
            scanTimer.cancel();
            scanTimer.purge();
            scanTimer = null;
        }
    }

    public void cancel() {
        cancelled.set(true);
        if (scanThread != null) {
            scanThread.interrupt();
        }
    }

    public boolean isCompleted() {
        return completed.get();
    }

    private static class Frame {

        final File file;
        final int depth;

        Frame(File file, int depth) {
            this.file = file;
            this.depth = depth;
        }
    }
}
