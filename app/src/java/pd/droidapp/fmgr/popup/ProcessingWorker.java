package pd.droidapp.fmgr.popup;

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

    private Runnable onStarted;
    private Consumer<StopReason> onStopped;

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
        this.onStarted = onStarted;
    }

    public void whenStopped(Consumer<StopReason> onStopped) {
        this.onStopped = onStopped;
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
            } else if (current == State.IDLE) {
                if (state.compareAndSet(State.IDLE, State.CANCELLED)) {
                    return;
                }
            } else {
                return;
            }
        }
    }

    public boolean isCancelled() {
        State state = this.state.get();
        return state == State.CANCELLING || state == State.CANCELLED;
    }

    /**
     * Runs on timer thread
     */
    protected void reportStarted() {
        if (onStarted != null) {
            onStarted.run();
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
        if (onStopped != null) {
            onStopped.accept(reason);
        }
    }

    private enum State {
        IDLE, RUNNING, CANCELLING, CANCELLED, COMPLETED, FAILED
    }

    public enum StopReason {
        CANCELLED, COMPLETED, FAILED
    }
}
