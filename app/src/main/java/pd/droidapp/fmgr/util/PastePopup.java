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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import pd.droidapp.fmgr.R;

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
    private PopupOnDismissListener onDismiss;

    private FilePasteUpdater paster;

    public PastePopup(View containerView, boolean isCopy, List<File> srcFiles, File dstRoot) {
        this.context = Objects.requireNonNull(containerView, "containerView").getContext();
        this.containerView = containerView;
        this.isCopy = isCopy;
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
            if (onDismiss != null) {
                onDismiss.accept(paster == null ? Collections.emptyList() : null);
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

    public void whenDismissClicked(PopupOnDismissListener onDismiss) {
        this.onDismiss = onDismiss;
    }

    public void show() {
        containerView.post(() -> selfWindow.showAtLocation(containerView, Gravity.NO_GRAVITY, 0, 0));
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

        startButton.setVisibility(View.GONE);
        abortButton.setVisibility(View.VISIBLE);
        titleBar.enableCloseButton(false);

        paster = new FilePasteUpdater();
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
        paster.whenPasteStopped((added, overwritten, skipped, failed, currentFile) -> containerView.post(() -> {
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
        RENAME_INCOMING,
        SKIP_INCOMING,
    }
}
