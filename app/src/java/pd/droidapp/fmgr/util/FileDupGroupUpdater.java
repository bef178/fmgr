package pd.droidapp.fmgr.util;

import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

class FileDupGroupUpdater {

    private final FileDupGrouper fileDupGrouper;
    private final int updateInterval;

    private Runnable onDupGroupStarted;
    private OnDupGroupUpdatedListener onDupGroupUpdated;
    private Runnable onDupGroupStopped;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private Timer updateTimer;
    private int scanned = 0;
    private List<FileDupGrouper.FileProperties> completed = new LinkedList<>();
    private final Object lock = new Object();

    FileDupGroupUpdater() {
        this(200);
    }

    FileDupGroupUpdater(int updateInterval) {
        this.fileDupGrouper = new FileDupGrouper();
        this.updateInterval = updateInterval;
    }

    public void whenDupGroupStarted(Runnable onDupGroupStarted) {
        this.onDupGroupStarted = onDupGroupStarted;
    }

    public void whenDupGroupUpdated(OnDupGroupUpdatedListener onDupGroupUpdated) {
        this.onDupGroupUpdated = onDupGroupUpdated;
    }

    public void whenDupGroupStopped(Runnable onDupGroupStopped) {
        this.onDupGroupStopped = onDupGroupStopped;
    }

    public boolean start(String startDirectory) {
        if (!started.compareAndSet(false, true)) {
            return false;
        }

        fileDupGrouper.whenReport((path, props) -> {
            synchronized (lock) {
                if (props == null) {
                    scanned++;
                } else {
                    completed.add(props);
                }
            }
        });

        if (!fileDupGrouper.start(startDirectory)) {
            return false;
        }
        startTimer();
        return true;
    }

    private void startTimer() {
        updateTimer = new Timer();
        if (onDupGroupStarted != null) {
            updateTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        onDupGroupStarted.run();
                    } catch (Throwable ignored) {
                    }
                }
            }, 0);
        }
        updateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                boolean running = fileDupGrouper.isRunning();
                if (onDupGroupUpdated != null) {
                    int nowScanned;
                    List<FileDupGrouper.FileProperties> nowCompleted;
                    synchronized (lock) {
                        nowScanned = scanned;
                        scanned = 0;
                        nowCompleted = completed;
                        completed = new LinkedList<>();
                    }
                    try {
                        onDupGroupUpdated.accept(nowScanned, nowCompleted);
                    } catch (Throwable ignored) {
                    }
                }
                if (!running) {
                    clearTimer();
                    if (onDupGroupStopped != null) {
                        try {
                            onDupGroupStopped.run();
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

    public boolean isRunning() {
        return fileDupGrouper.isRunning();
    }

    public boolean isCompleted() {
        return fileDupGrouper.isCompleted();
    }

    public void cancel() {
        fileDupGrouper.cancel();
    }

    public boolean isCancelled() {
        return fileDupGrouper.isCancelled();
    }

    public interface OnDupGroupUpdatedListener {
        void accept(int scanned, List<FileDupGrouper.FileProperties> completed);
    }
}
