package pd.droidapp.fmgr.popup;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.popup.ProcessingWorker.StopReason;
import pd.droidapp.fmgr.util.Util;

public class DeletePopup extends ProcessingPopup {

    private final List<File> srcFiles;
    private final boolean prune;

    // views
    private final LinearLayout progressArea;
    private final ProgressBar progressBarView;
    private final TextView progressBarTextView;
    private final TextView progressBarSideTextView;
    private final TextView progressSummaryTextView;

    // callbacks
    private PopupOnDismissedListener onPopupDismissed;

    private DeleteWorker worker;
    private final Collection<File> totalRemoved = new LinkedList<>();
    private int totalFailed;
    private int totalProgressed;

    public DeletePopup(View containerView, List<File> srcFiles, boolean prune) {
        super(containerView, R.layout.delete_popup);
        this.srcFiles = new LinkedList<>(srcFiles);
        this.prune = prune;

        progressArea = mainAreaView.findViewById(R.id.progress_area);
        progressBarView = mainAreaView.findViewById(R.id.progress_bar);
        progressBarTextView = mainAreaView.findViewById(R.id.progress_bar_text);
        progressBarSideTextView = mainAreaView.findViewById(R.id.progress_bar_side_text);
        progressSummaryTextView = mainAreaView.findViewById(R.id.progress_summary);

        titleBar.setTitle(context.getString(R.string.delete_x_items, srcFiles.size()));

        initProgress();
    }

    @Override
    protected void initPopupButtons() {
        super.initPopupButtons();
        buttonBar.addButton(R.string.start, () -> worker == null, () -> true, v -> start());
        buttonBar.addButton(R.string.abort, this::isProcessing, () -> isProcessing() && !worker.isCancelled(), v -> abort());
        buttonBar.addButton(R.string.close, () -> worker != null && !worker.isWorking(), () -> true, v -> selfWindow.dismiss());
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
    protected void stopProcessing(Runnable onStopped) {
        if (worker == null || !worker.isWorking()) {
            onStopped.run();
            return;
        }
        worker.whenStopped(ignored -> onStopped.run());
        worker.cancel();
    }

    @Override
    protected void onDismissed() {
        if (onPopupDismissed != null) {
            onPopupDismissed.accept(Collections.emptyList(), totalRemoved);
        }
    }

    public void whenPopupDismissed(PopupOnDismissedListener onPopupDismissed) {
        this.onPopupDismissed = onPopupDismissed;
    }

    @Override
    protected void onShow() {
    }

    private void start() {
        final int total = srcFiles.size();

        worker = new DeleteWorker();
        worker.whenStarted(() -> containerView.post(() -> {
            progressArea.setVisibility(View.VISIBLE);
            progressBarView.setProgress(0);
            progressBarTextView.setText(context.getString(R.string.popup_progress_text, 1, total));
            progressBarSideTextView.setText(R.string.popup_progress_processing);
        }));
        worker.whenUpdated((removed, failed, progressed) -> containerView.post(() -> {
            for (String path : removed) {
                totalRemoved.add(new File(Util.stripTrailingSlash(path)));
            }
            totalFailed += failed;
            totalProgressed += progressed;

            progressBarView.setProgress(totalProgressed * 100 / total);
            progressBarTextView.setText(context.getString(R.string.popup_progress_text,
                    Math.min(totalProgressed + 1, total), total));
            progressSummaryTextView.setText(context.getString(R.string.delete_progress_summary,
                    totalRemoved.size(), totalFailed));
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
        worker.start(srcPaths, prune);

        updateButtons();
    }

    private void abort() {
        if (worker != null) {
            worker.cancel();
        }
        updateButtons();
    }
}
