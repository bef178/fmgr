package pd.droidapp.fmgr.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import androidx.core.util.Consumer;

import java.util.Objects;

import pd.droidapp.fmgr.R;

public class EditPopup {

    private final Context context;
    private final View containerView;
    private final PopupTitleBar titleBar;
    private final EditText textEditView;
    private final Button okButton;
    private final PopupWindow popupWindow;

    public EditPopup(View containerView) {
        this.context = Objects.requireNonNull(containerView, "containerView").getContext();
        this.containerView = containerView;

        View popupView = LayoutInflater.from(context).inflate(
                R.layout.edit_popup,
                (ViewGroup) containerView,
                false);

        View popupArea = popupView.findViewById(R.id.popup_area);
        titleBar = new PopupTitleBar(popupView.findViewById(R.id.popup_title_bar));
        titleBar.setOnCloseClicked(v -> dismiss());
        textEditView = popupView.findViewById(R.id.popup_edit);
        okButton = popupView.findViewById(R.id.button_ok);

        textEditView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(textEditView.getWindowToken(), 0);
                }
                okButton.performClick();
                return true;
            }
            return false;
        });

        popupView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            private final Rect rect = new Rect();
            private int lastKeyboardHeight = 0;

            @Override
            public void onGlobalLayout() {
                popupView.getWindowVisibleDisplayFrame(rect);
                int keyboardHeight = popupView.getRootView().getHeight() - rect.bottom;
                if (keyboardHeight != lastKeyboardHeight) {
                    lastKeyboardHeight = keyboardHeight;
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) popupArea.getLayoutParams();
                    params.topMargin = rect.top + (rect.height() - popupArea.getHeight()) / 2;
                    params.gravity = Gravity.CENTER_HORIZONTAL;
                    popupArea.setLayoutParams(params);
                }
            }
        });

        popupView.setOnClickListener(v -> dismiss());
        popupArea.setOnClickListener(v -> {
        });

        popupWindow = new PopupWindow(popupView,
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                true);
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(24);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
    }

    public void show(String title, String text, String hintText, Consumer<String> onConfirm) {
        titleBar.setTitle(title);
        textEditView.setText(text);
        textEditView.setHint(hintText);

        okButton.setOnClickListener(v -> {
            if (onConfirm != null) {
                onConfirm.accept(textEditView.getText().toString());
            }
        });

        containerView.post(() -> {
            popupWindow.showAtLocation(containerView, Gravity.NO_GRAVITY, 0, 0);
            textEditView.requestFocus();
            textEditView.selectAll();
            textEditView.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(textEditView, InputMethodManager.SHOW_FORCED);
                }
            }, 300);
        });
    }

    public void dismiss() {
        popupWindow.dismiss();
    }
}
