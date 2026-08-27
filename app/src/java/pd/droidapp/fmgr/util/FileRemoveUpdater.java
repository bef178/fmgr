package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private int failed = 0;
    private int progressed = 0;
    private String current;
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
                    synchronized (lock) {
                        if (succeeded == null) {
                            current = src;
                        } else if (succeeded) {
                            deleted.add(new File(src));
                        } else {
                            failed++;
                        }
                    }
                    break;
                case PROGRESS:
                    synchronized (lock) {
                        progressed++;
                    }
                    break;
                default:
                    break;
            }
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
                        List<File> nowDeleted;
                        int nowFailed;
                        int nowProgressed;
                        String nowCurrent;
                        synchronized (lock) {
                            nowDeleted = deleted;
                            deleted = new LinkedList<>();
                            nowFailed = failed;
                            failed = 0;
                            nowProgressed = progressed;
                            progressed = 0;
                            nowCurrent = current;
                        }
                        onRemoveUpdated.accept(
                                nowDeleted,
                                nowFailed,
                                nowProgressed,
                                nowCurrent);
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
