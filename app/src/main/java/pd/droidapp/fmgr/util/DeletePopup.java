package pd.droidapp.fmgr.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import pd.droidapp.fmgr.R;

public class DeletePopup {

    private final Context context;
    private final View containerView;
    private final Collection<File> files;
    private final boolean prune;
    private final File startDirectory;
    private PopupOnClosed onClosed;

    private final PopupTitleBar titleBar;
    private final LinearLayout progressArea;
    private final ProgressBar progressBarView;
    private final TextView progressBarTextView;
    private final TextView progressBarSideTextView;
    private final TextView progressSummaryTextView;
    private final Button startButton;
    private final Button abortButton;
    private final Button closeButton;
    private final PopupWindow popupWindow;

    private FileRemoveUpdater fileRemoveUpdater;
    private boolean dismissed;
    private final Collection<File> removedFiles = new ArrayList<>();
    private int processedCount;
    private int removedCount;
    private int failedCount;

    public DeletePopup(View containerView, Collection<File> files, boolean prune, File startDirectory) {
        this.context = Objects.requireNonNull(containerView, "containerView").getContext();
        this.containerView = containerView;
        this.files = files;
        this.prune = prune;
        this.startDirectory = startDirectory;

        View popupView = LayoutInflater.from(context).inflate(
                R.layout.delete_popup,
                (ViewGroup) containerView,
                false);

        View popupArea = popupView.findViewById(R.id.popup_area);
        titleBar = new PopupTitleBar(popupView.findViewById(R.id.popup_title_bar));
        progressArea = popupView.findViewById(R.id.progress_area);
        progressBarView = popupView.findViewById(R.id.progress_bar);
        progressBarTextView = popupView.findViewById(R.id.progress_bar_text);
        progressBarSideTextView = popupView.findViewById(R.id.progress_bar_side_text);
        progressSummaryTextView = popupView.findViewById(R.id.progress_summary);
        startButton = popupView.findViewById(R.id.button_start);
        abortButton = popupView.findViewById(R.id.button_abort);
        closeButton = popupView.findViewById(R.id.button_close);

        startButton.setVisibility(View.VISIBLE);
        abortButton.setVisibility(View.GONE);
        closeButton.setVisibility(View.GONE);

        titleBar.setTitle(context.getString(R.string.delete_x_items, this.files.size()));
        progressBarTextView.setText(context.getString(R.string.progress_text, 0, this.files.size()));
        progressBarSideTextView.setText(R.string.progress_pending);

        titleBar.whenCloseButtonClicked(v -> dismiss());
        startButton.setOnClickListener(v -> start());
        abortButton.setOnClickListener(v -> abort());
        closeButton.setOnClickListener(v -> dismiss());

        // for system back button
        popupView.setFocusableInTouchMode(true);
        popupView.requestFocus();
        popupView.setOnKeyListener((v, keyCode, event) ->
                keyCode == KeyEvent.KEYCODE_BACK
                        && event.getAction() == KeyEvent.ACTION_UP
                        && !titleBar.isCloseButtonEnabled());

        popupView.setOnClickListener(v -> {
            if (fileRemoveUpdater == null) {
                dismiss();
            }
        });
        popupArea.setOnClickListener(v -> {});

        popupWindow = new PopupWindow(popupView,
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                true);
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(24);
    }

    public void whenClosed(PopupOnClosed onClosed) {
        this.onClosed = onClosed;
    }

    public void show() {
        containerView.post(() -> popupWindow.showAtLocation(containerView, Gravity.NO_GRAVITY, 0, 0));
    }

    public void dismiss() {
        if (dismissed) {
            return;
        }
        dismissed = true;
        if (onClosed != null) {
            onClosed.onClosed(removedFiles);
        }
        popupWindow.dismiss();
    }

    private void start() {
        int total = files.size();

        startButton.setVisibility(View.GONE);
        abortButton.setVisibility(View.VISIBLE);
        titleBar.enableCloseButton(false);

        fileRemoveUpdater = new FileRemoveUpdater();
        fileRemoveUpdater.whenRemoveStarted(() -> containerView.post(() -> {
            progressArea.setVisibility(View.VISIBLE);
            progressBarView.setProgress(0);
            progressBarTextView.setText(context.getString(R.string.progress_text, 0, total));
            progressSummaryTextView.setText(context.getString(R.string.delete_progress_summary, 0, 0));
        }));
        fileRemoveUpdater.whenFileRemoved((file, isFailed) -> containerView.post(() -> {
            countRemoved(file, isFailed, total);
        }));
        fileRemoveUpdater.whenDirectoryRemoved((directory, isFailed) -> containerView.post(() -> {
            countRemoved(directory, isFailed, total);
        }));
        fileRemoveUpdater.whenRemoveUpdated((removed, failed) -> containerView.post(() -> {
            removedCount += removed;
            failedCount += failed;
            progressSummaryTextView.setText(context.getString(R.string.delete_progress_summary, removedCount, failedCount));
        }));
        fileRemoveUpdater.whenRemoveStopped(() -> containerView.post(() -> {
            if (fileRemoveUpdater.isCancelled()) {
                progressBarSideTextView.setText(R.string.progress_aborted);
            } else {
                progressBarSideTextView.setText(R.string.progress_completed);
            }
            progressSummaryTextView.setText(context.getString(R.string.delete_progress_summary, removedCount, failedCount));

            abortButton.setVisibility(View.GONE);
            closeButton.setVisibility(View.VISIBLE);
            titleBar.enableCloseButton(true);
        }));
        fileRemoveUpdater.start(files, prune ? startDirectory : null);
    }

    private void countRemoved(File file, boolean isFailed, int total) {
        if (files.contains(file)) {
            if (!isFailed) {
                removedFiles.add(file);
            }
            processedCount++;
            progressBarView.setProgress(processedCount * 100 / total);
            progressBarTextView.setText(context.getString(R.string.progress_text, processedCount, total));
        }
        progressBarSideTextView.setText(file.getPath());
    }

    private void abort() {
        if (fileRemoveUpdater != null) {
            fileRemoveUpdater.cancel();
        }
    }
}
