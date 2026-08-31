package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

class FilePasteUpdater {

    private final FilePaster filePaster;
    private final int updateInterval;

    private Runnable onPasteStarted;
    private OnPasteUpdatedListener onPasteUpdated;
    private Runnable onPasteStopped;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private Timer updateTimer;
    private int added = 0;
    private int deleted = 0;
    private int renamed = 0;
    private int failed = 0;
    private int progressed = 0;
    private String current;
    private final Object lock = started;

    public FilePasteUpdater() {
        this.filePaster = new FilePaster();
        this.updateInterval = 200;
    }

    public void whenPasteStarted(Runnable onPasteStarted) {
        this.onPasteStarted = onPasteStarted;
    }

    public void whenPasteUpdated(OnPasteUpdatedListener onPasteUpdated) {
        this.onPasteUpdated = onPasteUpdated;
    }

    public void whenPasteStopped(Runnable onPasteStopped) {
        this.onPasteStopped = onPasteStopped;
    }

    public boolean start(boolean isCopy, List<File> srcFiles, File dstDirectory, FilePaster.ConflictResolution resolution, boolean mergeDirectories) {
        if (!started.compareAndSet(false, true)) {
            return false;
        }

        filePaster.whenPasteAction((action, src, dst, succeeded) -> {
            synchronized (lock) {
                if (succeeded == null) {
                    current = src != null ? src : dst;
                } else if (succeeded) {
                    switch (action) {
                        case ADD:
                            added++;
                            break;
                        case DELETE:
                            deleted++;
                            break;
                        case RENAME:
                            renamed++;
                            break;
                        case PROGRESS:
                            progressed++;
                            break;
                        default:
                            break;
                    }
                } else {
                    failed++;
                }
            }
        });

        List<String> paths = new LinkedList<>();
        for (File src : srcFiles) {
            paths.add(src.getPath());
        }
        if (!filePaster.start(isCopy, paths, dstDirectory.getPath(), resolution, mergeDirectories)) {
            stopped.set(true);
            return false;
        }
        startTimer();
        return true;
    }

    private void startTimer() {
        updateTimer = new Timer();
        if (onPasteStarted != null) {
            updateTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        onPasteStarted.run();
                    } catch (Throwable ignored) {
                    }
                }
            }, 0);
        }
        updateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                boolean running = filePaster.isRunning();
                if (onPasteUpdated != null) {
                    try {
                        int nowAdded;
                        int nowDeleted;
                        int nowRenamed;
                        int nowFailed;
                        int nowProgressed;
                        String nowCurrent;
                        synchronized (lock) {
                            nowAdded = added;
                            added = 0;
                            nowDeleted = deleted;
                            deleted = 0;
                            nowRenamed = renamed;
                            renamed = 0;
                            nowFailed = failed;
                            failed = 0;
                            nowProgressed = progressed;
                            progressed = 0;
                            nowCurrent = current;
                        }
                        onPasteUpdated.accept(
                                nowAdded,
                                nowDeleted,
                                nowRenamed,
                                nowFailed,
                                nowProgressed,
                                nowCurrent);
                    } catch (Throwable ignored) {
                    }
                }
                if (!running) {
                    clearTimer();
                    stopped.set(true);
                    if (onPasteStopped != null) {
                        try {
                            onPasteStopped.run();
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

    public boolean isCompleted() {
        return filePaster.isCompleted();
    }

    public void cancel() {
        filePaster.cancel();
    }

    public boolean isCancelled() {
        return filePaster.isCancelled();
    }

    interface OnPasteUpdatedListener {

        void accept(int added, int removed, int renamed, int failed, int progressed, String current);
    }
}
