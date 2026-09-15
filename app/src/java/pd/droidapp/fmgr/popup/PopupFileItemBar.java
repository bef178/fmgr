package pd.droidapp.fmgr.popup;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.Util;

class PopupFileItemBar {

    private final ImageView iconView;
    private final ImageView selectedIcon;
    private final TextView pathView;
    private final TextView indexView;

    public PopupFileItemBar(View selfView) {
        iconView = selfView.findViewById(R.id.popup_file_icon);
        selectedIcon = selfView.findViewById(R.id.popup_file_selected);
        pathView = selfView.findViewById(R.id.popup_file_name);
        indexView = selfView.findViewById(R.id.popup_file_index);
    }

    public void setIndex(int index) {
        indexView.setText(String.valueOf(index));
    }

    public void setIcon(int resId) {
        iconView.setImageResource(resId);
    }

    public void setSelected(boolean selected) {
        selectedIcon.setVisibility(selected ? View.VISIBLE : View.GONE);
    }

    public void setPath(CharSequence path) {
        pathView.setText(path);
    }

    public void forwardPathViewClicksTo(View itemView) {
        Util.forwardViewActionsTo(pathView, itemView);
    }
}
