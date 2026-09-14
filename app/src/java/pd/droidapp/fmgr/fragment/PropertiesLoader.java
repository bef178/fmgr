package pd.droidapp.fmgr.fragment;

import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import pd.droidapp.fmgr.util.FileProperties;
import pd.util.FileOps;

class PropertiesLoader {

    private static final int UPDATE_INTERVAL = 200;

    private volatile OnUpdatedListener onUpdated;

    private boolean isIdle = true;
    private volatile boolean cancelRequested;
    private long generation; // generation-based invalidation
    private final List<FileProperties> queued = new LinkedList<>();
    private final List<FileProperties> completed = new LinkedList<>();
    private final Object lock = new Object();

    private final Timer updateTimer = new Timer();
    private TimerTask updateTimerTask;

    public PropertiesLoader() {
        start();
    }

    public void whenUpdated(OnUpdatedListener onUpdated) {
        this.onUpdated = onUpdated;
    }

    private void start() {
        new Thread(() -> {
            while (true) {
                FileProperties item;
                long itemGeneration;
                synchronized (lock) {
                    while (queued.isEmpty() && !cancelRequested) {
                        isIdle = true;
                        try {
                            lock.wait();
                        } catch (InterruptedException ignored) {
                        }
                    }
                    if (cancelRequested) {
                        isIdle = true;
                        return;
                    }
                    isIdle = false;
                    itemGeneration = generation;
                    item = queued.remove(0);
                }

                boolean loaded;
                try {
                    doLoad(item);
                    loaded = true;
                } catch (Throwable ignored) {
                    loaded = false;
                }
                synchronized (lock) {
                    if (!cancelRequested && loaded && itemGeneration == generation) {
                        completed.add(item);
                    }
                }
            }
        }).start();
    }

    /**
     * on worker thread
     */
    private void doLoad(FileProperties item) {
        if (item.isDirectory) {
            item.numChildren = null;
            FileOps.singleton.listDirectory(item.path, 1, true, null, (action, src, dst, succeeded) -> {
                if (action == FileOps.Action.LIST) {
                    item.numChildren = succeeded ? 0 : null;
                } else if (action == FileOps.Action.MEET) {
                    item.numChildren++;
                }
            });
        } else {
            item.size = FileOps.singleton.stat(item.path).size;
        }
        item.computed = true;
    }

    public void add(List<FileProperties> items) {
        synchronized (lock) {
            if (cancelRequested) {
                return;
            }
            queued.addAll(items);
            if (updateTimerTask == null && !queued.isEmpty()) {
                startTimerTask();
            }
            lock.notifyAll();
        }
    }

    private void startTimerTask() {
        updateTimerTask = new TimerTask() {
            @Override
            public void run() {
                reportUpdated();
                synchronized (lock) {
                    if (completed.isEmpty() && queued.isEmpty() && isIdle) {
                        updateTimerTask.cancel();
                        updateTimerTask = null;
                    }
                }
            }
        };
        updateTimer.schedule(updateTimerTask, UPDATE_INTERVAL, UPDATE_INTERVAL);
    }

    /**
     * on timer thread
     */
    private void reportUpdated() {
        List<FileProperties> nowCompleted;
        synchronized (lock) {
            nowCompleted = new LinkedList<>(completed);
            completed.clear();
        }
        if (!cancelRequested && !nowCompleted.isEmpty() && onUpdated != null) {
            try {
                onUpdated.accept(nowCompleted);
            } catch (Throwable ignored) {
            }
        }
    }

    public void clear() {
        synchronized (lock) {
            generation++;
            queued.clear();
            completed.clear();
        }
    }

    public void cancel() {
        synchronized (lock) {
            cancelRequested = true;
            queued.clear();
            completed.clear();
            lock.notifyAll();
        }
        updateTimer.cancel();
    }

    public interface OnUpdatedListener {
        void accept(List<FileProperties> updated);
    }
}
