package pd.droidapp.fmgr.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import androidx.annotation.LayoutRes;

import java.util.Objects;

import pd.droidapp.fmgr.R;

public abstract class ProcessingPopup {

    protected final Context context;
    protected final View containerView;

    // views
    protected final View selfView;
    protected final PopupWindow selfWindow;
    protected final LinearLayout mainAreaView;
    protected final PopupTitleBar titleBar;
    protected final PopupButtonBar buttonBar;

    protected ProcessingPopup(View containerView, @LayoutRes int layoutId) {
        this.context = Objects.requireNonNull(containerView, "containerView").getContext();
        this.containerView = containerView;

        selfView = LayoutInflater.from(context).inflate(layoutId, (ViewGroup) containerView, false);
        selfWindow = new PopupWindow(selfView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true) {
            @Override
            public void dismiss() {
                if (isProcessing()) {
                    return;
                }
                super.dismiss();
            }
        };
        mainAreaView = selfView.findViewById(R.id.popup_area);

        titleBar = new PopupTitleBar(mainAreaView.findViewById(R.id.popup_title_bar));
        buttonBar = new PopupButtonBar(mainAreaView.findViewById(R.id.popup_button_bar));

        initPopupWindow();
        initPopupButtons();
    }

    protected void initPopupWindow() {
        selfWindow.setOutsideTouchable(false);
        selfWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        selfWindow.setElevation(24);
        selfWindow.setOnDismissListener(this::onDismissed);

        // outside touch
        selfView.setOnClickListener(v -> selfWindow.dismiss());
        mainAreaView.setOnClickListener(v -> {
        });
    }

    protected void initPopupButtons() {
        titleBar.whenCloseButtonClicked(v -> selfWindow.dismiss());
        selfView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                updateButtons();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
            }
        });
    }

    protected final void updateButtons() {
        buttonBar.invalidate();
        titleBar.enableCloseButton(!isProcessing());
    }

    protected abstract boolean isProcessing();

    protected abstract void onDismissed();

    public final void show() {
        containerView.post(() -> {
            selfWindow.showAtLocation(containerView, Gravity.NO_GRAVITY, 0, 0);
            onShow();
        });
    }

    protected void onShow() {
    }
}
