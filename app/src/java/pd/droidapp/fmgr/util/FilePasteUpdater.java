package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.AbstractMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
    private List<String> added = new LinkedList<>();
    private List<String> deleted = new LinkedList<>();
    private List<Map.Entry<String, String>> renamed = new LinkedList<>();
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
                            added.add(dst);
                            break;
                        case DELETE:
                            deleted.add(src);
                            break;
                        case RENAME:
                            renamed.add(new AbstractMap.SimpleEntry<>(src, dst));
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
                        List<String> nowAdded;
                        List<String> nowDeleted;
                        List<Map.Entry<String, String>> nowRenamed;
                        int nowFailed;
                        int nowProgressed;
                        String nowCurrent;
                        synchronized (lock) {
                            nowAdded = added;
                            added = new LinkedList<>();
                            nowDeleted = deleted;
                            deleted = new LinkedList<>();
                            nowRenamed = renamed;
                            renamed = new LinkedList<>();
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

    public interface OnPasteUpdatedListener {

        void accept(List<String> added, List<String> removed, List<Map.Entry<String, String>> renamed, int failed, int progressed, String current);
    }
}
