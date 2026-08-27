package pd.droidapp.fmgr.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import pd.util.FileOps;

public class FileScanner {

    private final int maxDepth;

    private Consumer<String> onScanAction;

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public FileScanner() {
        this.maxDepth = 32;
    }

    public void whenScanAction(Consumer<String> onScanAction) {
        this.onScanAction = onScanAction;
    }

    public boolean start(String startDirectory) {
        if (!state.compareAndSet(State.IDLE, State.RUNNING)) {
            return false;
        }

        Thread workerThread = new Thread(() -> {
            try {
                doScan(startDirectory);
            } catch (Throwable ignored) {
                state.compareAndSet(State.RUNNING, State.FAILED);
            } finally {
                state.compareAndSet(State.RUNNING, State.COMPLETED);
                state.compareAndSet(State.CANCELLING, State.CANCELLED);
            }
        });
        workerThread.start();
        return true;
    }

    private void doScan(String startDirectory) {
        FileOps.singleton.listDirectory(startDirectory, maxDepth, cancelled, (action, src, dst, succeeded) -> {
            if (onScanAction != null) {
                onScanAction.accept(src);
            }
        });
    }

    public boolean isRunning() {
        State state = this.state.get();
        return state == State.RUNNING || state == State.CANCELLING;
    }

    public boolean isCompleted() {
        return state.get() == State.COMPLETED;
    }

    public void cancel() {
        if (state.compareAndSet(State.RUNNING, State.CANCELLING)) {
            cancelled.set(true);
        }
    }

    public boolean isCancelled() {
        State state = this.state.get();
        return state == State.CANCELLING || state == State.CANCELLED;
    }

    enum State {
        IDLE, RUNNING, CANCELLING, CANCELLED, COMPLETED, FAILED
    }
}
