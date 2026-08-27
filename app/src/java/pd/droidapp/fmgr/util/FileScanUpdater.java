package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private int scannedFiles = 0;
    private List<File> matched = new LinkedList<>();
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

        fileScanner.whenScanAction((action, file) -> {
            switch (action) {
                case DIRECTORY: {
                    if (onDirectory != null && onDirectory.apply(file)) {
                        synchronized (lock) {
                            matched.add(file);
                        }
                    }
                    break;
                }
                case FILE: {
                    boolean acceptable = onFile != null && onFile.apply(file);
                    synchronized (lock) {
                        scannedFiles++;
                        if (acceptable) {
                            matched.add(file);
                        }
                    }
                    break;
                }
                default:
                    break;
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
                if (onScanUpdated != null) {
                    List<File> nowMatched;
                    int nowScannedFiles;
                    synchronized (lock) {
                        nowMatched = matched;
                        matched = new LinkedList<>();
                        nowScannedFiles = scannedFiles;
                        scannedFiles = 0;
                    }
                    try {
                        onScanUpdated.accept(nowMatched, nowScannedFiles);
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
}
