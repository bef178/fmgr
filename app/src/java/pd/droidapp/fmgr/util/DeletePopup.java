package pd.droidapp.fmgr.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.getDisplayPath;

public class DeletePopup {

    private final Context context;
    private final View containerView;
    private final List<File> srcFiles;
    private final boolean prune;

    // views
    private final View selfView;
    private final PopupWindow selfWindow;
    private final View mainAreaView;
    private final PopupTitleBar titleBar;
    private final LinearLayout progressArea;
    private final ProgressBar progressBarView;
    private final TextView progressBarTextView;
    private final TextView progressBarSideTextView;
    private final TextView progressSummaryTextView;
    private final PopupButtonBar buttonBar;

    // callbacks
    private PopupOnDismissListener onDismiss;

    private FileRemoveUpdater remover;
    private final Collection<File> totalDeleted = Collections.synchronizedList(new LinkedList<>());

    public DeletePopup(View containerView, List<File> srcFiles, boolean prune) {
        this.context = Objects.requireNonNull(containerView, "containerView").getContext();
        this.containerView = containerView;
        this.srcFiles = new LinkedList<>(srcFiles);
        this.prune = prune;

        selfView = LayoutInflater.from(context).inflate(
                R.layout.delete_popup,
                (ViewGroup) containerView,
                false);
        selfWindow = new PopupWindow(selfView,
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                true) {
            @Override
            public void dismiss() {
                if (isRemoving()) {
                    return;
                }
                super.dismiss();
            }
        };
        mainAreaView = selfView.findViewById(R.id.popup_area);

        titleBar = new PopupTitleBar(mainAreaView.findViewById(R.id.popup_title_bar));
        progressArea = mainAreaView.findViewById(R.id.progress_area);
        progressBarView = mainAreaView.findViewById(R.id.progress_bar);
        progressBarTextView = mainAreaView.findViewById(R.id.progress_bar_text);
        progressBarSideTextView = mainAreaView.findViewById(R.id.progress_bar_side_text);
        progressSummaryTextView = mainAreaView.findViewById(R.id.progress_summary);
        buttonBar = new PopupButtonBar(mainAreaView.findViewById(R.id.popup_button_bar));

        initPopupWindow();
        enableClosePopupOnOutsideTouch();
        initPopupTitleBar();
        initProgress();
        initPopupButtonBar();
    }

    private void initPopupWindow() {
        selfWindow.setOutsideTouchable(false);
        selfWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        selfWindow.setElevation(24);
        selfWindow.setOnDismissListener(() -> {
            if (onDismiss != null) {
                onDismiss.accept(totalDeleted);
            }
        });
    }

    private void enableClosePopupOnOutsideTouch() {
        selfView.setOnClickListener(v -> selfWindow.dismiss());
        mainAreaView.setOnClickListener(v -> {});
    }

    private void initPopupTitleBar() {
        titleBar.setTitle(context.getString(R.string.delete_x_items, srcFiles.size()));
        titleBar.whenCloseButtonClicked(v -> selfWindow.dismiss());
    }

    private void initProgress() {
        progressBarTextView.setText(context.getString(R.string.popup_progress_text, 1, srcFiles.size()));
        progressBarSideTextView.setText(R.string.popup_progress_pending);
    }

    private void initPopupButtonBar() {
        buttonBar.addButton(R.string.start, () -> true, () -> remover == null, v -> start());
        buttonBar.addButton(R.string.abort, () -> true, this::isRemoving, v -> abort());
        buttonBar.addButton(R.string.close, () -> true, this::isStopped, v -> selfWindow.dismiss());
        updateButtons();
    }

    private void updateButtons() {
        buttonBar.invalidate();
        titleBar.enableCloseButton(isStopped());
    }

    private boolean isRemoving() {
        return remover != null && !remover.isStopped();
    }

    private boolean isStopped() {
        return !isRemoving();
    }

    public void whenDismissClicked(PopupOnDismissListener onDismiss) {
        this.onDismiss = onDismiss;
    }

    public void show() {
        containerView.post(() -> selfWindow.showAtLocation(containerView, Gravity.NO_GRAVITY, 0, 0));
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
            if (remover.isCancelled()) {
                progressBarSideTextView.setText(R.string.popup_progress_aborted);
            } else {
                progressBarSideTextView.setText(R.string.popup_progress_completed);
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
