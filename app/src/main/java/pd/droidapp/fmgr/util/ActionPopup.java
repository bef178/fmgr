package pd.droidapp.fmgr.util;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupWindow;

import pd.droidapp.fmgr.R;

public class ActionPopup {

    private final PopupWindow popupWindow;
    private final ImageButton newDirectoryButton;
    private final ImageButton newFileButton;
    private final ImageButton pasteFromCutButton;
    private final ImageButton pasteFromCopyButton;
    private final ImageButton cleanEmptyButton;

    public ActionPopup(Context context, View anchor) {
        View popupView = LayoutInflater.from(context).inflate(R.layout.action_popup, null);

        newDirectoryButton = popupView.findViewById(R.id.action_new_directory);
        newFileButton = popupView.findViewById(R.id.action_new_file);
        pasteFromCutButton = popupView.findViewById(R.id.action_paste_from_cut);
        pasteFromCopyButton = popupView.findViewById(R.id.action_paste_from_copy);
        cleanEmptyButton = popupView.findViewById(R.id.action_delete_empty);

        popupWindow = new PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(16);
        popupWindow.showAsDropDown(anchor, 0, 0, Gravity.END);
    }

    public void setOnNewDirectoryClickedListener(Runnable listener) {
        newDirectoryButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.run();
            }
            dismiss();
        });
    }

    public void setOnNewFileClickedListener(Runnable listener) {
        newFileButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.run();
            }
            dismiss();
        });
    }

    public void setPasteFromCutButtonVisible(boolean visible) {
        pasteFromCutButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setOnPasteFromCutClickedListener(Runnable listener) {
        pasteFromCutButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.run();
            }
            dismiss();
        });
    }

    public void setPasteFromCopyButtonVisible(boolean visible) {
        pasteFromCopyButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setOnPasteFromCopyClickedListener(Runnable listener) {
        pasteFromCopyButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.run();
            }
            dismiss();
        });
    }

    public void whenDeleteEmptyClicked(Runnable listener) {
        cleanEmptyButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.run();
            }
            dismiss();
        });
    }

    public void dismiss() {
        popupWindow.dismiss();
    }
}
