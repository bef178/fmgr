package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.Arrays;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import pd.util.PathOps;

public class FileScanner {

    private final int maxDepth;

    private Consumer<File> onFile;
    private Consumer<File> onDirectory;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile Thread workerThread;

    public FileScanner() {
        this.maxDepth = 32;
    }

    /**
     * Called on each file found during the scan.
     * The file will be accumulated if the callback returns `true`.
     * Follows symlinks.
     */
    public void whenFileReached(Consumer<File> onFile) {
        this.onFile = onFile;
    }

    public void whenDirectoryReached(Consumer<File> onDirectory) {
        this.onDirectory = onDirectory;
    }

    public boolean start(File startDirectory) {
        if (!started.compareAndSet(false, true)) {
            return false;
        }

        workerThread = new Thread(() -> doScan(startDirectory));
        workerThread.start();
        return true;
    }

    private void doScan(File startDirectory) {
        Stack<Frame> stack = new Stack<>();
        stack.push(new Frame(startDirectory, 0));
        while (!cancelled.get() && !stack.isEmpty()) {
            Frame frame = stack.pop();
            File[] children = frame.file.listFiles();
            if (children == null) {
                continue;
            }

            Arrays.sort(children, (a, b) -> PathOps.singleton.compare(a.getPath(), b.getPath()));

            // push directories in reverse so they are visited in sorted order
            for (int i = children.length - 1; i >= 0; i--) {
                if (cancelled.get()) {
                    return;
                }
                File child = children[i];
                if (child.isDirectory() && frame.depth < maxDepth) {
                    if (onDirectory != null) {
                        onDirectory.accept(child);
                    }
                    stack.push(new Frame(child, frame.depth + 1));
                }
            }
            for (File child : children) {
                if (cancelled.get()) {
                    return;
                }
                if (child.isFile()) {
                    if (onFile != null) {
                        onFile.accept(child);
                    }
                }
            }
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
        final int depth;

        Frame(File file, int depth) {
            this.file = file;
            this.depth = depth;
        }
    }
}
