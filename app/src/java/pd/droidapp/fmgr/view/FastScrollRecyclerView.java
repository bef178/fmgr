package pd.droidapp.fmgr.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class FastScrollRecyclerView extends RecyclerView {

    private static final float TOUCH_WIDTH_DP = 24;
    private static final float MIN_TOUCH_HEIGHT_DP = 24;

    private final float density;
    private final Drawable thumbDrawable;
    private final int thumbWidth;
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF thumbRect = new RectF();
    private final boolean alwaysVisible;

    private final Runnable hideRunnable = this::fadeOut;
    private ValueAnimator fadeAnimator;
    private float barAlpha;

    private boolean dragging;
    private float dragFraction;
    private ItemAnimator dragItemAnimator;

    public FastScrollRecyclerView(@NonNull Context context) {
        this(context, null);
    }

    public FastScrollRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FastScrollRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        TypedArray a = context.obtainStyledAttributes(new int[] {
                android.R.attr.scrollbarThumbVertical,
                android.R.attr.scrollbarSize,
        });
        Drawable thumb = a.getDrawable(0);
        int size = a.getDimensionPixelSize(1, Math.round(4 * density));
        a.recycle();
        if (thumb != null && thumb.getIntrinsicWidth() > 0) {
            size = thumb.getIntrinsicWidth();
        }
        thumbDrawable = thumb;
        thumbWidth = Math.max(1, size);
        fallbackPaint.setColor(Color.BLACK);

        TypedArray custom = context.obtainStyledAttributes(attrs,
                new int[] { android.R.attr.fastScrollAlwaysVisible }, defStyleAttr, 0);
        alwaysVisible = custom.getBoolean(0, false);
        custom.recycle();

        barAlpha = alwaysVisible ? 1f : 0f;
        setVerticalScrollBarEnabled(false);

        if (!alwaysVisible) {
            addOnScrollListener(new OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (dy == 0) {
                        return;
                    }
                    showBar();
                    if (getScrollState() == SCROLL_STATE_IDLE) {
                        scheduleHide();
                    }
                }

                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (newState == SCROLL_STATE_IDLE) {
                        scheduleHide();
                    } else {
                        showBar();
                    }
                }
            });
        }
    }

    @Override
    public void onDrawForeground(@NonNull Canvas canvas) {
        super.onDrawForeground(canvas);
        if (barAlpha <= 0f || !isScrollable()) {
            return;
        }
        updateThumbRect();
        if (thumbDrawable != null) {
            thumbDrawable.setState(getDrawableState());
            thumbDrawable.setAlpha(Math.round(255 * barAlpha));
            thumbDrawable.setBounds((int) thumbRect.left, (int) thumbRect.top,
                    (int) thumbRect.right, (int) thumbRect.bottom);
            thumbDrawable.draw(canvas);
        } else {
            fallbackPaint.setAlpha(Math.round(0x66 * barAlpha));
            float radius = thumbWidth / 2f;
            canvas.drawRoundRect(thumbRect, radius, radius, fallbackPaint);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                && barAlpha > 0f && isScrollable()) {
            updateThumbRect();
            if (isOnThumb(event.getX(), event.getY())) {
                stopScroll();
                dragItemAnimator = getItemAnimator();
                setItemAnimator(null);
                dragging = true;
                dragFraction = fractionForY(event.getY());
                showBar();
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            }
        }
        return super.onInterceptTouchEvent(event);
    }

    // ClickableViewAccessibility: dragging the thumb has no click action;
    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent event) {
        if (!dragging) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                dragFraction = fractionForY(event.getY());
                scrollToFraction(dragFraction);
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                setItemAnimator(dragItemAnimator);
                dragItemAnimator = null;
                getParent().requestDisallowInterceptTouchEvent(false);
                scheduleHide();
                invalidate();
                break;
            default:
                break;
        }
        return true;
    }

    private void showBar() {
        if (alwaysVisible) {
            return;
        }
        removeCallbacks(hideRunnable);
        cancelFade();
        if (barAlpha < 1f) {
            barAlpha = 1f;
            invalidate();
        }
    }

    private void scheduleHide() {
        if (alwaysVisible || dragging) {
            return;
        }
        removeCallbacks(hideRunnable);
        postDelayed(hideRunnable, ViewConfiguration.getScrollDefaultDelay());
    }

    private void fadeOut() {
        if (alwaysVisible || dragging || barAlpha <= 0f) {
            return;
        }
        cancelFade();
        ValueAnimator animator = ValueAnimator.ofFloat(barAlpha, 0f);
        animator.setDuration(ViewConfiguration.getScrollBarFadeDuration());
        animator.addUpdateListener(a -> {
            barAlpha = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (fadeAnimator == animation) {
                    fadeAnimator = null;
                }
            }
        });
        fadeAnimator = animator;
        animator.start();
    }

    private void cancelFade() {
        if (fadeAnimator != null) {
            fadeAnimator.cancel();
            fadeAnimator = null;
        }
    }

    private boolean isScrollable() {
        return computeVerticalScrollRange() > computeVerticalScrollExtent();
    }

    private void updateThumbRect() {
        int range = computeVerticalScrollRange();
        int extent = computeVerticalScrollExtent();
        int offset = computeVerticalScrollOffset();
        int height = getHeight();
        int thumbHeight = Math.max(thumbWidth * 2, Math.round((float) height * extent / range));
        float fraction = dragging ? dragFraction : (float) offset / (range - extent);
        int thumbTop = Math.round((height - thumbHeight) * fraction);
        thumbRect.set(getWidth() - thumbWidth, thumbTop, getWidth(), thumbTop + thumbHeight);
    }

    private boolean isOnThumb(float x, float y) {
        float minHeight = MIN_TOUCH_HEIGHT_DP * density;
        float grabMargin = Math.max(0f, minHeight - thumbRect.height()) / 2f;
        return x >= getWidth() - TOUCH_WIDTH_DP * density
                && y >= thumbRect.top - grabMargin
                && y <= thumbRect.bottom + grabMargin;
    }

    private float fractionForY(float y) {
        int height = getHeight();
        int thumbHeight = (int) thumbRect.height();
        int travel = height - thumbHeight;
        if (travel <= 0) {
            return 0f;
        }
        float fraction = (y - thumbHeight / 2f) / travel;
        return Math.max(0f, Math.min(1f, fraction));
    }

    private void scrollToFraction(float fraction) {
        int range = computeVerticalScrollRange();
        int maxOffset = range - computeVerticalScrollExtent();
        if (maxOffset <= 0) {
            return;
        }
        int target = Math.round(fraction * maxOffset);
        LayoutManager layoutManager = getLayoutManager();
        Adapter<?> adapter = getAdapter();
        if (layoutManager instanceof LinearLayoutManager && adapter != null && adapter.getItemCount() > 0) {
            int itemCount = adapter.getItemCount();
            float avgRowHeight = (float) range / itemCount;
            int position = Math.min(itemCount - 1, (int) (target / avgRowHeight));
            int within = target - Math.round(position * avgRowHeight);
            ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(position, -within);
            return;
        }
        int delta = target - computeVerticalScrollOffset();
        if (delta != 0) {
            scrollBy(0, delta);
        }
    }
}
