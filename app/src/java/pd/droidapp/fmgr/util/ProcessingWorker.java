package pd.droidapp.fmgr.util;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

abstract class ProcessingWorker {

    private final int updateInterval;
    private Timer updateTimer;

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    protected final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    private Runnable onStarted;
    private Runnable onStopped;

    protected ProcessingWorker() {
        this(200);
    }

    protected ProcessingWorker(int updateInterval) {
        this.updateInterval = updateInterval;
    }

    public void whenStarted(Runnable onStarted) {
        this.onStarted = onStarted;
    }

    public void whenStopped(Runnable onStopped) {
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

        if (updateInterval > 0) {
            updateTimer = new Timer();
            updateTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    reportStarted();
                }
            }, 0);
            updateTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    reportUpdated();
                    if (!isRunning()) {
                        updateTimer.cancel();
                        reportStopped();
                    }
                }
            }, updateInterval, updateInterval);
        }
        return true;
    }

    public boolean isRunning() {
        State state = this.state.get();
        return state == State.RUNNING || state == State.CANCELLING;
    }

    public boolean isCompleted() {
        return state.get() == State.COMPLETED;
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

    protected void reportStarted() {
        if (onStarted != null) {
            try {
                onStarted.run();
            } catch (Throwable ignored) {
            }
        }
    }

    protected abstract void reportUpdated();

    protected void reportStopped() {
        if (onStopped != null) {
            try {
                onStopped.run();
            } catch (Throwable ignored) {
            }
        }
    }

    enum State {
        IDLE, RUNNING, CANCELLING, CANCELLED, COMPLETED, FAILED
    }
}
