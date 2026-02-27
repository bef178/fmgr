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

    public ActionPopup(Context context, View anchor) {
        View popupView = LayoutInflater.from(context).inflate(R.layout.action_popup, null);
        popupWindow = new PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(16);

        newDirectoryButton = popupView.findViewById(R.id.action_new_directory);
        newFileButton = popupView.findViewById(R.id.action_new_file);

        popupWindow.showAsDropDown(anchor, 0, 0, Gravity.END);
    }

    public void setOnNewDirectoryClickListener(Runnable listener) {
        newDirectoryButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.run();
            }
            dismiss();
        });
    }

    public void setOnNewFileClickListener(Runnable listener) {
        newFileButton.setOnClickListener(v -> {
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
