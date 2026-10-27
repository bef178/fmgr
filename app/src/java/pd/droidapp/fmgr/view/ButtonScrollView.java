package pd.droidapp.fmgr.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.HoldScrollProgressor;
import pd.droidapp.fmgr.util.ViscousFluidCurve;

public class ButtonScrollView extends HorizontalScrollView {

    private static final float BUTTON_SIZE_DP = 24;
    private static final float BUTTON_INSET_DP = 14;
    private static final float BUTTON_GAP_DP = 4;

    private final ImageButton leftButton;
    private final ImageButton rightButton;
    private final HoldScrollProgressor holdScrollProgressor;
    private final Runnable updateVisibilityRunnable = this::updateVisibility;

    private int scrollStartX;
    private int scrollDirection;

    public ButtonScrollView(@NonNull Context context) {
        this(context, null);
    }

    public ButtonScrollView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ButtonScrollView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ColorStateList buttonTint;
        try (TypedArray a = context.obtainStyledAttributes(attrs,
                new int[] { android.R.attr.buttonTint }, defStyleAttr, 0)) {
            buttonTint = a.getColorStateList(0);
        }
        leftButton = createButton(R.drawable.ic_rt_triangle_left_24, -1, buttonTint);
        rightButton = createButton(R.drawable.ic_rt_triangle_right_24, 1, buttonTint);
        holdScrollProgressor = new HoldScrollProgressor(
                ViscousFluidCurve::riseVelocity,
                t -> 1f,
                ViscousFluidCurve::tailVelocity,
                1f, 30, 220,
                (distance, velocity) -> scrollTo(
                        scrollStartX + scrollDirection * Math.round(distance * getWidth()), 0));
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // HorizontalScrollView: addView allows only one sub view
        int buttonSize = getButtonSize();
        addViewInLayout(leftButton, -1, new FrameLayout.LayoutParams(buttonSize, buttonSize), true);
        addViewInLayout(rightButton, -1, new FrameLayout.LayoutParams(buttonSize, buttonSize), true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getPaddingLeft() + getPaddingRight();
        int height = getPaddingTop() + getPaddingBottom();
        if (getChildCount() > 0) {
            View content = getChildAt(0);
            measureChild(content, widthMeasureSpec, heightMeasureSpec);
            width += Math.max(0, content.getMeasuredWidth());
            height += Math.max(0, content.getMeasuredHeight());
        }
        int buttonSize = getButtonSize();
        int buttonSpec = MeasureSpec.makeMeasureSpec(buttonSize, MeasureSpec.EXACTLY);
        leftButton.measure(buttonSpec, buttonSpec);
        rightButton.measure(buttonSpec, buttonSpec);
        setMeasuredDimension(
                resolveSize(width, widthMeasureSpec),
                resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int buttonSize = getButtonSize();
        int buttonTop = (getHeight() - buttonSize) / 2;
        leftButton.layout(0, buttonTop, buttonSize, buttonTop + buttonSize);
        rightButton.layout(getWidth() - buttonSize, buttonTop, getWidth(), buttonTop + buttonSize);
        updateButtons();
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        updateButtons();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (getChildCount() == 0) {
            super.dispatchDraw(canvas);
            return;
        }
        View content = getChildAt(0);
        int scrollX = getScrollX();
        int inset = getButtonInset() + getButtonGap();
        float clipLeft = scrollX;
        float clipRight = scrollX + getWidth();
        if (leftButton.getVisibility() == VISIBLE) {
            clipLeft = scrollX + inset;
        }
        if (rightButton.getVisibility() == VISIBLE) {
            clipRight = scrollX + getWidth() - inset;
        }
        int saveCount = canvas.save();
        canvas.clipRect(clipLeft, 0, clipRight, getHeight());
        drawChild(canvas, content, getDrawingTime());
        canvas.restoreToCount(saveCount);
        if (leftButton.getVisibility() == VISIBLE) {
            drawChild(canvas, leftButton, getDrawingTime());
        }
        if (rightButton.getVisibility() == VISIBLE) {
            drawChild(canvas, rightButton, getDrawingTime());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(updateVisibilityRunnable);
        holdScrollProgressor.cancel();
    }

    private ImageButton createButton(int iconRes, int direction, ColorStateList tint) {
        ScrollButton button = new ScrollButton(getContext(), direction);
        button.setHapticFeedbackEnabled(false);
        button.setImageResource(iconRes);
        if (tint != null) {
            button.setImageTintList(tint);
        }
        button.setBackground(getBorderlessBackground(getContext()));
        button.setVisibility(GONE);
        return button;
    }

    private void updateButtons() {
        int scrollX = getScrollX();
        leftButton.setTranslationX(scrollX);
        rightButton.setTranslationX(scrollX);
        if (isInLayout()) {
            removeCallbacks(updateVisibilityRunnable);
            post(updateVisibilityRunnable);
        } else {
            updateVisibility();
        }
    }

    private void updateVisibility() {
        int scrollX = getScrollX();
        int max = getMaxScrollX();
        leftButton.setVisibility(scrollX > 0 ? VISIBLE : GONE);
        rightButton.setVisibility(scrollX < max ? VISIBLE : GONE);
    }

    private int getMaxScrollX() {
        if (getChildCount() == 0) {
            return 0;
        }
        int viewport = getWidth() - getPaddingLeft() - getPaddingRight();
        return Math.max(0, getChildAt(0).getWidth() - viewport);
    }

    private int getButtonSize() {
        return Math.round(BUTTON_SIZE_DP * getResources().getDisplayMetrics().density);
    }

    private int getButtonInset() {
        return Math.round(BUTTON_INSET_DP * getResources().getDisplayMetrics().density);
    }

    private int getButtonGap() {
        return Math.round(BUTTON_GAP_DP * getResources().getDisplayMetrics().density);
    }

    private static Drawable getBorderlessBackground(Context context) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, value, true);
        return AppCompatResources.getDrawable(context, value.resourceId);
    }

    private class ScrollButton extends ImageButton {

        private final int direction;

        ScrollButton(Context context, int direction) {
            super(context);
            this.direction = direction;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    scrollDirection = direction;
                    scrollStartX = getScrollX();
                    holdScrollProgressor.press();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    holdScrollProgressor.release();
                    break;
            }
            return super.onTouchEvent(event);
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }
    }
}
