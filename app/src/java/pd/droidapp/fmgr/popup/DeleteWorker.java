package pd.droidapp.fmgr.popup;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import pd.droidapp.fmgr.util.FileProperties;
import pd.util.FileOps;

import static pd.droidapp.fmgr.util.Util.toFileProperties;

class DeleteWorker extends ProcessingWorker {

    private OnUpdatedListener onUpdated;
    private List<FileProperties> removed = new LinkedList<>();
    private int failed = 0;
    private int progressed = 0;
    private final Object lock = new Object();

    private final FileOps.OnActionListener onAction = (action, src, dst, succeeded) -> {
        switch (action) {
            case LIST:
                if (succeeded != null && !succeeded) {
                    accumulate(DeleteAction.REMOVE, src, false);
                }
                break;
            case REMOVE:
                accumulate(DeleteAction.REMOVE, src, succeeded);
                break;
            default:
                break;
        }
    };

    private void accumulate(DeleteAction action, String src, Boolean succeeded) {
        if (succeeded == null) {
            return;
        }
        synchronized (lock) {
            if (succeeded) {
                switch (action) {
                    case REMOVE:
                        removed.add(toFileProperties(src));
                        break;
                    case PROGRESS:
                        progressed++;
                        break;
                    default:
                        break;
                }
            } else {
                failed++;
            }
        }
    }

    public void whenUpdated(OnUpdatedListener onUpdated) {
        this.onUpdated = onUpdated;
    }

    public boolean start(List<FileProperties> srcItems, boolean prune) {
        return start(() -> {
            for (FileProperties item : srcItems) {
                doRemove(item.path, prune);
                if (isCancelled()) {
                    return;
                }
                accumulate(DeleteAction.PROGRESS, item.path, true);
            }
        });
    }

    private void doRemove(String src, boolean prune) {
        if (Files.isDirectory(Paths.get(src), LinkOption.NOFOLLOW_LINKS)) {
            FileOps.singleton.removeDirectory(src, true, prune, cancelRequested, onAction);
        } else {
            FileOps.singleton.removeFile(src, onAction);
        }
    }

    @Override
    protected void reportUpdated() {
        List<FileProperties> nowRemoved;
        int nowFailed;
        int nowProgressed;
        synchronized (lock) {
            nowRemoved = removed;
            removed = new LinkedList<>();
            nowFailed = failed;
            failed = 0;
            nowProgressed = progressed;
            progressed = 0;
        }
        if (onUpdated != null) {
            try {
                onUpdated.accept(nowRemoved, nowFailed, nowProgressed);
            } catch (Throwable ignored) {
            }
        }
    }

    public interface OnUpdatedListener {
        void accept(List<FileProperties> removed, int failed, int progressed);
    }

    private enum DeleteAction {
        REMOVE,
        PROGRESS,
    }
}
