package pd.droidapp.fmgr.popup;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.annotation.StringRes;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.view.ButtonState;
import pd.droidapp.fmgr.view.PopupBottomBar;
import pd.droidapp.fmgr.view.PopupTitleBar;

public abstract class EditPopup extends ProcessingPopup {

    private final String title;

    // views
    private final EditText textEditView;

    protected EditPopup(View containerView, @StringRes int titleTextId, String initText, String hintText) {
        super(containerView);
        this.title = context.getString(titleTextId);

        textEditView = contentView.findViewById(R.id.popup_edit);
        textEditView.setText(initText);
        textEditView.setHint(hintText);

        bottomBar.whenButtonClicked(id -> {
            if (id == R.id.action_start) {
                if (onStart(getInputText())) {
                    selfWindow.dismiss();
                }
            }
        });

        initTextEdit();
    }

    @Override
    protected void initPopup() {
        super.initPopup();
        selfWindow.setFocusable(true);
        selfWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    @Override
    protected void inflateContent() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) areaView.getLayoutParams();
        params.topMargin = context.getResources().getDimensionPixelSize(R.dimen.edit_popup_top_margin);
        areaView.setLayoutParams(params);

        LayoutInflater.from(context).inflate(R.layout.edit_popup_content, contentView, true);
    }

    private void initTextEdit() {
        textEditView.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                renderBottomBar();
            }
        });

        textEditView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String text = getInputText();
                if (canStart(text) && onStart(text)) {
                    selfWindow.dismiss();
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(textEditView.getWindowToken(), 0);
                    }
                }
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onDismissing(Runnable continueDismiss) {
        continueDismiss.run();
    }

    @Override
    protected void onDismissed() {
    }

    @Override
    protected void onShow() {
        titleBar.render(new PopupTitleBar.State(title));
        renderBottomBar();

        textEditView.requestFocus();
        textEditView.selectAll();
    }

    private void renderBottomBar() {
        bottomBar.render(new PopupBottomBar.State(
                new ButtonState(R.id.action_start, R.drawable.ic_checkmark_24, true, canStart(getInputText()))));
    }

    protected boolean canStart(String text) {
        return true;
    }

    protected abstract boolean onStart(String text);

    private String getInputText() {
        return textEditView.getText().toString();
    }
}
