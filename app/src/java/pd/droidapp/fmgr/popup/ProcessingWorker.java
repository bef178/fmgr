package pd.droidapp.fmgr.popup;

import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The actual work is delegated to a background worker thread.
 * The reports are all on a timer thread.
 * One-way: once stopped, the worker cannot be restarted.
 */
abstract class ProcessingWorker {

    private final int updateInterval;
    private Timer updateTimer;

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
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
            if (state.get() == State.STOPPED) {
                return false;
            }
            this.onStoppedCallbacks.add(onStopped);
            return true;
        }
    }

    protected boolean start(Runnable work) {
        if (!state.compareAndSet(State.IDLE, State.RUNNING)) {
            return false;
        }

        Thread workerThread = new Thread(() -> {
            try {
                work.run();
            } catch (Throwable ignored) {
                state.compareAndSet(State.RUNNING, State.FAILED);
            } finally {
                state.compareAndSet(State.RUNNING, State.COMPLETED);
                state.compareAndSet(State.CANCELLING, State.CANCELLED);
            }
        });
        workerThread.start();
        startTimer();
        return true;
    }

    protected void startTimer() {
        updateTimer = new Timer();
        updateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    reportStarted();
                } catch (Throwable ignored) {
                }
            }
        }, 0);
        updateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    reportUpdated();
                } catch (Throwable ignored) {
                }
                if (!isWorking()) {
                    updateTimer.cancel();
                    StopReason reason;
                    switch (state.get()) {
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
            }
        }, updateInterval, updateInterval);
    }

    public boolean isWorking() {
        State state = this.state.get();
        return state == State.RUNNING || state == State.CANCELLING;
    }

    public void cancel() {
        while (true) {
            State current = state.get();
            if (current == State.RUNNING) {
                if (state.compareAndSet(State.RUNNING, State.CANCELLING)) {
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

    /**
     * Runs on timer thread
     */
    protected void reportStarted() {
        if (onStartedCallback != null) {
            onStartedCallback.run();
        }
    }

    /**
     * Runs on timer thread
     */
    protected abstract void reportUpdated();

    /**
     * Runs on timer thread
     */
    protected void reportStopped(StopReason reason) {
        List<Consumer<StopReason>> callbacks;
        synchronized (stopLock) {
            callbacks = new LinkedList<>(onStoppedCallbacks);
            onStoppedCallbacks.clear();
            state.set(State.STOPPED);
        }
        for (Consumer<StopReason> callback : callbacks) {
            try {
                callback.accept(reason);
            } catch (Throwable ignored) {
            }
        }
    }

    private enum State {
        IDLE, RUNNING, CANCELLING, CANCELLED, COMPLETED, FAILED, STOPPED
    }

    public enum StopReason {
        CANCELLED, COMPLETED, FAILED
    }
}
