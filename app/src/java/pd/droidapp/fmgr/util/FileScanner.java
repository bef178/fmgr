package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import pd.util.FileOps;

public class FileScanner {

    private final int maxDepth;

    private OnScanActionListener onScanAction;

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public FileScanner() {
        this.maxDepth = 32;
    }

    public void whenScanAction(OnScanActionListener onScanAction) {
        this.onScanAction = onScanAction;
    }

    private void callback(ScanAction action, File file) {
        if (onScanAction != null) {
            onScanAction.accept(action, file);
        }
    }

    public boolean start(File startDirectory) {
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

    private void doScan(File startDirectory) {
        FileOps.singleton.listDirectory(startDirectory.getPath(), maxDepth, cancelled, (action, src, dst, succeeded) -> {
            callback(src.endsWith("/") ? ScanAction.DIRECTORY : ScanAction.FILE, new File(src));
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

    public interface OnScanActionListener {

        void accept(ScanAction action, File file);
    }

    public enum ScanAction {
        FILE,
        DIRECTORY,
    }
}
