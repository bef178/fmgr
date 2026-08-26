package pd.droidapp.fmgr.util;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import pd.droidapp.fmgr.util.FileScanner.State;

import pd.util.FileOps;

public class FileRemover {

    private OnDeleteActionListener onDeleteAction;

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private final FileOps.OnActionListener onAction = (action, src, dst, succeeded) -> {
        if (succeeded == null) {
            return;
        }
        switch (action) {
            case DELETE:
                callback(DeleteAction.DELETE, src, succeeded);
                break;
            default:
                break;
        }
    };

    private void callback(DeleteAction action, String src, boolean succeeded) {
        if (onDeleteAction != null) {
            onDeleteAction.accept(action, src, succeeded);
        }
    }

    public void whenDeleteAction(OnDeleteActionListener onDeleteAction) {
        this.onDeleteAction = onDeleteAction;
    }

    public boolean start(Iterable<String> startFiles, boolean prune) {
        if (!state.compareAndSet(State.IDLE, State.RUNNING)) {
            return false;
        }

        Thread workerThread = new Thread(() -> {
            try {
                for (String s : startFiles) {
                    doRemove(s, prune);
                    if (isCancelled()) {
                        return;
                    }
                    callback(DeleteAction.PROGRESS, s, true);
                }
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

    private void doRemove(String src, boolean prune) {
        if (Files.isDirectory(Paths.get(src), LinkOption.NOFOLLOW_LINKS)) {
            FileOps.singleton.deleteDirectory(src, true, prune, cancelled, onAction);
        } else {
            FileOps.singleton.deleteFile(src, onAction);
        }
    }

    public boolean isRunning() {
        State state = this.state.get();
        return state == State.RUNNING || state == State.CANCELLING;
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

    public interface OnDeleteActionListener {

        void accept(DeleteAction action, String src, boolean succeeded);
    }

    public enum DeleteAction {
        DELETE,
        PROGRESS,
    }
}
