package pd.droidapp.fmgr.repository;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import pd.droidapp.fmgr.fileheader.FileMimeUtil;
import pd.droidapp.fmgr.util.FileProperties;
import pd.util.FileOps;

public class PropertiesLoader {

    private static final int UPDATE_INTERVAL = 200;

    private volatile OnUpdatedListener onUpdated;

    private volatile boolean cancelRequested;
    private long generation; // generation-based invalidation
    private int pending;
    private final List<FileProperties> completed = new LinkedList<>();
    private final Object lock = new Object();

    private ScheduledFuture<?> updateTick;

    public void whenUpdated(OnUpdatedListener onUpdated) {
        this.onUpdated = onUpdated;
    }

    public void add(List<FileProperties> items) {
        if (items.isEmpty()) {
            return;
        }
        long markedGeneration;
        synchronized (lock) {
            if (cancelRequested) {
                return;
            }
            markedGeneration = generation;
            pending++;
            if (updateTick == null) {
                startTick();
            }
        }
        WorkerPool.post(() -> {
            try {
                load(items, markedGeneration);
            } finally {
                synchronized (lock) {
                    pending--;
                }
            }
        });
    }

    private void load(List<FileProperties> items, long markedGeneration) {
        for (FileProperties item : items) {
            synchronized (lock) {
                if (cancelRequested || markedGeneration != generation) {
                    return;
                }
            }

            boolean loaded;
            try {
                item.computed = false;
                if (item.isDirectory) {
                    item.numChildren = FileOps.singleton.getNumDirectoryEntries(item.path);
                } else {
                    item.size = FileOps.singleton.stat(item.path).size;
                    item.mimeType = FileMimeUtil.mimeByContent(item.path);
                }
                item.computed = true;
                loaded = true;
            } catch (Throwable ignored) {
                loaded = false;
            }
            synchronized (lock) {
                if (!cancelRequested && loaded && markedGeneration == generation) {
                    completed.add(item);
                }
            }
        }
    }

    private void startTick() {
        updateTick = ReporterPool.schedule(() -> {
            try {
                reportUpdated();
            } catch (Throwable ignored) {
            }
            synchronized (lock) {
                if (completed.isEmpty() && pending == 0) {
                    updateTick.cancel(false);
                    updateTick = null;
                }
            }
        }, UPDATE_INTERVAL);
    }

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
            completed.clear();
        }
    }

    public void cancel() {
        synchronized (lock) {
            cancelRequested = true;
            completed.clear();
            if (updateTick != null) {
                updateTick.cancel(false);
            }
        }
    }

    public interface OnUpdatedListener {
        void accept(List<FileProperties> updated);
    }
}
