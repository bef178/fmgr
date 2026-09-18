package pd.droidapp.fmgr.popup;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import pd.droidapp.fmgr.util.FileProperties;
import pd.util.FileOps;

import static java.util.AbstractMap.SimpleEntry;
import static pd.droidapp.fmgr.util.Util.toFileProperties;

class DeleteWorker extends ProcessingWorker {

    private OnUpdatedListener onUpdated;
    private List<FileProperties> removed = new LinkedList<>();
    private int failed = 0;
    private List<Map.Entry<String, Boolean>> progressed = new LinkedList<>();
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
        synchronized (lock) {
            switch (action) {
                case REMOVE:
                    if (succeeded != null) {
                        if (succeeded) {
                            removed.add(toFileProperties(src));
                        } else {
                            failed++;
                        }
                    }
                    break;
                case PROGRESS:
                    progressed.add(new SimpleEntry<>(src, succeeded));
                    break;
                default:
                    break;
            }
        }
    }

    public void whenUpdated(OnUpdatedListener onUpdated) {
        this.onUpdated = onUpdated;
    }

    public boolean start(List<FileProperties> srcItems, boolean prune) {
        return start(() -> {
            for (FileProperties item : srcItems) {
                String src = item.path;
                boolean succeeded;
                if (Files.isDirectory(Paths.get(src), LinkOption.NOFOLLOW_LINKS)) {
                    succeeded = FileOps.singleton.removeDirectory(src, true, prune, cancelRequested, onAction);
                } else {
                    succeeded = FileOps.singleton.removeFile(src, onAction);
                }
                if (!succeeded && isCancelled()) {
                    accumulate(DeleteAction.PROGRESS, src, null);
                    break;
                }
                accumulate(DeleteAction.PROGRESS, src, succeeded);
            }
        });
    }

    @Override
    protected void reportUpdated() {
        List<FileProperties> nowRemoved;
        int nowFailed;
        List<Map.Entry<String, Boolean>> nowProgressed;
        synchronized (lock) {
            nowRemoved = removed;
            removed = new LinkedList<>();
            nowFailed = failed;
            nowProgressed = progressed;
            progressed = new LinkedList<>();
            failed = 0;
        }
        if (onUpdated != null) {
            try {
                onUpdated.accept(nowRemoved, nowFailed, nowProgressed);
            } catch (Throwable ignored) {
            }
        }
    }

    public interface OnUpdatedListener {
        void accept(List<FileProperties> removed, int failed, List<Map.Entry<String, Boolean>> progressed);
    }

    private enum DeleteAction {
        REMOVE,
        PROGRESS,
    }
}
