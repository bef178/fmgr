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
import android.widget.TextView;

import androidx.core.util.Consumer;

import pd.droidapp.fmgr.R;

public class EditPopup {

    private final Context context;
    private final View rootView;
    private final PopupWindow popupWindow;
    private final TextView titleTextView;
    private final EditText textEditView;
    private final Button okButton;

    public EditPopup(Context context, View rootView) {
        this.context = context;
        this.rootView = rootView;

        View popupView = LayoutInflater.from(context).inflate(
                R.layout.edit_popup,
                rootView != null ? (ViewGroup) rootView : null,
                false);

        View popupArea = popupView.findViewById(R.id.popup_area);
        titleTextView = popupView.findViewById(R.id.popup_title);
        textEditView = popupView.findViewById(R.id.popup_text);
        okButton = popupView.findViewById(R.id.button_ok);
        Button cancelButton = popupView.findViewById(R.id.button_cancel);

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

        cancelButton.setOnClickListener(v -> dismiss());

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
        titleTextView.setText(title);
        textEditView.setText(text);
        textEditView.setHint(hintText);

        okButton.setOnClickListener(v -> {
            String name = textEditView.getText().toString().trim();
            if (onConfirm != null) {
                onConfirm.accept(name);
            }
        });

        if (rootView != null) {
            rootView.post(() -> {
                popupWindow.showAtLocation(rootView, Gravity.NO_GRAVITY, 0, 0);
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
    }

    public void dismiss() {
        popupWindow.dismiss();
    }
}
