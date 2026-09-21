package pd.droidapp.fmgr.popup;

import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.forwardViewActionsTo;

class PopupFileItemBar {

    private final ImageView iconImageView;
    private final ImageView badgeIconView;
    private final ProgressBar badgeProgressView;
    private final TextView pathTextView;
    private final TextView indexTextView;

    public PopupFileItemBar(View selfView) {
        iconImageView = selfView.findViewById(R.id.item_icon);
        badgeIconView = selfView.findViewById(R.id.item_badge_icon);
        badgeProgressView = selfView.findViewById(R.id.item_badge_progress);
        pathTextView = selfView.findViewById(R.id.item_name);
        indexTextView = selfView.findViewById(R.id.item_index);
        indexTextView.setVisibility(View.VISIBLE);
    }

    public void setIndex(int index) {
        indexTextView.setText(String.valueOf(index));
    }

    public void setIcon(int resId) {
        iconImageView.setImageResource(resId);
    }

    public void setSelected(boolean selected) {
        setBadge(selected ? BadgeState.SELECTED : BadgeState.NONE);
    }

    public void setBadge(BadgeState badgeState) {
        if (badgeState == BadgeState.RUNNING) {
            badgeProgressView.setBackgroundResource(R.drawable.ic_none_yellow_16);
            badgeProgressView.setVisibility(View.VISIBLE);
            badgeIconView.setVisibility(View.GONE);
            return;
        }

        badgeProgressView.setVisibility(View.GONE);
        switch (badgeState) {
            case SELECTED:
                badgeIconView.setImageResource(R.drawable.i_check_16);
                badgeIconView.setVisibility(View.VISIBLE);
                break;
            case STOPPED:
                badgeIconView.setImageResource(R.drawable.ic_prohibition_yellow_16);
                badgeIconView.setVisibility(View.VISIBLE);
                break;
            case DONE:
                badgeIconView.setImageResource(R.drawable.ic_check_green_16);
                badgeIconView.setVisibility(View.VISIBLE);
                break;
            case FAILED:
                badgeIconView.setImageResource(R.drawable.ic_cross_red_16);
                badgeIconView.setVisibility(View.VISIBLE);
                break;
            default:
                badgeIconView.setVisibility(View.GONE);
                break;
        }
    }

    public void setPath(CharSequence path) {
        pathTextView.setText(path);
    }

    public void forwardPathViewClicksTo(View itemView) {
        forwardViewActionsTo(pathTextView, itemView);
    }

    public enum BadgeState {
        NONE,
        SELECTED,
        RUNNING,
        STOPPED,
        DONE,
        FAILED,
    }
}
