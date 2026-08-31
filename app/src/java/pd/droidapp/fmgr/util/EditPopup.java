package pd.droidapp.fmgr.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import java.util.Objects;
import java.util.function.Predicate;

import pd.droidapp.fmgr.R;

public class EditPopup {

    private final Context context;
    private final View containerView;
    private final Predicate<String> onConfirm;

    // views
    private final View selfView;
    private final PopupWindow selfWindow;
    private final LinearLayout mainAreaView;
    private final PopupTitleBar titleBar;
    private final EditText textEditView;
    private final PopupButtonBar buttonBar;

    public EditPopup(View containerView, String title, String text, String hintText, Predicate<String> onConfirm) {
        this.context = Objects.requireNonNull(containerView, "containerView").getContext();
        this.containerView = containerView;
        this.onConfirm = onConfirm;

        selfView = LayoutInflater.from(context).inflate(
                R.layout.edit_popup,
                (ViewGroup) containerView,
                false);
        selfWindow = new PopupWindow(selfView,
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                true);
        mainAreaView = selfView.findViewById(R.id.popup_area);

        titleBar = new PopupTitleBar(mainAreaView.findViewById(R.id.popup_title_bar));
        titleBar.setTitle(title);
        textEditView = mainAreaView.findViewById(R.id.popup_edit);
        textEditView.setText(text);
        textEditView.setHint(hintText);
        buttonBar = new PopupButtonBar(mainAreaView.findViewById(R.id.popup_button_bar));

        initPopupWindow();
        enableClosePopupOnOutsideTouch();
        initPopupTitleBar();
        initTextEdit();
        initPopupButtonBar();
        trackKeyboardHeight();
    }

    private void initPopupWindow() {
        selfWindow.setFocusable(true);
        selfWindow.setOutsideTouchable(false);
        selfWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        selfWindow.setElevation(24);
        selfWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
    }

    private void enableClosePopupOnOutsideTouch() {
        selfView.setOnClickListener(v -> selfWindow.dismiss());
        mainAreaView.setOnClickListener(v -> {
        });
    }

    private void initPopupTitleBar() {
        titleBar.whenCloseButtonClicked(v -> selfWindow.dismiss());
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

    private void initPopupButtonBar() {
        buttonBar.addButton(R.string.ok, () -> true, () -> true, v -> confirm());
        buttonBar.invalidate();
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

    public void show() {
        containerView.post(() -> {
            selfWindow.showAtLocation(containerView, Gravity.NO_GRAVITY, 0, 0);
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

    private void confirm() {
        if (onConfirm.test(textEditView.getText().toString())) {
            selfWindow.dismiss();
        }
    }
}
