package pd.droidapp.fmgr.popup;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.popup.ProcessingWorker.StopReason;
import pd.droidapp.fmgr.util.FileProperties;

public class DeletePopup extends ProcessingPopup {

    private final List<FileProperties> srcItems;
    private final boolean prune;

    // views
    private final StatusBar statusBar;
    private final LinearLayout progressArea;
    private final ProgressBar progressBarView;
    private final TextView progressBarTextView;
    private final TextView progressBarSideTextView;

    // callbacks
    private PopupOnDismissedListener onPopupDismissed;

    private DeleteWorker worker;
    private final Collection<FileProperties> netRemoved = new LinkedList<>();
    private int totalRemoved;
    private int totalFailed;
    private int totalProgressed;

    public DeletePopup(View containerView, Collection<FileProperties> srcItems, boolean prune) {
        super(containerView, R.layout.delete_popup);
        this.srcItems = new LinkedList<>(srcItems);
        this.prune = prune;

        statusBar = new StatusBar(mainAreaView.findViewById(R.id.status_bar));
        progressArea = mainAreaView.findViewById(R.id.progress_area);
        progressBarView = mainAreaView.findViewById(R.id.progress_bar);
        progressBarTextView = mainAreaView.findViewById(R.id.progress_bar_text);
        progressBarSideTextView = mainAreaView.findViewById(R.id.progress_bar_side_text);

        titleBar.setTitle(R.string.delete);

        initStatusBar();
        initProgress();
    }

    @Override
    protected void initPopupButtons() {
        super.initPopupButtons();
        buttonBar.addButton(R.string.start, () -> worker == null, () -> true, v -> start());
        buttonBar.addButton(R.string.abort, this::isProcessing, () -> isProcessing() && !worker.isCancelled(), v -> abort());
        buttonBar.addButton(R.string.close, () -> worker != null && !worker.isWorking(), () -> true, v -> selfWindow.dismiss());
    }

    private void initStatusBar() {
        statusBar.markReady(R.drawable.outline_delete_24);
        statusBar.setText(context.getString(R.string.x_selected, srcItems.size()));
    }

    private void initProgress() {
        progressBarTextView.setText(context.getString(R.string.popup_progress_text, 1, srcItems.size()));
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
            onPopupDismissed.accept(Collections.emptyList(), netRemoved);
        }
    }

    public void whenPopupDismissed(PopupOnDismissedListener onPopupDismissed) {
        this.onPopupDismissed = onPopupDismissed;
    }

    @Override
    protected void onShow() {
    }

    private void start() {
        final int total = srcItems.size();

        worker = new DeleteWorker();
        worker.whenStarted(() -> containerView.post(() -> {
            statusBar.markRunning();
            statusBar.setText(context.getString(R.string.status_working));

            progressArea.setVisibility(View.VISIBLE);
            progressBarView.setProgress(0);
            progressBarTextView.setText(context.getString(R.string.popup_progress_text, 1, total));
            progressBarSideTextView.setText(R.string.popup_progress_processing);
        }));
        worker.whenUpdated((removed, failed, progressed) -> containerView.post(() -> {
            netRemoved.addAll(removed);
            totalRemoved += removed.size();
            totalFailed += failed;
            totalProgressed += progressed;

            statusBar.setText(context.getString(R.string.delete_progress_summary, totalRemoved, totalFailed));
            progressBarView.setProgress(totalProgressed * 100 / total);
            progressBarTextView.setText(context.getString(R.string.popup_progress_text,
                    Math.min(totalProgressed + 1, total), total));
        }));
        worker.whenStopped(reason -> containerView.post(() -> {
            if (reason == StopReason.COMPLETED) {
                statusBar.markDone();
                progressBarSideTextView.setText(R.string.popup_progress_completed);
            } else if (reason == StopReason.CANCELLED) {
                statusBar.markStopped();
                progressBarSideTextView.setText(R.string.popup_progress_aborted);
            } else {
                statusBar.markStopped();
                progressBarSideTextView.setText(R.string.popup_progress_failed);
            }
            updateButtons();
        }));
        worker.start(srcItems, prune);

        updateButtons();
    }

    private void abort() {
        if (worker != null) {
            worker.cancel();
        }
        updateButtons();
    }
}
