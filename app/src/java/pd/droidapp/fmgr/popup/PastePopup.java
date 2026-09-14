package pd.droidapp.fmgr.popup;

import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.popup.PasteWorker.ConflictResolution;
import pd.droidapp.fmgr.popup.ProcessingWorker.StopReason;
import pd.droidapp.fmgr.util.Util;

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

    private PasteWorker worker;
    private final Collection<String> totalAdded = new LinkedList<>();
    private int totalRemoved;
    private int totalMoved;
    private int totalFailed;
    private int totalProcessed;

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
        buttonBar.addButton(R.string.start, () -> worker == null, () -> true, v -> start());
        buttonBar.addButton(R.string.abort, this::isProcessing, () -> isProcessing() && !worker.isCancelled(), v -> abort());
        buttonBar.addButton(R.string.close, () -> worker != null && !worker.isWorking(), () -> true, v -> selfWindow.dismiss());
    }

    private void initConflictResolution() {
        resolutionTitleTextView.setText(R.string.select_resolution);

        boolean inPlacePaste = srcFiles.stream()
                .allMatch(file -> Objects.equals(file.getParentFile(), dstDirectory));
        mergeDirectoriesCheckBox.setChecked(!inPlacePaste);
    }

    private void initProgress() {
        progressBarTextView.setText(context.getString(R.string.popup_progress_text, 1, srcFiles.size()));
        progressBarSideTextView.setText(R.string.popup_progress_pending);
    }

    @Override
    protected boolean isProcessing() {
        return worker != null && worker.isWorking();
    }

    @Override
    protected void onDismissing(Runnable continueDismiss) {
        if (worker != null) {
            worker.cancel();
        }
        if (worker == null || !worker.whenStopped(reason -> continueDismiss.run())) {
            continueDismiss.run();
        }
    }

    @Override
    protected void onDismissed() {
        if (onPopupDismissed != null) {
            onPopupDismissed.accept(totalAdded, Collections.emptyList());
        }
    }

    public void whenPopupDismissed(PopupOnDismissedListener onPopupDismissed) {
        this.onPopupDismissed = onPopupDismissed;
    }

    @Override
    protected void onShow() {
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

        worker = new PasteWorker();
        worker.whenStarted(() -> containerView.post(() -> {
            progressArea.setVisibility(View.VISIBLE);
            progressBarView.setProgress(0);
            progressBarTextView.setText(context.getString(R.string.popup_progress_text, 1, total));
            progressBarSideTextView.setText(R.string.popup_progress_processing);
        }));
        worker.whenUpdated((added, removed, moved, failed, progressed) -> containerView.post(() -> {
            for (String path : added) {
                totalAdded.add(Util.stripTrailingSlash(path));
            }
            for (String path : removed) {
                totalAdded.remove(Util.stripTrailingSlash(path));
            }
            for (Map.Entry<String, String> pair : moved) {
                totalAdded.remove(Util.stripTrailingSlash(pair.getKey()));
                totalAdded.add(Util.stripTrailingSlash(pair.getValue()));
            }
            int reportAdded = added.size() + moved.size();
            totalRemoved += removed.size() + moved.size();
            totalMoved += moved.size();
            totalFailed += failed;
            totalProcessed += progressed;

            progressBarView.setProgress(totalProcessed * 100 / total);
            progressBarTextView.setText(context.getString(R.string.popup_progress_text,
                    Math.min(totalProcessed + 1, total), total));
            progressSummaryTextView.setText(context.getString(R.string.paste_progress_summary,
                    reportAdded, totalRemoved, totalMoved, totalFailed));
        }));
        worker.whenStopped(reason -> containerView.post(() -> {
            if (reason == StopReason.COMPLETED) {
                progressBarSideTextView.setText(R.string.popup_progress_completed);
            } else if (reason == StopReason.CANCELLED) {
                progressBarSideTextView.setText(R.string.popup_progress_aborted);
            } else {
                progressBarSideTextView.setText(R.string.popup_progress_failed);
            }
            updateButtons();
        }));
        List<String> srcPaths = srcFiles.stream().map(File::getPath).collect(Collectors.toList());
        if (isCopy) {
            worker.startCopy(srcPaths, dstDirectory.getPath(), resolution, mergeDirectoriesCheckBox.isChecked());
        } else {
            worker.startCut(srcPaths, dstDirectory.getPath(), resolution, mergeDirectoriesCheckBox.isChecked());
        }

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
        if (worker != null) {
            worker.cancel();
        }
        updateButtons();
    }
}
