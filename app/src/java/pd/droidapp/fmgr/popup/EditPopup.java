package pd.droidapp.fmgr.popup;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import java.util.function.Predicate;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.view.ButtonState;
import pd.droidapp.fmgr.view.PopupBottomBar;
import pd.droidapp.fmgr.view.PopupTitleBar;

public class EditPopup extends ProcessingPopup {

    private final String title;
    private final Predicate<String> onConfirm;

    // views
    private final EditText textEditView;

    public EditPopup(View containerView, String title, String text, String hintText, Predicate<String> onConfirm) {
        super(containerView);
        this.title = title;
        this.onConfirm = onConfirm;

        textEditView = contentView.findViewById(R.id.popup_edit);
        textEditView.setText(text);
        textEditView.setHint(hintText);

        bottomBar.whenButtonClicked(id -> {
            if (id == R.string.ok) {
                start();
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
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        params.topMargin = context.getResources().getDimensionPixelSize(R.dimen.edit_popup_top_margin);
        areaView.setLayoutParams(params);

        LayoutInflater.from(context).inflate(R.layout.edit_popup_content, contentView, true);
    }

    private void initTextEdit() {
        textEditView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(textEditView.getWindowToken(), 0);
                }
                start();
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
        bottomBar.render(new PopupBottomBar.State(ButtonState.ofText(R.string.ok, context.getString(R.string.ok))));

        textEditView.requestFocus();
        textEditView.selectAll();
    }

    private void start() {
        if (onConfirm.test(textEditView.getText().toString())) {
            selfWindow.dismiss();
        }
    }
}
