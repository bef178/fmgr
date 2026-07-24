package pd.droidapp.fmgr.util;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import pd.droidapp.fmgr.R;

public class PopupFileItem {

    private final ImageView iconView;
    private final ImageView selectedIcon;
    private final TextView pathView;
    private final TextView indexView;

    public PopupFileItem(View itemView) {
        iconView = itemView.findViewById(R.id.popup_file_icon);
        selectedIcon = itemView.findViewById(R.id.popup_file_selected);
        pathView = itemView.findViewById(R.id.popup_file_name);
        indexView = itemView.findViewById(R.id.popup_file_index);
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
}
