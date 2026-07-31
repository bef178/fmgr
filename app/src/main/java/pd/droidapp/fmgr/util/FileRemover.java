package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.Arrays;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import pd.util.PathExtension;

public class FileRemover {

    private BiConsumer<File, Boolean> onFile;
    private BiConsumer<File, Boolean> onDirectory;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile Thread workerThread;

    public void whenFileRemoved(BiConsumer<File, Boolean> onFile) {
        this.onFile = onFile;
    }

    public void whenDirectoryRemoved(BiConsumer<File, Boolean> onDirectory) {
        this.onDirectory = onDirectory;
    }

    public boolean start(Iterable<File> startFiles, File stopDirectory) {
        if (!started.compareAndSet(false, true)) {
            return false;
        }
        workerThread = new Thread(() -> {
            for (File file : startFiles) {
                doRemove(file, stopDirectory);
            }
        });
        workerThread.start();
        return true;
    }

    private void doRemove(File startFile, File stopDirectory) {
        Stack<Frame> stack = new Stack<>();
        stack.push(new Frame(startFile));

        while (!cancelled.get() && !stack.isEmpty()) {
            Frame frame = stack.pop();
            if (frame.reached) {
                removeEmptyDirectory(frame.file);
                continue;
            }
            if (frame.file.isDirectory()) {
                File[] children = frame.file.listFiles();
                if (children == null || children.length == 0) {
                    removeEmptyDirectory(frame.file);
                    continue;
                }

                Arrays.sort(children, (a, b) -> PathExtension.compare(a.getPath(), b.getPath()));

                frame.reached = true;
                stack.push(frame);
                for (int i = children.length - 1; i >= 0; i--) {
                    if (cancelled.get()) {
                        return;
                    }

                    stack.push(new Frame(children[i]));
                }
            } else {
                removeFile(frame.file);
            }
        }

        // remove now-empty parent directories up to stopDirectory
        if (stopDirectory == null || !isDescendantOf(startFile, stopDirectory)) {
            return;
        }
        File parentFile = startFile.getParentFile();
        while (!cancelled.get() && parentFile != null && !parentFile.equals(stopDirectory)) {
            File[] children = parentFile.listFiles();
            if (children != null && children.length > 0) {
                return;
            }
            removeEmptyDirectory(parentFile);
            parentFile = parentFile.getParentFile();
        }
    }

    private static boolean isDescendantOf(File descendant, File ancestor) {
        File parent = descendant.getParentFile();
        while (parent != null) {
            if (parent.equals(ancestor)) {
                return true;
            }
            parent = parent.getParentFile();
        }
        return false;
    }

    private void removeEmptyDirectory(File directory) {
        boolean isFailed = !directory.delete();
        if (onDirectory != null) {
            onDirectory.accept(directory, isFailed);
        }
    }

    private void removeFile(File file) {
        boolean isFailed = !file.delete();
        if (onFile != null) {
            onFile.accept(file, isFailed);
        }
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

    private static class Frame {

        final File file;
        boolean reached;

        Frame(File file) {
            this.file = file;
        }
    }
}
