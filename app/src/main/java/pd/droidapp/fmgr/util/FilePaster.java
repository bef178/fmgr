package pd.droidapp.fmgr.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import pd.util.FileOps;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.getAlternativeFile;

public class FilePaster {

    private OnPasteActionListener onPasteAction;

    private final FileOps.OnActionListener onAction = (action, src, dst, succeeded) -> {
        if (succeeded == null) {
            return;
        }
        switch (action) {
            case DELETE:
                callback(PasteAction.DELETE, src, dst, succeeded);
                break;
            case COPY:
                callback(PasteAction.ADD, src, dst, succeeded);
                break;
            case RENAME:
                callback(PasteAction.RENAME, src, dst, succeeded);
                break;
        }
    };

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile Thread workerThread;

    public void whenPasteAction(OnPasteActionListener onPasteAction) {
        this.onPasteAction = onPasteAction;
    }

    public boolean start(boolean isCopy, Collection<String> srcs, String dstDirectory, ConflictResolution resolution, boolean mergeDirectories) {
        if (!started.compareAndSet(false, true)) {
            return false;
        }

        workerThread = new Thread(() -> {
            for (String s : srcs) {
                Path src = Paths.get(s);
                Path dst = Paths.get(dstDirectory, PathOps.singleton.basename(s));
                if (isCopy) {
                    doCopy(src, dst, resolution, mergeDirectories);
                } else {
                    doCut(src, dst, resolution, mergeDirectories);
                }
                if (cancelled.get()) {
                    return;
                }
                callback(PasteAction.PROGRESS, src.toString(), dst.toString(), true);
            }
        });
        workerThread.start();
        return true;
    }

    private void doCopy(Path src, Path dst, ConflictResolution resolution, boolean mergeDirectories) {
        if (cancelled.get()) {
            return;
        }

        if (!Files.exists(dst, LinkOption.NOFOLLOW_LINKS)) {
            cp(src, dst);
            return;
        }

        boolean srcIsDirectory = Files.isDirectory(src, LinkOption.NOFOLLOW_LINKS);
        boolean dstIsDirectory = Files.isDirectory(dst, LinkOption.NOFOLLOW_LINKS);

        if (srcIsDirectory && dstIsDirectory && mergeDirectories) {
            copyMergeDirectory(src, dst, resolution);
            return;
        }

        switch (resolution) {
            case OVERWRITE:
                copyOverwriteExisting(src, dst);
                break;
            case RENAME_INCOMING:
                copyRenameIncoming(src, dst);
                break;
            case SKIP_INCOMING:
                callback(PasteAction.SKIP, src.toString(), dst.toString(), true);
                break;
            default:
                break;
        }
    }

    // `dst` must be a directory
    private void copyMergeDirectory(Path src, Path dst, ConflictResolution resolution) {
        List<String> children = FileOps.singleton.listDirectory(src.toString(), 1, cancelled, onAction);
        if (children == null) {
            return;
        }
        for (String child : children) {
            if (cancelled.get()) {
                return;
            }
            Path childDst = dst.resolve(PathOps.singleton.basename(child));
            doCopy(Paths.get(child), childDst, resolution, true);
        }
        // since `dst` already exists, `src` marks skipped
        callback(PasteAction.SKIP, src.toString(), dst.toString(), true);
    }

    private void copyOverwriteExisting(Path src, Path dst) {
        if (isSamePath(src, dst)) {
            callback(PasteAction.SKIP, src.toString(), dst.toString(), true);
            return;
        }

        Path dstParent = dst.getParent();
        String dstBasename = dst.getFileName().toString();

        Path tmp = getAlternativeFile(dstParent, ".tmp_src_" + dstBasename);
        if (!cp(src, tmp)) {
            return;
        }

        Path bak = getAlternativeFile(dstParent, ".tmp_dst_" + dstBasename);
        if (!mv(dst, bak)) {
            rm(tmp);
            return;
        }

        if (!mv(tmp, dst)) {
            mv(bak, dst); // rollback
            return;
        }

        rm(bak);
    }

    private void copyRenameIncoming(Path src, Path dst) {
        Path parent = dst.getParent();
        String dstName = dst.getFileName().toString();

        Path tmp = getAlternativeFile(parent, ".tmp_" + dstName);
        if (!cp(src, tmp)) {
            return;
        }

        if (!mv(tmp, getAlternativeFile(parent, dstName))) {
            rm(tmp);
        }
    }

    private void callback(PasteAction action, String from, String to, boolean succeeded) {
        if (onPasteAction != null) {
            onPasteAction.accept(action, from, to, succeeded);
        }
    }

    // `dst` must not exist
    private boolean cp(Path src, Path dst) {
        if (Files.isDirectory(src, LinkOption.NOFOLLOW_LINKS)) {
            return FileOps.singleton.copyDirectory(src.toString(), dst.toString(), cancelled, onAction);
        }
        return FileOps.singleton.copyFile(src.toString(), dst.toString(), cancelled, onAction);
    }

    // `dst` must not exist
    private boolean mv(Path src, Path dst) {
        boolean renameSucceeded = false;
        boolean fallback = false;
        try {
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
            renameSucceeded = true;
        } catch (AtomicMoveNotSupportedException ignored) {
            fallback = true;
        } catch (IOException ignored) {
        }
        callback(PasteAction.RENAME, src.toString(), dst.toString(), renameSucceeded);
        if (renameSucceeded) {
            return true;
        } else if (!fallback) {
            return false;
        } else {
            // cross-device: fall back to copy + delete
            return cp(src, dst) && rm(src);
        }
    }

    private boolean rm(Path path) {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return FileOps.singleton.deleteDirectory(path.toString(), true, false, cancelled, onAction);
        }
        return FileOps.singleton.deleteFile(path.toString(), onAction);
    }

    private boolean isSamePath(Path p1, Path p2) {
        return p1.toAbsolutePath().normalize().equals(p2.toAbsolutePath().normalize());
    }

    private void doCut(Path src, Path dst, ConflictResolution resolution, boolean mergeDirectories) {
        if (cancelled.get()) {
            return;
        }

        if (isSamePath(src, dst)) {
            callback(PasteAction.SKIP, src.toString(), dst.toString(), true);
            return;
        }

        if (!Files.exists(dst, LinkOption.NOFOLLOW_LINKS)) {
            mv(src, dst);
            return;
        }

        boolean srcIsDirectory = Files.isDirectory(src, LinkOption.NOFOLLOW_LINKS);
        boolean dstIsDirectory = Files.isDirectory(dst, LinkOption.NOFOLLOW_LINKS);

        if (srcIsDirectory && dstIsDirectory && mergeDirectories) {
            cutMergeDirectory(src, dst, resolution);
            return;
        }

        switch (resolution) {
            case OVERWRITE:
                cutOverwriteExisting(src, dst);
                break;
            case RENAME_INCOMING:
                mv(src, getAlternativeFile(dst.getParent(), dst.getFileName().toString()));
                break;
            case SKIP_INCOMING:
                callback(PasteAction.SKIP, src.toString(), dst.toString(), true);
                break;
            default:
                break;
        }
    }

    private void cutOverwriteExisting(Path src, Path dst) {
        Path dstParent = dst.getParent();
        String dstBasename = dst.getFileName().toString();

        Path bak = getAlternativeFile(dstParent, ".tmp_dst_" + dstBasename);
        if (!mv(dst, bak)) {
            return;
        }

        if (!mv(src, dst)) {
            mv(bak, dst); // rollback
            return;
        }

        rm(bak);
    }

    private void cutMergeDirectory(Path src, Path dst, ConflictResolution resolution) {
        List<String> children = FileOps.singleton.listDirectory(src.toString(), 1, cancelled, onAction);
        if (children == null) {
            return;
        }
        for (String child : children) {
            if (cancelled.get()) {
                return;
            }
            Path childDst = dst.resolve(PathOps.singleton.basename(child));
            doCut(Paths.get(child), childDst, resolution, true);
        }
        // remove src only if empty: skipped/failed children must stay
        FileOps.singleton.deleteDirectory(src.toString(), false, false, cancelled, onAction);
    }

    public boolean isRunning() {
        return workerThread != null && workerThread.isAlive();
    }

    public boolean isCompleted() {
        return started.get() && !cancelled.get() && !isRunning();
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

    public interface OnPasteActionListener {

        void accept(PasteAction action, String src, String dst, boolean succeeded);
    }

    public enum PasteAction {
        ADD,
        DELETE,
        RENAME,
        SKIP,
        PROGRESS,
    }

    public enum ConflictResolution {
        OVERWRITE,
        RENAME_INCOMING,
        SKIP_INCOMING
    }
}
