package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static pd.droidapp.fmgr.util.Util.copySafeReplace;
import static pd.droidapp.fmgr.util.Util.getAlternativeFile;
import static pd.droidapp.fmgr.util.Util.moveSafeReplace;

class FilePasteUpdater {

    private Runnable onPasteStarted;
    private OnPasteUpdateListener onPasteUpdated;
    private OnPasteUpdateListener onPasteStopped;
    private Thread workerThread;

    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final AtomicBoolean aborted = new AtomicBoolean(false);

    private final AtomicInteger currentProcessingIndex = new AtomicInteger(0);
    private final AtomicInteger addedCount = new AtomicInteger(0);
    private final AtomicInteger overwrittenCount = new AtomicInteger(0);
    private final AtomicInteger skippedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);

    void whenPasteStarted(Runnable onPasteStarted) {
        this.onPasteStarted = onPasteStarted;
    }

    void whenPasteUpdated(OnPasteUpdateListener onPasteUpdated) {
        this.onPasteUpdated = onPasteUpdated;
    }

    void whenPasteStopped(OnPasteUpdateListener onPasteStopped) {
        this.onPasteStopped = onPasteStopped;
    }

    int getCurrentProcessingIndex() {
        return currentProcessingIndex.get();
    }

    void start(List<File> srcFiles, File dstDirectory, boolean isCopyAction, PastePopup.ConflictResolution resolution) {
        if (getAborted() || isCompleted()) {
            return;
        }

        workerThread = new Thread(() -> {
            if (onPasteStarted != null) {
                onPasteStarted.run();
            }

            for (File src : srcFiles) {
                if (aborted.get()) {
                    break;
                }

                currentProcessingIndex.incrementAndGet();

                if (onPasteUpdated != null) {
                    onPasteUpdated.accept(addedCount.get(), overwrittenCount.get(), skippedCount.get(), failedCount.get(), src);
                }

                File dst = new File(dstDirectory, src.getName());
                boolean isSameFile = src.getAbsolutePath().equals(dst.getAbsolutePath());
                if (isCopyAction) {
                    if (!dst.exists()) {
                        if (copySafeReplace(src, dst, aborted)) {
                            addedCount.incrementAndGet();
                        } else {
                            failedCount.incrementAndGet();
                        }
                    } else if (resolution == PastePopup.ConflictResolution.OVERWRITE) {
                        if (isSameFile) {
                            // overwrite itself doom to fail
                            failedCount.incrementAndGet();
                        } else if (copySafeReplace(src, dst, aborted)) {
                            overwrittenCount.incrementAndGet();
                        } else {
                            failedCount.incrementAndGet();
                        }
                    } else if (resolution == PastePopup.ConflictResolution.SKIP_INCOMING) {
                        if (isSameFile) {
                            failedCount.incrementAndGet();
                        } else {
                            skippedCount.incrementAndGet();
                        }
                    } else if (resolution == PastePopup.ConflictResolution.RENAME_INCOMING) {
                        if (copySafeReplace(src, getAlternativeFile(dst.getParentFile(), dst.getName()), aborted)) {
                            addedCount.incrementAndGet();
                        } else {
                            failedCount.incrementAndGet();
                        }
                    }
                } else {
                    if (!dst.exists()) {
                        if (moveSafeReplace(src, dst, aborted)) {
                            addedCount.incrementAndGet();
                        } else {
                            failedCount.incrementAndGet();
                        }
                    } else if (resolution == PastePopup.ConflictResolution.OVERWRITE) {
                        if (isSameFile) {
                            failedCount.incrementAndGet();
                        } else if (moveSafeReplace(src, dst, aborted)) {
                            overwrittenCount.incrementAndGet();
                        } else {
                            failedCount.incrementAndGet();
                        }
                    } else if (resolution == PastePopup.ConflictResolution.SKIP_INCOMING) {
                        if (isSameFile) {
                            failedCount.incrementAndGet();
                        } else {
                            skippedCount.incrementAndGet();
                        }
                    } else if (resolution == PastePopup.ConflictResolution.RENAME_INCOMING) {
                        if (isSameFile) {
                            failedCount.incrementAndGet();
                        } else {
                            if (moveSafeReplace(src, getAlternativeFile(dst.getParentFile(), dst.getName()), aborted)) {
                                addedCount.incrementAndGet();
                            } else {
                                failedCount.incrementAndGet();
                            }
                        }
                    }
                }
            }

            if (!aborted.get()) {
                completed.set(true);
            }

            if (onPasteStopped != null) {
                onPasteStopped.accept(addedCount.get(), overwrittenCount.get(), skippedCount.get(), failedCount.get(), null);
            }
        });
        workerThread.start();
    }

    void cancel() {
        aborted.set(true);
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    boolean getAborted() {
        return aborted.get();
    }

    boolean isCompleted() {
        return completed.get();
    }

    interface OnPasteUpdateListener {

        void accept(int added, int overwritten, int skipped, int failed, File current);
    }
}
