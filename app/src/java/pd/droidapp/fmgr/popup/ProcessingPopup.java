package pd.droidapp.fmgr.popup;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import java.util.Objects;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.view.PopupBottomBar;
import pd.droidapp.fmgr.view.PopupTitleBar;

public abstract class ProcessingPopup {

    private static final int NESTED_POPUP_OFFSET_DP = 16;
    private static int numShowingPopups;

    protected final Context context;
    protected final View containerView;

    // views
    protected final View selfView;
    protected final PopupTitleBar titleBar;
    protected final LinearLayout contentView;
    protected final PopupBottomBar bottomBar;
    protected final Dialog selfDialog;

    // guard
    private boolean dismissing;

    protected ProcessingPopup(View containerView) {
        this.context = Objects.requireNonNull(containerView, "containerView").getContext();
        this.containerView = containerView;

        selfView = LayoutInflater.from(context).inflate(R.layout.popup_frame, (ViewGroup) containerView, false);
        titleBar = new PopupTitleBar(selfView.findViewById(R.id.popup_title_bar));
        bottomBar = new PopupBottomBar(selfView.findViewById(R.id.popup_bottom_bar));
        contentView = selfView.findViewById(R.id.popup_content);
        inflateContentView();

        selfDialog = new Dialog(context) {
            @Override
            public void onBackPressed() {
                if (ProcessingPopup.this.onBackPressed()) {
                    return;
                }
                super.onBackPressed();
            }

            @Override
            public void dismiss() {
                if (!canDismiss() || dismissing) {
                    return;
                }
                dismissing = true;
                renderTitleBar();
                onDismissing(() -> selfView.post(() -> {
                    dismissing = false;
                    renderTitleBar();
                    super.dismiss();
                }));
            }
        };
        selfDialog.setContentView(selfView);
        setupDialog();

        titleBar.whenCloseButtonClicked(selfDialog::dismiss);
    }

    private void renderTitleBar() {
        PopupTitleBar.State state = titleBar.getState();
        if (state != null) {
            titleBar.render(state.copyWithButtonEnabled(!dismissing));
        }
    }

    protected void inflateContentView() {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) contentView.getLayoutParams();
        params.width = context.getResources().getDimensionPixelSize(R.dimen.processing_popup_width);
        params.height = context.getResources().getDimensionPixelSize(R.dimen.processing_popup_content_height);
        contentView.setLayoutParams(params);
    }

    private void setupDialog() {
        selfDialog.setCancelable(true);
        selfDialog.setCanceledOnTouchOutside(false);
        selfDialog.setOnDismissListener(dialog -> {
            numShowingPopups--;
            onDismissed();
        });
        Window window = selfDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.5f);
            setupDialogWindow(window);
        }
    }

    protected void setupDialogWindow(@NonNull Window window) {
        window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        window.getAttributes().y = context.getResources().getDimensionPixelSize(R.dimen.processing_popup_top_margin);
    }

    protected boolean canDismiss() {
        return true;
    }

    protected abstract void onDismissing(Runnable continueDismiss);

    protected abstract void onDismissed();

    public final void show() {
        containerView.post(() -> {
            if (numShowingPopups > 0) {
                offsetWindow();
            }
            selfDialog.show();
            numShowingPopups++;
            onShow();
        });
    }

    private void offsetWindow() {
        Window window = selfDialog.getWindow();
        if (window == null) {
            return;
        }
        int offset = Math.round(NESTED_POPUP_OFFSET_DP * context.getResources().getDisplayMetrics().density);
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.x += offset;
        attrs.y += offset;
        window.setAttributes(attrs);
    }

    protected boolean onBackPressed() {
        return false;
    }

    protected abstract void onShow();
}
