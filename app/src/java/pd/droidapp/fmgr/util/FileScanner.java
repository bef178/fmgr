package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.Arrays;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import pd.util.PathOps;

public class FileScanner {

    private final int maxDepth;

    private Consumer<File> onFile;
    private Consumer<File> onDirectory;

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private volatile Thread workerThread;

    public FileScanner() {
        this.maxDepth = 32;
    }

    public void whenFileReached(Consumer<File> onFile) {
        this.onFile = onFile;
    }

    public void whenDirectoryReached(Consumer<File> onDirectory) {
        this.onDirectory = onDirectory;
    }

    public boolean start(File startDirectory) {
        if (!state.compareAndSet(State.IDLE, State.RUNNING)) {
            return false;
        }

        workerThread = new Thread(() -> {
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
        Stack<Frame> stack = new Stack<>();
        stack.push(new Frame(startDirectory, 0));
        while (state.get() != State.CANCELLING && !stack.isEmpty()) {
            Frame frame = stack.pop();
            File[] children = frame.file.listFiles();
            if (children == null) {
                continue;
            }

            Arrays.sort(children, (a, b) -> PathOps.singleton.compare(a.getPath(), b.getPath()));

            // push directories in reverse so they are visited in sorted order
            for (int i = children.length - 1; i >= 0; i--) {
                if (state.get() == State.CANCELLING) {
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
                if (state.get() == State.CANCELLING) {
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
        State state = this.state.get();
        return state == State.RUNNING || state == State.CANCELLING;
    }

    public boolean isCompleted() {
        return state.get() == State.COMPLETED;
    }

    public void cancel() {
        if (state.compareAndSet(State.RUNNING, State.CANCELLING) && workerThread != null) {
            workerThread.interrupt();
        }
    }

    public boolean isCancelled() {
        State state = this.state.get();
        return state == State.CANCELLING || state == State.CANCELLED;
    }

    private static class Frame {

        final File file;
        final int depth;

        Frame(File file, int depth) {
            this.file = file;
            this.depth = depth;
        }
    }

    enum State {
        IDLE, RUNNING, CANCELLING, CANCELLED, COMPLETED, FAILED
    }
}
