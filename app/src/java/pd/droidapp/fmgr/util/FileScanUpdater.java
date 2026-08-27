package pd.droidapp.fmgr.util;

import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class FileScanUpdater {

    private final FileScanner fileScanner;
    private final int updateInterval;

    private Function<String, Boolean> onReached;

    private Runnable onScanStarted;
    private OnScanUpdateListener onScanUpdated;
    private Runnable onScanStopped;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private Timer updateTimer;
    private int scanned = 0;
    private List<String> matched = new LinkedList<>();
    private final Object lock = started;

    public FileScanUpdater() {
        this.fileScanner = new FileScanner();
        this.updateInterval = 1000;
    }

    /**
     * Called on each file or directory found during the scan
     * - which will be accumulated if the callback returns `true`
     * - directory paths end with "/"
     * Follows symlinks.
     */
    public void whenReached(Function<String, Boolean> onReached) {
        this.onReached = onReached;
    }

    public void whenScanStarted(Runnable onScanStarted) {
        this.onScanStarted = onScanStarted;
    }

    public void whenScanUpdated(OnScanUpdateListener onScanUpdated) {
        this.onScanUpdated = onScanUpdated;
    }

    public void whenScanStopped(Runnable onScanStopped) {
        this.onScanStopped = onScanStopped;
    }

    public boolean start(String startDirectory) {
        if (!started.compareAndSet(false, true)) {
            return false;
        }

        fileScanner.whenScanAction(path -> {
            boolean acceptable = onReached != null && onReached.apply(path);
            synchronized (lock) {
                scanned++;
                if (acceptable) {
                    matched.add(path);
                }
            }
        });

        fileScanner.start(startDirectory);
        startTimer();
        return true;
    }

    private void startTimer() {
        updateTimer = new Timer();
        if (onScanStarted != null) {
            updateTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        onScanStarted.run();
                    } catch (Throwable ignored) {
                    }
                }
            }, 0);
        }
        updateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                boolean running = fileScanner.isRunning();
                if (onScanUpdated != null) {
                    int nowScanned;
                    List<String> nowMatched;
                    synchronized (lock) {
                        nowScanned = scanned;
                        scanned = 0;
                        nowMatched = matched;
                        matched = new LinkedList<>();
                    }
                    try {
                        onScanUpdated.accept(nowScanned, nowMatched);
                    } catch (Throwable ignored) {
                    }
                }
                if (!running) {
                    clearTimer();
                    if (onScanStopped != null) {
                        try {
                            onScanStopped.run();
                        } catch (Throwable ignored) {
                        }
                    }
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

    public boolean isCompleted() {
        return fileScanner.isCompleted();
    }

    public void cancel() {
        fileScanner.cancel();
    }

    public interface OnScanUpdateListener {

        void accept(int scanned, List<String> matched);
    }
}
