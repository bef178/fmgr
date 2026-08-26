package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class FileScanUpdater {

    private final FileScanner fileScanner;
    private final int updateInterval;

    private Function<File, Boolean> onFile;
    private Function<File, Boolean> onDirectory;

    private Runnable onScanStarted;
    private BiConsumer<List<File>, Integer> onScanUpdated;
    private Runnable onScanStopped;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private Timer updateTimer;
    private final AtomicInteger scanned = new AtomicInteger(0);
    private List<File> accumulated = new LinkedList<>();
    private final Object lock = started;

    public FileScanUpdater() {
        this.fileScanner = new FileScanner();
        this.updateInterval = 1000;
    }

    /**
     * Called on each file found during the scan.
     * The file will be accumulated if the callback returns `true`.
     * Follows symlinks.
     */
    public void whenFileReached(Function<File, Boolean> onFile) {
        this.onFile = onFile;
    }

    public void whenDirectoryReached(Function<File, Boolean> onDirectory) {
        this.onDirectory = onDirectory;
    }

    public void whenScanStarted(Runnable onScanStarted) {
        this.onScanStarted = onScanStarted;
    }

    public void whenScanUpdated(BiConsumer<List<File>, Integer> onScanUpdated) {
        this.onScanUpdated = onScanUpdated;
    }

    public void whenScanStopped(Runnable onScanStopped) {
        this.onScanStopped = onScanStopped;
    }

    public boolean start(File startDirectory) {
        if (!started.compareAndSet(false, true)) {
            return false;
        }

        fileScanner.whenDirectoryReached(directory -> {
            if (onDirectory != null && onDirectory.apply(directory)) {
                synchronized (lock) {
                    accumulated.add(directory);
                }
            }
        });
        fileScanner.whenFileReached(file -> {
            if (onFile != null && onFile.apply(file)) {
                synchronized (lock) {
                    accumulated.add(file);
                }
            }
            scanned.incrementAndGet();
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
                if (onScanUpdated != null) {
                    try {
                        onScanUpdated.accept(dump(), scanned.getAndSet(0));
                    } catch (Throwable ignored) {
                    }
                }
                if (!fileScanner.isRunning()) {
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

    private List<File> dump() {
        synchronized (lock) {
            List<File> batch = accumulated;
            accumulated = new LinkedList<>();
            return batch;
        }
    }
}
