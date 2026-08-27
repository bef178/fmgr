package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class FileScanUpdater {

    private final FileScanner fileScanner;
    private final int updateInterval;

    private Function<File, Boolean> onFile;
    private Function<File, Boolean> onDirectory;

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

    public void whenScanUpdated(OnScanUpdateListener onScanUpdated) {
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
            Function<File, Boolean> predicate = action == FileScanner.ScanAction.DIRECTORY ? onDirectory : onFile;
            boolean acceptable = predicate != null && predicate.apply(file);
            synchronized (lock) {
                scanned++;
                if (acceptable) {
                    matched.add(file.getPath());
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

    public interface OnScanUpdateListener {

        void accept(int scanned, List<String> matched);
    }
}
