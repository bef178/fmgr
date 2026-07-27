package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.Arrays;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import pd.util.PathExtension;

public class FileScanner {

    private final int updateInterval;
    private final int maxDepth;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread scannerThread;
    private Timer updateTimer;
    protected final AtomicInteger numFilesScanned = new AtomicInteger(0);

    public FileScanner() {
        this(1000, 32);
    }

    public FileScanner(int updateInterval, int maxDepth) {
        this.updateInterval = updateInterval;
        this.maxDepth = maxDepth;
    }

    public boolean start(File startDirectory) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }

        onScanStarted();

        startTimer();

        scannerThread = new Thread(() -> {
            try {
                doScan(startDirectory);
                if (!cancelled.get()) {
                    completed.set(true);
                }
            } finally {
                running.set(false);
            }
        });
        scannerThread.start();
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
                if (child.isDirectory()) {
                    if (onDirectory(child) && frame.depth < maxDepth) {
                        stack.push(new Frame(child, frame.depth + 1));
                    }
                }
            }
            for (File child : children) {
                if (cancelled.get()) {
                    return;
                }
                if (child.isFile()) {
                    numFilesScanned.incrementAndGet();
                    onFile(child);
                }
            }
        }
    }

    /**
     * Moment to update UI.
     */
    protected void onScanStarted() {
    }

    /**
     * Moment to update UI.
     */
    protected void onScanUpdated(int numFilesScanned) {
    }

    /**
     * Called for each directory found during the scan.
     * Follows symlinks.
     * Return `true` to descend into this directory, `false` to skip it.
     * Moment to update data.
     */
    protected boolean onDirectory(File dir) {
        return true;
    }

    /**
     * Called for each file found during the scan.
     * Follows symlinks.
     * Moment to update data.
     */
    protected void onFile(File file) {
    }

    private void startTimer() {
        updateTimer = new Timer();
        updateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                onScanUpdated(numFilesScanned.get());
                if (completed.get() || cancelled.get()) {
                    clearTimer();
                }
            }
        }, updateInterval, updateInterval);
    }

    private void clearTimer() {
        if (updateTimer != null) {
            updateTimer.cancel();
            updateTimer.purge();
            updateTimer = null;
        }
    }

    public void cancel() {
        cancelled.set(true);
        if (scannerThread != null) {
            scannerThread.interrupt();
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
