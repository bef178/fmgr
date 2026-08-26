package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class FileRemoveUpdater {

    private final FileRemover fileRemover;
    private final int updateInterval;

    private Runnable onRemoveStarted;
    private OnRemoveUpdateListener onRemoveUpdated;
    private Runnable onRemoveStopped;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private Timer updateTimer;
    private List<File> deleted = new LinkedList<>();
    private final AtomicInteger failed = new AtomicInteger(0);
    private final AtomicInteger progressed = new AtomicInteger(0);
    private final AtomicReference<String> current = new AtomicReference<>();
    private final Object lock = started;

    public FileRemoveUpdater() {
        this.fileRemover = new FileRemover();
        this.updateInterval = 200;
    }

    public void whenRemoveStarted(Runnable onRemoveStarted) {
        this.onRemoveStarted = onRemoveStarted;
    }

    public void whenRemoveUpdated(OnRemoveUpdateListener onRemoveUpdated) {
        this.onRemoveUpdated = onRemoveUpdated;
    }

    public void whenRemoveStopped(Runnable onRemoveStopped) {
        this.onRemoveStopped = onRemoveStopped;
    }

    public boolean start(Iterable<File> startFiles, boolean prune) {
        if (!started.compareAndSet(false, true)) {
            return false;
        }

        fileRemover.whenDeleteAction((action, src, succeeded) -> {
            switch (action) {
                case DELETE:
                    if (succeeded) {
                        synchronized (lock) {
                            deleted.add(new File(src));
                        }
                    } else {
                        failed.incrementAndGet();
                    }
                    break;
                case PROGRESS:
                    progressed.incrementAndGet();
                    break;
                default:
                    break;
            }
            current.set(src);
        });

        List<String> paths = new LinkedList<>();
        for (File file : startFiles) {
            paths.add(file.getPath());
        }
        fileRemover.start(paths, prune);
        startTimer();
        return true;
    }

    private void startTimer() {
        updateTimer = new Timer();
        if (onRemoveStarted != null) {
            updateTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        onRemoveStarted.run();
                    } catch (Throwable ignored) {
                    }
                }
            }, 0);
        }
        updateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (onRemoveUpdated != null) {
                    try {
                        onRemoveUpdated.accept(
                                dumpDeleted(),
                                failed.getAndSet(0),
                                progressed.getAndSet(0),
                                current.get());
                    } catch (Throwable ignored) {
                    }
                }
                if (!fileRemover.isRunning()) {
                    clearTimer();
                    stopped.set(true);
                    if (onRemoveStopped != null) {
                        try {
                            onRemoveStopped.run();
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

    private List<File> dumpDeleted() {
        synchronized (lock) {
            List<File> batch = deleted;
            deleted = new LinkedList<>();
            return batch;
        }
    }

    public boolean isStopped() {
        return started.get() && stopped.get();
    }

    public void cancel() {
        fileRemover.cancel();
    }

    public boolean isCancelled() {
        return fileRemover.isCancelled();
    }

    public interface OnRemoveUpdateListener {

        void accept(List<File> removed, int failed, int progressed, String current);
    }
}
