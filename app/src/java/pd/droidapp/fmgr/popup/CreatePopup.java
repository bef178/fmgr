package pd.droidapp.fmgr.popup;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.Collections;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.view.ButtonState;
import pd.droidapp.fmgr.view.PopupBottomBar;
import pd.droidapp.fmgr.view.PopupTitleBar;
import pd.util.FileOps;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.checkBasename;

public class CreatePopup extends ProcessingPopup {

    private final String directory;
    private final boolean isDirectory;
    private final Preset preset;
    private String createdPath;
    private boolean processing;

    // views
    private final EditText textEditView;

    // callbacks
    private PopupOnDismissedListener onPopupDismissed;

    public CreatePopup(View containerView, String directory, boolean isDirectory) {
        super(containerView);
        this.directory = directory;
        this.isDirectory = isDirectory;
        this.preset = isDirectory ? Preset.NEW_DIRECTORY : Preset.NEW_FILE;

        textEditView = contentView.findViewById(R.id.popup_edit);
        textEditView.setHint(context.getString(preset.hintStringId));

        bottomBar.whenButtonClicked(id -> {
            if (id == preset.actionId) {
                start();
            }
        });

        initTextEdit();
    }

    @Override
    protected void inflateContentView() {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) contentView.getLayoutParams();
        params.width = context.getResources().getDimensionPixelSize(R.dimen.processing_popup_width);
        params.height = context.getResources().getDimensionPixelSize(R.dimen.edit_popup_content_height);
        contentView.setLayoutParams(params);
        LayoutInflater.from(context).inflate(R.layout.edit_popup_content, contentView, true);
    }

    @Override
    protected void setupDialogWindow(@NonNull Window window) {
        window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        window.getAttributes().y = context.getResources().getDimensionPixelSize(R.dimen.edit_popup_top_margin);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
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
                start();
                return true;
            }
            return false;
        });
    }

    public void whenPopupDismissed(PopupOnDismissedListener onPopupDismissed) {
        this.onPopupDismissed = onPopupDismissed;
    }

    @Override
    protected boolean canDismiss() {
        return !processing;
    }

    @Override
    protected void onDismissing(Runnable continueDismiss) {
        continueDismiss.run();
    }

    @Override
    protected void onDismissed() {
        if (onPopupDismissed == null || createdPath == null) {
            return;
        }
        onPopupDismissed.accept(
                Collections.singletonList(new FileProperties(createdPath, isDirectory)),
                Collections.emptyList());
    }

    @Override
    protected void onShow() {
        titleBar.render(new PopupTitleBar.State(context.getString(preset.titleStringId)));
        renderBottomBar();

        textEditView.requestFocus();
    }

    private void renderBottomBar() {
        bottomBar.render(new PopupBottomBar.State(
                new ButtonState(preset.actionId, preset.drawableId, !processing && createdPath == null, canStart()),
                new ButtonState(R.id.action_abort, R.drawable.ic_blockmark_24, processing, false)));
    }

    private boolean canStart() {
        return !textEditView.getText().toString().trim().isEmpty();
    }

    private void start() {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(textEditView.getWindowToken(), 0);
        }

        String newName = textEditView.getText().toString().trim();
        Integer errorTextId = checkBasename(newName);
        if (errorTextId != null) {
            Toast.makeText(context, errorTextId, Toast.LENGTH_SHORT).show();
            return;
        }

        if (directory == null) {
            Toast.makeText(context, R.string.error_create_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        String newPath = PathOps.singleton.join(directory, newName);
        if (FileOps.singleton.stat(newPath).exists(false)) {
            Toast.makeText(context, R.string.error_already_exists, Toast.LENGTH_SHORT).show();
            return;
        }

        processing = true;
        renderBottomBar();

        boolean success;
        if (isDirectory) {
            success = FileOps.singleton.createEmptyDirectory(newPath);
            if (success) {
                Toast.makeText(context, R.string.directory_created, Toast.LENGTH_SHORT).show();
            }
        } else {
            success = FileOps.singleton.save(newPath, new byte[0], false);
            if (success) {
                Toast.makeText(context, R.string.file_created, Toast.LENGTH_SHORT).show();
            }
        }

        processing = false;
        if (!success) {
            Toast.makeText(context, R.string.error_create_failed, Toast.LENGTH_SHORT).show();
            renderBottomBar();
            return;
        }

        createdPath = newPath;
        selfDialog.dismiss();
    }

    private enum Preset {

        NEW_FILE(R.id.action_new_file, R.drawable.ia_new_file_24, R.string.new_file, R.string.file_name),
        NEW_DIRECTORY(R.id.action_new_directory, R.drawable.ia_new_directory_24, R.string.new_directory, R.string.directory_name);

        final int actionId;
        final int drawableId;
        final int titleStringId;
        final int hintStringId;

        Preset(int actionId, int drawableId, int titleStringId, int hintStringId) {
            this.actionId = actionId;
            this.drawableId = drawableId;
            this.titleStringId = titleStringId;
            this.hintStringId = hintStringId;
        }
    }
}
