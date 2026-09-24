package pd.droidapp.fmgr.popup;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import java.util.Objects;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.view.PopupTitleBar;

public abstract class ProcessingPopup {

    protected final Context context;
    protected final View containerView;

    // views
    protected final View selfView;
    protected final LinearLayout areaView;
    protected final PopupTitleBar titleBar;
    protected final LinearLayout contentView;
    protected final PopupButtonBar bottomBar;
    protected final PopupWindow selfWindow;

    // guard
    private boolean dismissing;

    protected ProcessingPopup(View containerView) {
        this.context = Objects.requireNonNull(containerView, "containerView").getContext();
        this.containerView = containerView;

        selfView = LayoutInflater.from(context).inflate(R.layout.popup_frame, (ViewGroup) containerView, false);
        areaView = selfView.findViewById(R.id.popup_area);
        titleBar = new PopupTitleBar(areaView.findViewById(R.id.popup_title_bar));
        contentView = areaView.findViewById(R.id.popup_content);
        bottomBar = new PopupButtonBar(areaView.findViewById(R.id.popup_bottom_bar));

        selfWindow = new PopupWindow(selfView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true) {
            @Override
            public void dismiss() {
                if (dismissing) {
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

        initPopup();
        inflateContent();
    }

    private void renderTitleBar() {
        PopupTitleBar.State currentState = titleBar.getState();
        if (currentState != null) {
            titleBar.render(currentState.copyWithButtonEnabled(!dismissing));
        }
    }

    protected void initPopup() {
        titleBar.whenCloseButtonClicked(selfWindow::dismiss);

        selfView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                updateButtons();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
            }
        });

        selfWindow.setOutsideTouchable(false);
        selfWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        selfWindow.setElevation(24);
        selfWindow.setOnDismissListener(this::onDismissed);
    }

    protected abstract void inflateContent();

    protected final void updateButtons() {
        bottomBar.invalidate();
    }

    protected abstract boolean isProcessing();

    protected abstract void onDismissing(Runnable continueDismiss);

    protected abstract void onDismissed();

    public final void show() {
        containerView.post(() -> {
            selfWindow.showAtLocation(containerView, Gravity.NO_GRAVITY, 0, 0);
            onShow();
        });
    }

    protected abstract void onShow();
}
