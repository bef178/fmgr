package pd.droidapp.fmgr.repository;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The actual work is delegated to a background worker thread.
 * The reports are all on a reporter thread.
 * One-way: once stopped, the worker cannot be restarted.
 */
public abstract class ProcessingWorker {

    private final int updateInterval;
    private final AtomicReference<ScheduledFuture<?>> updateTick = new AtomicReference<>();

    private final AtomicReference<Status> status = new AtomicReference<>(Status.IDLE);
    protected final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    private Runnable onStartedCallback;
    private final List<Consumer<StopReason>> onStoppedCallbacks = new LinkedList<>();
    private final Object stopLock = new Object();

    protected ProcessingWorker() {
        this(200);
    }

    protected ProcessingWorker(int updateInterval) {
        if (updateInterval <= 0) {
            throw new IllegalArgumentException("E: expected positive `updateInterval`, actual: " + updateInterval);
        }
        this.updateInterval = updateInterval;
    }

    public void whenStarted(Runnable onStarted) {
        this.onStartedCallback = onStarted;
    }

    /**
     * Return `true` iff the callback is registered and will be called after `start`
     */
    public boolean whenStopped(Consumer<StopReason> onStopped) {
        synchronized (stopLock) {
            if (status.get() == Status.STOPPED) {
                return false;
            }
            this.onStoppedCallbacks.add(onStopped);
            return true;
        }
    }

    protected boolean start(Runnable work) {
        if (!status.compareAndSet(Status.IDLE, Status.RUNNING)) {
            return false;
        }

        WorkerPool.post(() -> {
            try {
                work.run();
            } catch (Throwable ignored) {
                status.compareAndSet(Status.RUNNING, Status.FAILED);
            } finally {
                status.compareAndSet(Status.RUNNING, Status.COMPLETED);
                status.compareAndSet(Status.CANCELLING, Status.CANCELLED);
            }
        });
        startReporting();
        return true;
    }

    private void startReporting() {
        ReporterPool.post(() -> {
            try {
                reportStarted();
            } catch (Throwable ignored) {
            }
        });
        updateTick.set(ReporterPool.schedule(this::tick, updateInterval));
    }

    private void tick() {
        boolean working = isWorking();
        try {
            reportUpdated();
        } catch (Throwable ignored) {
        }
        if (working) {
            return;
        }
        updateTick.get().cancel(false);
        StopReason reason;
        switch (status.get()) {
            case CANCELLED:
                reason = StopReason.CANCELLED;
                break;
            case COMPLETED:
                reason = StopReason.COMPLETED;
                break;
            case FAILED:
            default:
                reason = StopReason.FAILED;
                break;
        }
        try {
            reportStopped(reason);
        } catch (Throwable ignored) {
        }
    }

    public boolean isWorking() {
        Status status = this.status.get();
        return status == Status.RUNNING || status == Status.CANCELLING;
    }

    public void cancel() {
        while (true) {
            Status current = status.get();
            if (current == Status.RUNNING) {
                if (status.compareAndSet(Status.RUNNING, Status.CANCELLING)) {
                    cancelRequested.set(true);
                    return;
                }
            } else {
                return;
            }
        }
    }

    public boolean isCancelled() {
        return cancelRequested.get();
    }

    protected void reportStarted() {
        if (onStartedCallback != null) {
            onStartedCallback.run();
        }
    }

    protected abstract void reportUpdated();

    protected void reportStopped(StopReason reason) {
        List<Consumer<StopReason>> callbacks;
        synchronized (stopLock) {
            callbacks = new LinkedList<>(onStoppedCallbacks);
            onStoppedCallbacks.clear();
            status.set(Status.STOPPED);
        }
        for (Consumer<StopReason> callback : callbacks) {
            try {
                callback.accept(reason);
            } catch (Throwable ignored) {
            }
        }
    }

    private enum Status {
        IDLE, RUNNING, CANCELLING, CANCELLED, COMPLETED, FAILED, STOPPED
    }

    public enum StopReason {
        CANCELLED, COMPLETED, FAILED
    }
}
