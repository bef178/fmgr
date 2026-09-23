package pd.droidapp.fmgr.popup;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import pd.droidapp.fmgr.util.FileProperties;
import pd.util.FileOps;
import pd.util.PathOps;

import static java.util.AbstractMap.SimpleEntry;
import static pd.droidapp.fmgr.util.Util.toFileProperties;

class PasteWorker extends ProcessingWorker {

    private OnUpdatedListener onUpdated;

    private List<FileProperties> added = new LinkedList<>();
    private List<FileProperties> removed = new LinkedList<>();
    private List<Map.Entry<FileProperties, FileProperties>> moved = new LinkedList<>();
    private int failed = 0;
    private List<Map.Entry<String, Boolean>> progressed = new LinkedList<>();
    private final Object lock = new Object();

    private final FileOps.OnActionListener onAction = (action, src, dst, succeeded) -> {
        switch (action) {
            case LIST:
                // an unreadable directory aborts the copy without further CREATE events
                if (succeeded != null && !succeeded) {
                    accumulate(PasteAction.REMOVE, src, null, false);
                }
                break;
            case CREATE:
                accumulate(PasteAction.ADD, src, dst, succeeded);
                break;
            case REMOVE:
                accumulate(PasteAction.REMOVE, src, dst, succeeded);
                break;
            case MOVE:
                accumulate(PasteAction.MOVE, src, dst, succeeded);
                break;
            default:
                break;
        }
    };

    private void accumulate(PasteAction action, String src, String dst, Boolean succeeded) {
        synchronized (lock) {
            switch (action) {
                case ADD:
                    if (succeeded != null) {
                        if (succeeded) {
                            added.add(toFileProperties(dst));
                        } else {
                            failed++;
                        }
                    }
                    break;
                case REMOVE:
                    if (succeeded != null) {
                        if (succeeded) {
                            removed.add(toFileProperties(src));
                        } else {
                            failed++;
                        }
                    }
                    break;
                case MOVE:
                    if (succeeded != null) {
                        if (succeeded) {
                            moved.add(new SimpleEntry<>(toFileProperties(src), toFileProperties(dst)));
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

    public boolean startCopy(List<FileProperties> srcItems, String dstDirectory, ConflictResolution resolution, boolean mergeDirectories) {
        return start(() -> {
            for (FileProperties item : srcItems) {
                String dstPath = PathOps.singleton.join(dstDirectory, PathOps.singleton.basename(item.path));
                Path src = Paths.get(item.path);
                Path dst = Paths.get(dstPath);
                boolean succeeded = doCopy(src, dst, resolution, mergeDirectories);
                if (!succeeded && isCancelled()) {
                    accumulate(PasteAction.PROGRESS, item.path, dstPath, null);
                    break;
                }
                accumulate(PasteAction.PROGRESS, item.path, dstPath, succeeded);
            }
        });
    }

    private boolean doCopy(Path src, Path dst, ConflictResolution resolution, boolean mergeDirectories) {
        if (isCancelled()) {
            return false;
        }

        if (!Files.exists(dst, LinkOption.NOFOLLOW_LINKS)) {
            return cp(src, dst);
        }

        boolean srcIsDirectory = Files.isDirectory(src, LinkOption.NOFOLLOW_LINKS);
        boolean dstIsDirectory = Files.isDirectory(dst, LinkOption.NOFOLLOW_LINKS);

        if (srcIsDirectory && dstIsDirectory && mergeDirectories) {
            return copyMergeDirectory(src, dst, resolution);
        }

        switch (resolution) {
            case OVERWRITE:
                return copyOverwriteExisting(src, dst);
            case RENAME_INCOMING:
                return copyRenameIncoming(src, dst);
            default:
                // SKIP_INCOMING is doomed to success
                return true;
        }
    }

    // `dst` must be a directory
    private boolean copyMergeDirectory(Path src, Path dst, ConflictResolution resolution) {
        List<String> children = listDirectory(src);
        if (children == null) {
            return true;
        }
        for (String child : children) {
            if (isCancelled()) {
                break;
            }
            Path childDst = dst.resolve(PathOps.singleton.basename(child));
            if (!doCopy(Paths.get(child), childDst, resolution, true)) {
                return false;
            }
        }
        return true;
    }

    private List<String> listDirectory(Path src) {
        List<String> children = new LinkedList<>();
        if (!FileOps.singleton.listDirectory(src.toString(), 1, false, cancelRequested,
                (action, from, to, succeeded) -> {
                    if (action == FileOps.Action.MEET) {
                        children.add(from);
                    }
                })) {
            return null;
        }
        return children;
    }

    private boolean copyOverwriteExisting(Path src, Path dst) {
        if (isSamePath(src, dst)) {
            return true;
        }

        String dstBasename = dst.getFileName().toString();

        Path tmp = getAlternativePath(dst.resolveSibling(".tmp_src_" + dstBasename));
        if (!cp(src, tmp)) {
            return false;
        }

        Path bak = getAlternativePath(dst.resolveSibling(".tmp_dst_" + dstBasename));
        if (!mv(dst, bak)) {
            rm(tmp);
            return false;
        }

        if (!mv(tmp, dst)) {
            mv(bak, dst); // rollback
            return false;
        }

        rm(bak);
        return true;
    }

    private boolean copyRenameIncoming(Path src, Path dst) {
        String dstName = dst.getFileName().toString();

        Path tmp = getAlternativePath(dst.resolveSibling(".tmp_" + dstName));
        if (!cp(src, tmp)) {
            return false;
        }

        if (!mv(tmp, getAlternativePath(dst))) {
            rm(tmp);
            return false;
        }
        return true;
    }

    // `dst` must not exist
    private boolean cp(Path src, Path dst) {
        if (Files.isDirectory(src, LinkOption.NOFOLLOW_LINKS)) {
            return FileOps.singleton.copyDirectory(src.toString(), dst.toString(), cancelRequested, onAction);
        }
        return FileOps.singleton.copyFile(src.toString(), dst.toString(), false, cancelRequested, onAction);
    }

    // `dst` must not exist
    private boolean mv(Path src, Path dst) {
        if (FileOps.singleton.move(src.toString(), dst.toString(), onAction)) {
            return true;
        }
        return cp(src, dst) && rm(src);
    }

    private boolean rm(Path path) {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return FileOps.singleton.removeDirectory(path.toString(), true, false, cancelRequested, onAction);
        }
        return FileOps.singleton.removeFile(path.toString(), onAction);
    }

    private boolean isSamePath(Path p1, Path p2) {
        return p1.normalize().equals(p2.normalize());
    }

    private Path getAlternativePath(Path path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return path;
        }

        String pathString = path.toString();
        String extension = PathOps.singleton.extname(pathString);
        String name = PathOps.singleton.basename(pathString, extension);

        int counter = 2;
        Path candidate;
        do {
            candidate = path.resolveSibling(name + " (" + counter + ")" + extension);
            counter++;
        } while (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS));

        return candidate;
    }

    public boolean startCut(List<FileProperties> srcItems, String dstDirectory, ConflictResolution resolution, boolean mergeDirectories) {
        return start(() -> {
            for (FileProperties item : srcItems) {
                String dstPath = PathOps.singleton.join(dstDirectory, PathOps.singleton.basename(item.path));
                Path src = Paths.get(item.path);
                Path dst = Paths.get(dstPath);
                boolean succeeded = doCut(src, dst, resolution, mergeDirectories);
                if (!succeeded && isCancelled()) {
                    accumulate(PasteAction.PROGRESS, item.path, dstPath, null);
                    break;
                }
                accumulate(PasteAction.PROGRESS, item.path, dstPath, succeeded);
            }
        });
    }

    private boolean doCut(Path src, Path dst, ConflictResolution resolution, boolean mergeDirectories) {
        if (isCancelled()) {
            return false;
        }

        if (isSamePath(src, dst)) {
            return false;
        }

        if (!Files.exists(dst, LinkOption.NOFOLLOW_LINKS)) {
            return mv(src, dst);
        }

        boolean srcIsDirectory = Files.isDirectory(src, LinkOption.NOFOLLOW_LINKS);
        boolean dstIsDirectory = Files.isDirectory(dst, LinkOption.NOFOLLOW_LINKS);

        if (srcIsDirectory && dstIsDirectory && mergeDirectories) {
            return cutMergeDirectory(src, dst, resolution);
        }

        switch (resolution) {
            case OVERWRITE:
                return cutOverwriteExisting(src, dst);
            case RENAME_INCOMING:
                return mv(src, getAlternativePath(dst));
            default:
                return true;
        }
    }

    private boolean cutOverwriteExisting(Path src, Path dst) {
        String dstBasename = dst.getFileName().toString();

        Path bak = getAlternativePath(dst.resolveSibling(".tmp_dst_" + dstBasename));
        if (!mv(dst, bak)) {
            return false;
        }

        if (!mv(src, dst)) {
            mv(bak, dst); // rollback
            return false;
        }

        rm(bak);
        return true;
    }

    private boolean cutMergeDirectory(Path srcDirectory, Path dstDirectory, ConflictResolution resolution) {
        List<String> children = listDirectory(srcDirectory);
        if (children == null) {
            return true;
        }
        for (String child : children) {
            if (isCancelled()) {
                return false;
            }
            Path childDst = dstDirectory.resolve(PathOps.singleton.basename(child));
            if (!doCut(Paths.get(child), childDst, resolution, true)) {
                return false;
            }
        }
        // remove srcDirectory iff empty: skipped/failed children must stay
        List<String> remaining = new LinkedList<>();
        if (FileOps.singleton.listDirectory(srcDirectory.toString(), 1, false, cancelRequested,
                (action, s, to, succeeded) -> {
                    if (action == FileOps.Action.MEET) {
                        remaining.add(s);
                    }
                }) && remaining.isEmpty()) {
            FileOps.singleton.removeDirectory(srcDirectory.toString(), false, false, cancelRequested, onAction);
        }
        return true;
    }

    @Override
    protected void reportUpdated() {
        List<FileProperties> nowAdded;
        List<FileProperties> nowRemoved;
        List<Map.Entry<FileProperties, FileProperties>> nowMoved;
        int nowFailed;
        List<Map.Entry<String, Boolean>> nowProgressed;
        synchronized (lock) {
            nowAdded = added;
            added = new LinkedList<>();
            nowRemoved = removed;
            removed = new LinkedList<>();
            nowMoved = moved;
            moved = new LinkedList<>();
            nowFailed = failed;
            failed = 0;
            nowProgressed = progressed;
            progressed = new LinkedList<>();
        }
        if (onUpdated != null) {
            try {
                onUpdated.accept(nowAdded, nowRemoved, nowMoved, nowFailed, nowProgressed);
            } catch (Throwable ignored) {
            }
        }
    }

    public interface OnUpdatedListener {
        void accept(List<FileProperties> added,
                List<FileProperties> removed,
                List<Map.Entry<FileProperties, FileProperties>> moved,
                int failed,
                List<Map.Entry<String, Boolean>> progressed);
    }

    public enum ConflictResolution {
        OVERWRITE,
        RENAME_INCOMING,
        SKIP_INCOMING
    }

    private enum PasteAction {
        ADD,
        REMOVE,
        MOVE,
        PROGRESS,
    }
}
