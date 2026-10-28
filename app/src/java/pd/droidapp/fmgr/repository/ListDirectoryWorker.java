package pd.droidapp.fmgr.repository;

import java.util.LinkedList;
import java.util.List;

import pd.droidapp.fmgr.util.FileProperties;
import pd.util.FileOps;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.toFileProperties;

public class ListDirectoryWorker extends ProcessingWorker {

    private OnUpdatedListener onUpdated;
    private List<FileProperties> listed = new LinkedList<>();
    private final Object lock = new Object();

    public void whenUpdated(OnUpdatedListener onUpdated) {
        this.onUpdated = onUpdated;
    }

    public boolean start(String directory) {
        return start(() -> FileOps.singleton.listDirectory(directory, 1, false, PathOps.singleton::compare, cancelRequested,
                (action, src, dst, succeeded) -> {
                    if (action == FileOps.Action.MEET) {
                        FileProperties item = toFileProperties(src);
                        synchronized (lock) {
                            listed.add(item);
                        }
                    }
                }));
    }

    @Override
    protected void reportUpdated() {
        List<FileProperties> nowListed;
        synchronized (lock) {
            nowListed = listed;
            listed = new LinkedList<>();
        }
        if (onUpdated != null) {
            try {
                onUpdated.accept(nowListed);
            } catch (Throwable ignored) {
            }
        }
    }

    public interface OnUpdatedListener {
        void accept(List<FileProperties> listed);
    }
}
