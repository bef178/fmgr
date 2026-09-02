package pd.droidapp.fmgr.util;

import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import java.util.function.Predicate;

import pd.droidapp.fmgr.R;

public class EditPopup extends ProcessingPopup {

    private final Predicate<String> onConfirm;

    // views
    private final EditText textEditView;

    public EditPopup(View containerView, String title, String text, String hintText, Predicate<String> onConfirm) {
        super(containerView, R.layout.edit_popup);
        this.onConfirm = onConfirm;

        titleBar.setTitle(title);
        textEditView = mainAreaView.findViewById(R.id.popup_edit);
        textEditView.setText(text);
        textEditView.setHint(hintText);

        initTextEdit();
        trackKeyboardHeight();
    }

    @Override
    protected void initPopupWindow() {
        super.initPopupWindow();
        selfWindow.setFocusable(true);
        selfWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
    }

    @Override
    protected void initPopupButtons() {
        super.initPopupButtons();
        buttonBar.addButton(R.string.ok, () -> true, () -> true, v -> confirm());
    }

    private void initTextEdit() {
        textEditView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(textEditView.getWindowToken(), 0);
                }
                confirm();
                return true;
            }
            return false;
        });
    }

    private void trackKeyboardHeight() {
        selfView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            private final Rect rect = new Rect();
            private int lastKeyboardHeight = 0;

            @Override
            public void onGlobalLayout() {
                selfView.getWindowVisibleDisplayFrame(rect);
                int keyboardHeight = selfView.getRootView().getHeight() - rect.bottom;
                if (keyboardHeight != lastKeyboardHeight) {
                    lastKeyboardHeight = keyboardHeight;
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mainAreaView.getLayoutParams();
                    params.topMargin = rect.top + (rect.height() - mainAreaView.getHeight()) / 2;
                    params.gravity = Gravity.CENTER_HORIZONTAL;
                    mainAreaView.setLayoutParams(params);
                }
            }
        });
    }

    @Override
    protected boolean isProcessing() {
        return false;
    }

    @Override
    protected void onDismissed() {
    }

    @Override
    protected void onShow() {
        textEditView.requestFocus();
        textEditView.selectAll();
        textEditView.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(textEditView, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 300);
    }

    private void confirm() {
        if (onConfirm.test(textEditView.getText().toString())) {
            selfWindow.dismiss();
        }
    }
}
