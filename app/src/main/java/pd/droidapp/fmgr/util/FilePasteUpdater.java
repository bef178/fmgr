package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class FilePasteUpdater {

    private final FilePaster filePaster;
    private final int updateInterval;

    private Runnable onPasteStarted;
    private OnPasteUpdateListener onPasteUpdated;
    private Runnable onPasteStopped;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private Timer updateTimer;
    private final AtomicInteger added = new AtomicInteger(0);
    private final AtomicInteger deleted = new AtomicInteger(0);
    private final AtomicInteger renamed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final AtomicInteger progressed = new AtomicInteger(0);
    private final AtomicReference<String> current = new AtomicReference<>();

    public FilePasteUpdater() {
        this.filePaster = new FilePaster();
        this.updateInterval = 200;
    }

    public void whenPasteStarted(Runnable onPasteStarted) {
        this.onPasteStarted = onPasteStarted;
    }

    public void whenPasteUpdated(OnPasteUpdateListener onPasteUpdated) {
        this.onPasteUpdated = onPasteUpdated;
    }

    public void whenPasteStopped(Runnable onPasteStopped) {
        this.onPasteStopped = onPasteStopped;
    }

    public void start(boolean isCopy, List<File> srcFiles, File dstDirectory, FilePaster.ConflictResolution resolution, boolean mergeDirectories) {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        filePaster.whenPasteAction((action, src, dst, succeeded) -> {
            if (succeeded) {
                switch (action) {
                    case ADD:
                        added.incrementAndGet();
                        break;
                    case DELETE:
                        deleted.incrementAndGet();
                        break;
                    case RENAME:
                        renamed.incrementAndGet();
                        break;
                }
            } else {
                failed.incrementAndGet();
            }
            if (action == FilePaster.PasteAction.PROGRESS) {
                progressed.incrementAndGet();
            }
            if (src != null) {
                current.set(src);
            } else if (dst != null) {
                current.set(dst);
            }
        });

        List<String> paths = new LinkedList<>();
        for (File src : srcFiles) {
            paths.add(src.getPath());
        }
        filePaster.start(isCopy, paths, dstDirectory.getPath(), resolution, mergeDirectories);
        startTimer();
    }

    private void startTimer() {
        updateTimer = new Timer();
        if (onPasteStarted != null) {
            updateTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    onPasteStarted.run();
                }
            }, 0);
        }
        updateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // note the race condition
                if (onPasteUpdated != null) {
                    onPasteUpdated.accept(
                            added.getAndSet(0),
                            deleted.getAndSet(0),
                            renamed.getAndSet(0),
                            failed.getAndSet(0),
                            progressed.getAndSet(0),
                            current.get());
                }
                if (!filePaster.isRunning()) {
                    clearTimer();
                    stopped.set(true);
                    if (onPasteStopped != null) {
                        onPasteStopped.run();
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
        return filePaster.isCompleted();
    }

    public boolean isStopped() {
        return started.get() && stopped.get();
    }

    public void cancel() {
        filePaster.cancel();
    }

    public boolean isCancelled() {
        return filePaster.isCancelled();
    }

    interface OnPasteUpdateListener {

        void accept(int added, int removed, int renamed, int failed, int progressed, String current);
    }
}
