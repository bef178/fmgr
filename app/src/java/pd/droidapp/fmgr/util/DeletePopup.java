package pd.droidapp.fmgr.util;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.getDisplayPath;

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

    private FileRemoveUpdater remover;
    private final Collection<File> totalDeleted = Collections.synchronizedList(new LinkedList<>());

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
        buttonBar.addButton(R.string.start, () -> remover == null, () -> true, v -> start());
        buttonBar.addButton(R.string.abort, () -> remover != null, this::isProcessing, v -> abort());
    }

    private void initProgress() {
        progressBarTextView.setText(context.getString(R.string.popup_progress_text, 1, srcFiles.size()));
        progressBarSideTextView.setText(R.string.popup_progress_pending);
    }

    @Override
    protected boolean isProcessing() {
        return remover != null && !remover.isStopped();
    }

    @Override
    protected void stopProcessing(Runnable onStopped) {
        if (remover == null || remover.isStopped()) {
            onStopped.run();
            return;
        }
        remover.whenRemoveStopped(onStopped);
        remover.cancel();
    }

    @Override
    protected void onDismissed() {
        if (onPopupDismissed != null) {
            onPopupDismissed.accept(Collections.emptyList(), totalDeleted);
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

        remover = new FileRemoveUpdater();
        remover.whenRemoveStarted(() -> containerView.post(() -> {
            progressArea.setVisibility(View.VISIBLE);
            progressBarView.setProgress(0);
            progressBarTextView.setText(context.getString(R.string.popup_progress_text, 1, total));
        }));
        remover.whenRemoveUpdated(new FileRemoveUpdater.OnRemoveUpdatedListener() {
            private int totalFailed;
            private int totalProgressed;

            @Override
            public void accept(List<File> deleted, int failed, int progressed, String current) {
                totalDeleted.addAll(deleted);
                totalFailed += failed;
                totalProgressed += progressed;

                containerView.post(() -> {
                    progressBarView.setProgress(totalProgressed * 100 / total);
                    progressBarTextView.setText(context.getString(R.string.popup_progress_text,
                            Math.min(totalProgressed + 1, total), total));
                    progressBarSideTextView.setText(getDisplayPath(current));
                    progressSummaryTextView.setText(context.getString(R.string.delete_progress_summary,
                            totalDeleted.size(), totalFailed));
                });
            }
        });
        remover.whenRemoveStopped(() -> containerView.post(() -> {
            if (remover.isCompleted()) {
                progressBarSideTextView.setText(R.string.popup_progress_completed);
            } else if (remover.isCancelled()) {
                progressBarSideTextView.setText(R.string.popup_progress_aborted);
            } else {
                progressBarSideTextView.setText(R.string.popup_progress_failed);
            }
            updateButtons();
        }));
        remover.start(srcFiles, prune);

        updateButtons();
    }

    private void abort() {
        if (remover != null) {
            remover.cancel();
        }
    }
}
