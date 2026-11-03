package pd.droidapp.glry;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Map;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.popup.ProcessingPopup;
import pd.droidapp.fmgr.view.ButtonState;
import pd.droidapp.fmgr.view.PopupBottomBar;
import pd.droidapp.fmgr.view.PopupTitleBar;

public class InfoPopup extends ProcessingPopup {

    private final List<Map.Entry<String, String>> rows;

    public InfoPopup(View containerView, List<Map.Entry<String, String>> rows) {
        super(containerView);
        this.rows = rows;
        bottomBar.whenButtonClicked(id -> {
            if (id == R.drawable.ic_checkmark_24) {
                selfDialog.dismiss();
            }
        });
    }

    @Override
    protected void inflateContentView() {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) contentView.getLayoutParams();
        params.width = context.getResources().getDimensionPixelSize(R.dimen.processing_popup_width);
        params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
        contentView.setLayoutParams(params);
        LayoutInflater.from(context).inflate(R.layout.info_popup_content, contentView, true);
    }

    @Override
    protected void onShow() {
        titleBar.render(new PopupTitleBar.State(context.getString(R.string.info)));
        LinearLayout rowsView = selfView.findViewById(R.id.info_popup_rows);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        Paint keyPaint = new Paint();
        keyPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16, metrics));
        keyPaint.setTypeface(Typeface.DEFAULT_BOLD);
        int keyWidth = 0;
        for (Map.Entry<String, String> row : rows) {
            keyWidth = Math.max(keyWidth, (int) Math.ceil(keyPaint.measureText(row.getKey())));
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        for (Map.Entry<String, String> row : rows) {
            View rowView = inflater.inflate(R.layout.info_popup_item, rowsView, false);
            TextView keyView = rowView.findViewById(R.id.info_popup_key);
            keyView.setText(row.getKey());
            keyView.setWidth(keyWidth);
            ((TextView) rowView.findViewById(R.id.info_popup_value)).setText(row.getValue());
            rowsView.addView(rowView);
        }

        bottomBar.render(new PopupBottomBar.State(
                new ButtonState(R.drawable.ic_checkmark_24)));
    }

    @Override
    protected void setupDialogWindow(@NonNull Window window) {
        window.setGravity(Gravity.CENTER);
    }

    @Override
    protected void onDismissing(Runnable continueDismiss) {
        continueDismiss.run();
    }

    @Override
    protected void onDismissed() {
    }
}
