package pd.droidapp.fmgr.util;

import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.FilePaster.ConflictResolution;

import static pd.droidapp.fmgr.util.Util.getDisplayPath;

public class PastePopup extends ProcessingPopup {

    private final boolean isCopy;
    private final List<File> srcFiles;
    private final File dstDirectory;

    // views
    private final TextView resolutionTitleTextView;
    private final RadioGroup resolutionOptionsGroup;
    private final CheckBox mergeDirectoriesCheckBox;
    private final LinearLayout progressArea;
    private final ProgressBar progressBarView;
    private final TextView progressBarTextView;
    private final TextView progressBarSideTextView;
    private final TextView progressSummaryTextView;

    // callbacks
    private PopupOnDismissedListener onPopupDismissed;

    private FilePasteUpdater paster;

    public PastePopup(View containerView, boolean isCopy, List<File> srcFiles, File dstDirectory) {
        super(containerView, R.layout.paste_popup);
        this.isCopy = isCopy;
        this.dstDirectory = dstDirectory;
        this.srcFiles = new LinkedList<>(srcFiles);

        resolutionTitleTextView = mainAreaView.findViewById(R.id.resolution_title);
        resolutionOptionsGroup = mainAreaView.findViewById(R.id.resolution_options);
        mergeDirectoriesCheckBox = mainAreaView.findViewById(R.id.merge_directories_checkbox);
        progressArea = mainAreaView.findViewById(R.id.progress_area);
        progressBarView = mainAreaView.findViewById(R.id.progress_bar);
        progressBarTextView = mainAreaView.findViewById(R.id.progress_bar_text);
        progressBarSideTextView = mainAreaView.findViewById(R.id.progress_bar_side_text);
        progressSummaryTextView = mainAreaView.findViewById(R.id.progress_summary);

        titleBar.setTitle(context.getString(
                isCopy ? R.string.copy_x_items : R.string.move_x_items,
                srcFiles.size()));

        initConflictResolution();
        initProgress();
    }

    @Override
    protected void initPopupButtons() {
        super.initPopupButtons();
        buttonBar.addButton(R.string.start, () -> true, () -> paster == null, v -> start());
        buttonBar.addButton(R.string.abort, () -> true, this::isProcessing, v -> abort());
        buttonBar.addButton(R.string.close, () -> true, () -> !isProcessing(), v -> selfWindow.dismiss());
    }

    private void initConflictResolution() {
        resolutionTitleTextView.setText(R.string.select_resolution);
    }

    private void initProgress() {
        progressBarTextView.setText(context.getString(R.string.popup_progress_text, 1, srcFiles.size()));
        progressBarSideTextView.setText(R.string.popup_progress_pending);
    }

    @Override
    protected boolean isProcessing() {
        return paster != null && !paster.isStopped();
    }

    @Override
    protected void onDismissed() {
        if (onPopupDismissed != null) {
            onPopupDismissed.accept(paster == null ? Collections.emptyList() : null);
        }
    }

    public void whenPopupDismissed(PopupOnDismissedListener onPopupDismissed) {
        this.onPopupDismissed = onPopupDismissed;
    }

    private void start() {
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
        mergeDirectoriesCheckBox.setVisibility(View.GONE);

        paster = new FilePasteUpdater();
        paster.whenPasteStarted(() -> containerView.post(() -> {
            progressArea.setVisibility(View.VISIBLE);
            progressBarView.setProgress(0);
            progressBarTextView.setText(context.getString(R.string.popup_progress_text, 1, total));
        }));
        paster.whenPasteUpdated(new FilePasteUpdater.OnPasteUpdatedListener() {
            private int totalAdded;
            private int totalDeleted;
            private int totalRenamed;
            private int totalFailed;
            private int totalProcessed;

            @Override
            public void accept(int added, int deleted, int renamed, int failed, int progressed, String current) {
                totalAdded += added;
                totalDeleted += deleted;
                totalRenamed += renamed;
                totalFailed += failed;
                totalProcessed += progressed;

                containerView.post(() -> {
                    progressBarView.setProgress(totalProcessed * 100 / total);
                    progressBarTextView.setText(context.getString(R.string.popup_progress_text,
                            Math.min(totalProcessed + 1, total), total));
                    progressBarSideTextView.setText(getDisplayPath(current));
                    progressSummaryTextView.setText(context.getString(R.string.paste_progress_summary,
                            totalAdded, totalDeleted, totalRenamed, totalFailed));
                });
            }
        });
        paster.whenPasteStopped(() -> containerView.post(() -> {
            if (paster.isCompleted()) {
                progressBarSideTextView.setText(R.string.popup_progress_completed);
            } else if (paster.isCancelled()) {
                progressBarSideTextView.setText(R.string.popup_progress_aborted);
            } else {
                progressBarSideTextView.setText(R.string.popup_progress_failed);
            }
            updateButtons();
        }));
        paster.start(isCopy, srcFiles, dstDirectory, resolution, mergeDirectoriesCheckBox.isChecked());

        updateButtons();
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
}
