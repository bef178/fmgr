package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class FileRemoveUpdater {

    private final FileRemover fileRemover;
    private final int updateInterval;

    private BiConsumer<File, Boolean> onFile;
    private BiConsumer<File, Boolean> onDirectory;

    private Runnable onRemoveStarted;
    private BiConsumer<Integer, Integer> onRemoveUpdated;
    private Runnable onRemoveStopped;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private Timer updateTimer;
    private final AtomicInteger removed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);

    public FileRemoveUpdater() {
        this.fileRemover = new FileRemover();
        this.updateInterval = 1000;
    }

    public void whenFileRemoved(BiConsumer<File, Boolean> onFileRemoved) {
        this.onFile = onFileRemoved;
    }

    public void whenDirectoryRemoved(BiConsumer<File, Boolean> onDirectoryRemoved) {
        this.onDirectory = onDirectoryRemoved;
    }

    public void whenRemoveStarted(Runnable onRemoveStarted) {
        this.onRemoveStarted = onRemoveStarted;
    }

    public void whenRemoveUpdated(BiConsumer<Integer, Integer> onRemoveUpdated) {
        this.onRemoveUpdated = onRemoveUpdated;
    }

    public void whenRemoveStopped(Runnable onRemoveStopped) {
        this.onRemoveStopped = onRemoveStopped;
    }

    public boolean start(Iterable<File> startFiles, File stopDirectory) {
        if (!started.compareAndSet(false, true)) {
            return false;
        }

        fileRemover.whenDirectoryRemoved((directory, isFailed) -> {
            count(isFailed);
            if (onDirectory != null) {
                onDirectory.accept(directory, isFailed);
            }
        });
        fileRemover.whenFileRemoved((file, isFailed) -> {
            count(isFailed);
            if (onFile != null) {
                onFile.accept(file, isFailed);
            }
        });
        fileRemover.start(startFiles, stopDirectory);
        startTimer();
        return true;
    }

    private void count(boolean isFailed) {
        if (isFailed) {
            failed.incrementAndGet();
        } else {
            removed.incrementAndGet();
        }
    }

    private void startTimer() {
        updateTimer = new Timer();
        if (onRemoveStarted != null) {
            updateTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    onRemoveStarted.run();
                }
            }, 0);
        }
        updateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // note the race condition
                if (fileRemover.isRunning()) {
                    if (onRemoveUpdated != null) {
                        onRemoveUpdated.accept(removed.getAndSet(0), failed.getAndSet(0));
                    }
                } else {
                    if (onRemoveUpdated != null) {
                        onRemoveUpdated.accept(removed.getAndSet(0), failed.getAndSet(0));
                    }
                    clearTimer();
                    if (onRemoveStopped != null) {
                        onRemoveStopped.run();
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
        return fileRemover.isCompleted();
    }

    public void cancel() {
        fileRemover.cancel();
    }

    public boolean isCancelled() {
        return fileRemover.isCancelled();
    }
}
