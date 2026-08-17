package pd.droidapp.fmgr.util;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

import pd.util.FileOps;

public class FileRemover {

    private OnDeleteActionListener onDeleteAction;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile Thread workerThread;

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
        if (!started.compareAndSet(false, true)) {
            return false;
        }

        workerThread = new Thread(() -> {
            for (String s : startFiles) {
                doRemove(s, prune);
                if (cancelled.get()) {
                    return;
                }
                callback(DeleteAction.PROGRESS, s, true);
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
        return workerThread != null && workerThread.isAlive();
    }

    public void cancel() {
        if (!started.get() || !isRunning()) {
            return;
        }
        cancelled.set(true);
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public interface OnDeleteActionListener {

        void accept(DeleteAction action, String src, boolean succeeded);
    }

    public enum DeleteAction {
        DELETE,
        PROGRESS,
    }
}
