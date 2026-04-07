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
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.copySafeReplace;
import static pd.droidapp.fmgr.util.Util.getAlternativeFile;
import static pd.droidapp.fmgr.util.Util.moveSafeReplace;

public class PastePopup {

    private final Context context;
    private final View containerView;
    private final File dstRoot;
    private final List<File> srcFiles;
    private Runnable onCompleted;

    private final PopupWindow popupWindow;
    private final TextView resolutionTitleTextView;
    private final RadioGroup resolutionOptionsGroup;
    private final TextView progressCountTextView;
    private final ProgressBar progressView;
    private final TextView progressCurrentTextView;
    private final TextView progressSummary;
    private final Button startButton;
    private final Button abortButton;
    private final Button closeButton;
    private final ImageButton actionClose;

    private Paster paster;

    public PastePopup(Context context, View containerView, String op, List<File> srcFiles, File dstRoot) {
        this.context = context;
        this.containerView = containerView;
        this.dstRoot = dstRoot;
        this.srcFiles = new ArrayList<>(srcFiles);
        boolean isCopyAction = "copy".equals(op);

        View popupView = LayoutInflater.from(context).inflate(
                R.layout.paste_popup,
                containerView != null ? (ViewGroup) containerView : null,
                false);

        TextView titleTextView = popupView.findViewById(R.id.popup_title);
        TextView detectedTextView = popupView.findViewById(R.id.detected_text);
        resolutionTitleTextView = popupView.findViewById(R.id.resolution_title);
        resolutionOptionsGroup = popupView.findViewById(R.id.resolution_options);
        progressCountTextView = popupView.findViewById(R.id.progress_count);
        progressView = popupView.findViewById(R.id.progress_view);
        progressCurrentTextView = popupView.findViewById(R.id.progress_current);
        progressSummary = popupView.findViewById(R.id.progress_summary);
        startButton = popupView.findViewById(R.id.button_start);
        abortButton = popupView.findViewById(R.id.button_abort);
        closeButton = popupView.findViewById(R.id.button_close);
        actionClose = popupView.findViewById(R.id.action_close);

        startButton.setVisibility(View.VISIBLE);
        abortButton.setVisibility(View.GONE);
        closeButton.setVisibility(View.GONE);
        actionClose.setEnabled(true);

        titleTextView.setText(isCopyAction
                ? R.string.paste_from_copy
                : R.string.paste_from_cut);

        detectedTextView.setText(context.getString(R.string.files_detected, srcFiles.size(), detectConflicts()));
        detectedTextView.setEnabled(false);

        resolutionTitleTextView.setText(R.string.select_conflict_resolution);

        progressCountTextView.setText(context.getString(R.string.paste_progress_count, 0, this.srcFiles.size()));
        progressCurrentTextView.setText(R.string.paste_progress_pending);

        actionClose.setOnClickListener(v -> dismiss());
        startButton.setOnClickListener(v -> start(isCopyAction));
        abortButton.setOnClickListener(v -> abort());
        closeButton.setOnClickListener(v -> dismiss());

        popupView.setOnClickListener(v -> {
            if (paster == null) {
                dismiss();
            }
        });

        View popupArea = popupView.findViewById(R.id.popup_area);
        popupArea.setOnClickListener(v -> {
            // dummy but prevent dismiss when clicking inside
        });

        popupWindow = new PopupWindow(popupView,
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                true);
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(24);
    }

    public void whenCompleted(Runnable callback) {
        this.onCompleted = callback;
    }

    public void show() {
        if (containerView != null) {
            containerView.post(() -> popupWindow.showAtLocation(containerView, Gravity.NO_GRAVITY, 0, 0));
        }
    }

    public void dismiss() {
        if (paster != null && !paster.isCompleted()) {
            abort();
            return;
        }
        if (onCompleted != null) {
            onCompleted.run();
        }
        popupWindow.dismiss();
    }

    private int detectConflicts() {
        File[] dstFiles = dstRoot.listFiles();
        if (dstFiles == null) {
            return 0;
        }

        Set<String> dstNames = new HashSet<>();
        for (File file : dstFiles) {
            dstNames.add(file.getName());
        }
        int n = 0;
        for (File src : srcFiles) {
            if (dstNames.contains(src.getName())) {
                n++;
            }
        }
        return n;
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

    private void start(boolean isCopyAction) {
        resolutionTitleTextView.setEnabled(false);
        for (int i = 0; i < resolutionOptionsGroup.getChildCount(); i++) {
            resolutionOptionsGroup.getChildAt(i).setEnabled(false);
        }

        final ConflictResolution resolution = getSelectedResolution();
        final int total = srcFiles.size();

        startButton.setVisibility(View.GONE);
        abortButton.setVisibility(View.VISIBLE);
        actionClose.setEnabled(false);

        paster = new Paster();
        paster.whenPasteStarted(() -> containerView.post(() -> {
            progressCountTextView.setText(context.getString(R.string.paste_progress_count, 0, total));
            progressView.setProgress(0);
        }));
        paster.whenPasteUpdated((added, overwritten, skipped, failed, currentFile) -> containerView.post(() -> {
            int n = paster.getCurrentProcessingIndex();
            progressCountTextView.setText(context.getString(R.string.paste_progress_count, n, total));
            progressView.setProgress(n * 100 / total);
            progressCurrentTextView.setText(currentFile.getAbsolutePath());
        }));
        paster.whenPasteCompleted((added, overwritten, skipped, failed, currentFile) -> containerView.post(() -> {
            if (paster.getAborted()) {
                progressCurrentTextView.setText(R.string.paste_progress_aborted);
            } else {
                progressCurrentTextView.setText(R.string.paste_progress_completed);
            }
            progressSummary.setText(context.getString(R.string.paste_progress_summary,
                    added, overwritten, skipped, failed));

            abortButton.setVisibility(View.GONE);
            closeButton.setVisibility(View.VISIBLE);
            actionClose.setEnabled(true);
        }));
        paster.start(srcFiles, dstRoot, isCopyAction, resolution);
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
