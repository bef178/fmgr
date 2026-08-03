package pd.droidapp.fmgr.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.copySafeReplace;
import static pd.droidapp.fmgr.util.Util.getAlternativeFile;
import static pd.droidapp.fmgr.util.Util.moveSafeReplace;

public class PastePopup {

    private final Context context;
    private final View containerView;
    private final boolean isCopy;
    private final List<File> srcFiles;
    private final File dstRoot;

    // views
    private final View selfView;
    private final PopupWindow selfWindow;
    private final View mainAreaView;
    private final PopupTitleBar titleBar;
    private final TextView resolutionTitleTextView;
    private final RadioGroup resolutionOptionsGroup;
    private final LinearLayout progressArea;
    private final ProgressBar progressBarView;
    private final TextView progressBarTextView;
    private final TextView progressBarSideTextView;
    private final TextView progressSummaryTextView;
    private final Button startButton;
    private final Button abortButton;
    private final Button closeButton;

    // callbacks
    private Consumer<Boolean> onDismissed;

    private Paster paster;
    private boolean everExecuted;

    public PastePopup(View containerView, String op, List<File> srcFiles, File dstRoot) {
        this.context = Objects.requireNonNull(containerView, "containerView").getContext();
        this.containerView = containerView;
        this.isCopy = "copy".equals(op);
        this.dstRoot = dstRoot;
        this.srcFiles = new ArrayList<>(srcFiles);

        selfView = LayoutInflater.from(context).inflate(
                R.layout.paste_popup,
                (ViewGroup) containerView,
                false);
        selfWindow = new PopupWindow(selfView,
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                true);
        mainAreaView = selfView.findViewById(R.id.popup_area);

        titleBar = new PopupTitleBar(mainAreaView.findViewById(R.id.popup_title_bar));
        resolutionTitleTextView = mainAreaView.findViewById(R.id.resolution_title);
        resolutionOptionsGroup = mainAreaView.findViewById(R.id.resolution_options);
        progressArea = mainAreaView.findViewById(R.id.progress_area);
        progressBarView = mainAreaView.findViewById(R.id.progress_bar);
        progressBarTextView = mainAreaView.findViewById(R.id.progress_bar_text);
        progressBarSideTextView = mainAreaView.findViewById(R.id.progress_bar_side_text);
        progressSummaryTextView = mainAreaView.findViewById(R.id.progress_summary);
        startButton = mainAreaView.findViewById(R.id.button_start);
        abortButton = mainAreaView.findViewById(R.id.button_abort);
        closeButton = mainAreaView.findViewById(R.id.button_close);

        initPopupWindow();
        enableClosePopupOnOutsideTouch();
        initPopupTitleBar();
        initConflictResolution();
        initProgress();
        initBottomButtons();
    }

    private void initPopupWindow() {
        selfWindow.setOutsideTouchable(false);
        selfWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        selfWindow.setElevation(24);
        selfWindow.setOnDismissListener(() -> {
            if (paster != null) {
                paster.cancel();
            }
            if (onDismissed != null) {
                onDismissed.accept(everExecuted);
            }
        });
    }

    private void enableClosePopupOnOutsideTouch() {
        selfView.setOnClickListener(v -> {
            if (paster == null) {
                selfWindow.dismiss();
            }
        });
        mainAreaView.setOnClickListener(v -> {});
    }

    private void initPopupTitleBar() {
        titleBar.setTitle(context.getString(
                isCopy ? R.string.copy_x_items : R.string.move_x_items,
                srcFiles.size()));
        titleBar.whenCloseButtonClicked(v -> selfWindow.dismiss());
    }

    private void initConflictResolution() {
        resolutionTitleTextView.setText(R.string.select_resolution);
    }

    private void initProgress() {
        progressBarTextView.setText(context.getString(R.string.paste_progress_text, 0, this.srcFiles.size()));
        progressBarSideTextView.setText(R.string.paste_progress_pending);
    }

    private void initBottomButtons() {
        startButton.setVisibility(View.VISIBLE);
        abortButton.setVisibility(View.GONE);
        closeButton.setVisibility(View.GONE);
        startButton.setOnClickListener(v -> start());
        abortButton.setOnClickListener(v -> abort());
        closeButton.setOnClickListener(v -> selfWindow.dismiss());
    }

    public void whenDismissed(Consumer<Boolean> callback) {
        this.onDismissed = callback;
    }

    public void show() {
        containerView.post(() -> selfWindow.showAtLocation(containerView, Gravity.NO_GRAVITY, 0, 0));
    }

    private void start() {
        everExecuted = true;
        final ConflictResolution resolution = getSelectedResolution();
        final int total = srcFiles.size();

        int shortId;
        if (resolution == ConflictResolution.OVERWRITE) {
            shortId = R.string.resolution_short_overwrite;
        } else if (resolution == ConflictResolution.SKIP_INCOMING) {
            shortId = R.string.resolution_short_skip;
        } else {
            shortId = R.string.resolution_short_rename;
        }
        resolutionTitleTextView.setText(
                context.getString(R.string.on_conflict_x, context.getString(shortId)));
        resolutionOptionsGroup.setVisibility(View.GONE);

        startButton.setVisibility(View.GONE);
        abortButton.setVisibility(View.VISIBLE);
        titleBar.enableCloseButton(false);

        paster = new Paster();
        paster.whenPasteStarted(() -> containerView.post(() -> {
            progressArea.setVisibility(View.VISIBLE);
            progressBarTextView.setText(context.getString(R.string.paste_progress_text, 0, total));
            progressBarView.setProgress(0);
        }));
        paster.whenPasteUpdated((added, overwritten, skipped, failed, currentFile) -> containerView.post(() -> {
            int n = paster.getCurrentProcessingIndex();
            progressBarTextView.setText(context.getString(R.string.paste_progress_text, n, total));
            progressBarView.setProgress(n * 100 / total);
            progressBarSideTextView.setText(currentFile.getAbsolutePath());
        }));
        paster.whenPasteCompleted((added, overwritten, skipped, failed, currentFile) -> containerView.post(() -> {
            if (paster.getAborted()) {
                progressBarSideTextView.setText(R.string.paste_progress_aborted);
            } else {
                progressBarSideTextView.setText(R.string.paste_progress_completed);
            }
            progressSummaryTextView.setText(context.getString(R.string.paste_progress_summary,
                    added, overwritten, skipped, failed));

            abortButton.setVisibility(View.GONE);
            closeButton.setVisibility(View.VISIBLE);
            titleBar.enableCloseButton(true);
        }));
        paster.start(srcFiles, dstRoot, isCopy, resolution);
    }

    private ConflictResolution getSelectedResolution() {
        int selectedId = resolutionOptionsGroup.getCheckedRadioButtonId();
        if (selectedId == R.id.resolution_option_overwrite) {
            return ConflictResolution.OVERWRITE;
        } else if (selectedId == R.id.resolution_option_skip_incoming) {
            return ConflictResolution.SKIP_INCOMING;
        } else if (selectedId == R.id.resolution_option_rename_incoming) {
            return ConflictResolution.RENAME_INCOMING;
        }
        return null;
    }

    private void abort() {
        if (paster != null) {
            paster.cancel();
        }
    }

    enum ConflictResolution {
        OVERWRITE,
        SKIP_INCOMING,
        RENAME_INCOMING
    }

    static class Paster {

        private Runnable onPasteStarted;
        private OnPasteUpdateListener onPasteUpdated;
        private OnPasteUpdateListener onPasteCompleted;
        private Thread pasteThread;

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

        void whenPasteCompleted(OnPasteUpdateListener onPasteCompleted) {
            this.onPasteCompleted = onPasteCompleted;
        }

        int getCurrentProcessingIndex() {
            return currentProcessingIndex.get();
        }

        void start(List<File> srcFiles, File dstDirectory, boolean isCopyAction, ConflictResolution resolution) {
            if (getAborted() || isCompleted()) {
                return;
            }

            pasteThread = new Thread(() -> {
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
                        } else if (resolution == ConflictResolution.OVERWRITE) {
                            if (isSameFile) {
                                // overwrite itself doom to fail
                                failedCount.incrementAndGet();
                            } else if (copySafeReplace(src, dst, aborted)) {
                                overwrittenCount.incrementAndGet();
                            } else {
                                failedCount.incrementAndGet();
                            }
                        } else if (resolution == ConflictResolution.SKIP_INCOMING) {
                            if (isSameFile) {
                                failedCount.incrementAndGet();
                            } else {
                                skippedCount.incrementAndGet();
                            }
                        } else if (resolution == ConflictResolution.RENAME_INCOMING) {
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
                        } else if (resolution == ConflictResolution.OVERWRITE) {
                            if (isSameFile) {
                                failedCount.incrementAndGet();
                            } else if (moveSafeReplace(src, dst, aborted)) {
                                overwrittenCount.incrementAndGet();
                            } else {
                                failedCount.incrementAndGet();
                            }
                        } else if (resolution == ConflictResolution.SKIP_INCOMING) {
                            if (isSameFile) {
                                failedCount.incrementAndGet();
                            } else {
                                skippedCount.incrementAndGet();
                            }
                        } else if (resolution == ConflictResolution.RENAME_INCOMING) {
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

                if (onPasteCompleted != null) {
                    onPasteCompleted.accept(addedCount.get(), overwrittenCount.get(), skippedCount.get(), failedCount.get(), null);
                }
            });
            pasteThread.start();
        }

        void cancel() {
            aborted.set(true);
            if (pasteThread != null) {
                pasteThread.interrupt();
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
}
