package pd.droidapp.fmgr.popup;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.forwardViewActionsTo;

class PopupFileItemBar {

    private final ImageView iconImageView;
    private final ImageView selectedIconImageView;
    private final TextView pathTextView;
    private final TextView indexTextView;

    public PopupFileItemBar(View selfView) {
        iconImageView = selfView.findViewById(R.id.popup_file_icon);
        selectedIconImageView = selfView.findViewById(R.id.popup_file_selected);
        pathTextView = selfView.findViewById(R.id.popup_file_name);
        indexTextView = selfView.findViewById(R.id.popup_file_index);
    }

    public void setIndex(int index) {
        indexTextView.setText(String.valueOf(index));
    }

    public void setIcon(int resId) {
        iconImageView.setImageResource(resId);
    }

    public void setSelected(boolean selected) {
        selectedIconImageView.setVisibility(selected ? View.VISIBLE : View.GONE);
    }

    public void setPath(CharSequence path) {
        pathTextView.setText(path);
    }

    public void forwardPathViewClicksTo(View itemView) {
        forwardViewActionsTo(pathTextView, itemView);
    }
}
