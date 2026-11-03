package pd.droidapp.glry;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.appcompat.widget.AppCompatImageView;

public class ZoomableImageView extends AppCompatImageView {

    // max zoom: 2x the larger of the screen/bitmap ratio per axis and 1
    private static final float MAX_ZOOM = 2f;
    private static final long ZOOM_ANIMATION_DURATION = 200;
    private static final float DEFAULT_ROTATION = 0f;

    private final Matrix matrix = new Matrix();
    private final RectF contentRect = new RectF();
    private final float[] matrixValues = new float[9];
    private final ScaleGestureDetector scaleDetector;
    private final RotationGestureDetector rotationDetector;
    private final GestureDetector gestureDetector;

    private int contentWidth;
    private int contentHeight;
    private float defaultScale = 1f;
    private float minScale = 1f;
    private float maxScale = 1f;
    private ValueAnimator zoomAnimator;

    public ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = clampScaleFactor(detector.getScaleFactor());
                if (factor != 1f) {
                    matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                    applyMatrix();
                }
                return true;
            }
        });

        rotationDetector = new RotationGestureDetector(
                ViewConfiguration.get(context).getScaledTouchSlop(),
                (delta, focusX, focusY) -> {
                    matrix.postRotate(delta, focusX, focusY);
                    applyMatrix();
                });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (scaleDetector.isInProgress() || !canPan()) {
                    return false;
                }
                matrix.postTranslate(-distanceX, -distanceY);
                applyMatrix();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                animateZoom(isAtDefault() ? 1f : defaultScale, e.getX(), e.getY());
                return true;
            }
        });
    }

    public void setDrawable(Drawable drawable) {
        contentWidth = drawable.getIntrinsicWidth();
        contentHeight = drawable.getIntrinsicHeight();
        setImageDrawable(drawable);
        if (drawable instanceof Animatable) {
            ((Animatable) drawable).start();
        }
        resetToDefault();
    }

    // show content at 1:1, anchored at the content point under the view center
    public void setDrawableAtActualSize(Drawable drawable) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        float anchorX = width / 2f;
        float anchorY = height / 2f;
        if (contentWidth > 0 && contentHeight > 0 && getWidth() > 0 && getHeight() > 0) {
            float[] anchor = new float[] {getWidth() / 2f, getHeight() / 2f};
            Matrix inverse = new Matrix();
            if (matrix.invert(inverse)) {
                inverse.mapPoints(anchor);
                anchorX = anchor[0] * width / contentWidth;
                anchorY = anchor[1] * height / contentHeight;
            }
        }
        float rotation = currentRotation();
        contentWidth = width;
        contentHeight = height;
        Drawable previous = getDrawable();
        if (previous instanceof Animatable) {
            ((Animatable) previous).stop();
        }
        setImageDrawable(drawable);
        if (drawable instanceof Animatable) {
            ((Animatable) drawable).start();
        }
        if (width <= 0 || height <= 0 || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        updateLimits();
        matrix.reset();
        matrix.postTranslate(-anchorX, -anchorY);
        matrix.postRotate(rotation);
        matrix.postTranslate(getWidth() / 2f, getHeight() / 2f);
        setImageMatrix(matrix);
    }

    public void clear() {
        cancelZoomAnimation();
        Drawable drawable = getDrawable();
        if (drawable instanceof Animatable) {
            ((Animatable) drawable).stop();
        }
        contentWidth = 0;
        contentHeight = 0;
        setImageDrawable(null);
    }

    private void updateLimits() {
        if (contentWidth <= 0 || contentHeight <= 0 || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        float widthScale = (float) getWidth() / contentWidth;
        float heightScale = (float) getHeight() / contentHeight;
        defaultScale = Math.min(widthScale, heightScale);
        // min zoom: the shorter side shrinks to 1 screen pixel
        minScale = 1f / Math.min(contentWidth, contentHeight);
        // max(区域宽*2/位图宽, 2, 区域高*2/位图高)
        maxScale = MAX_ZOOM * Math.max(1f, Math.max(widthScale, heightScale));
    }

    public void resetToDefault() {
        cancelZoomAnimation();
        if (contentWidth <= 0 || contentHeight <= 0 || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        updateLimits();
        matrix.reset();
        matrix.postScale(defaultScale, defaultScale);
        matrix.postTranslate(
                (getWidth() - contentWidth * defaultScale) / 2f,
                (getHeight() - contentHeight * defaultScale) / 2f);
        setImageMatrix(matrix);
    }

    private float currentScale() {
        matrix.getValues(matrixValues);
        return (float) Math.hypot(matrixValues[Matrix.MSCALE_X], matrixValues[Matrix.MSKEW_Y]);
    }

    private float currentRotation() {
        matrix.getValues(matrixValues);
        return (float) Math.toDegrees(
                Math.atan2(matrixValues[Matrix.MSKEW_Y], matrixValues[Matrix.MSCALE_X]));
    }

    // exact comparison on purpose: zoom animations write the target scale/angle explicitly
    private boolean isAtDefault() {
        return currentScale() == defaultScale && currentRotation() == DEFAULT_ROTATION;
    }

    private float clampScaleFactor(float factor) {
        float current = currentScale();
        if (current <= 0f) {
            return 1f;
        }
        float proposed = current * factor;
        if (proposed < minScale) {
            return minScale / current;
        }
        if (proposed > maxScale) {
            return maxScale / current;
        }
        return factor;
    }

    private boolean canPan() {
        if (contentWidth <= 0 || contentHeight <= 0 || getWidth() == 0 || getHeight() == 0) {
            return false;
        }
        contentRect.set(0f, 0f, contentWidth, contentHeight);
        matrix.mapRect(contentRect);
        return contentRect.width() > getWidth() || contentRect.height() > getHeight();
    }

    private void animateZoom(float targetScale, float focusX, float focusY) {
        float startScale = currentScale();
        float startRotation = currentRotation();
        cancelZoomAnimation();
        zoomAnimator = ValueAnimator.ofFloat(0f, 1f);
        zoomAnimator.setDuration(ZOOM_ANIMATION_DURATION);
        zoomAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        zoomAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            float scale = startScale + (targetScale - startScale) * fraction;
            float rotation = startRotation * (1f - fraction);
            float factor = scale / currentScale();
            float rotationDelta = rotation - currentRotation();
            matrix.postScale(factor, factor, focusX, focusY);
            matrix.postRotate(rotationDelta, focusX, focusY);
            if (fraction == 1f) {
                matrix.getValues(matrixValues);
                matrixValues[Matrix.MSCALE_X] = targetScale;
                matrixValues[Matrix.MSCALE_Y] = targetScale;
                matrixValues[Matrix.MSKEW_X] = 0f;
                matrixValues[Matrix.MSKEW_Y] = 0f;
                matrix.setValues(matrixValues);
            }
            applyMatrix();
        });
        zoomAnimator.start();
    }

    private void cancelZoomAnimation() {
        if (zoomAnimator != null) {
            zoomAnimator.cancel();
            zoomAnimator = null;
        }
    }

    private void applyMatrix() {
        clampTranslation();
        setImageMatrix(matrix);
    }

    private void clampTranslation() {
        if (contentWidth <= 0 || contentHeight <= 0 || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        contentRect.set(0f, 0f, contentWidth, contentHeight);
        matrix.mapRect(contentRect);
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        float dx = 0f;
        float dy = 0f;
        if (contentRect.width() <= viewWidth) {
            dx = (viewWidth - contentRect.width()) / 2f - contentRect.left;
        } else if (contentRect.left > 0f) {
            dx = -contentRect.left;
        } else if (contentRect.right < viewWidth) {
            dx = viewWidth - contentRect.right;
        }
        if (contentRect.height() <= viewHeight) {
            dy = (viewHeight - contentRect.height()) / 2f - contentRect.top;
        } else if (contentRect.top > 0f) {
            dy = -contentRect.top;
        } else if (contentRect.bottom < viewHeight) {
            dy = viewHeight - contentRect.bottom;
        }
        if (dx != 0f || dy != 0f) {
            matrix.postTranslate(dx, dy);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetToDefault();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Drawable drawable = getDrawable();
        if (drawable instanceof Animatable) {
            ((Animatable) drawable).start();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (contentWidth <= 0 || contentHeight <= 0) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelZoomAnimation();
                if (!isAtDefault()) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                // two fingers stay with pinch/rotate; three fingers page
                getParent().requestDisallowInterceptTouchEvent(event.getPointerCount() == 2);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
            default:
                break;
        }
        scaleDetector.onTouchEvent(event);
        rotationDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelZoomAnimation();
        Drawable drawable = getDrawable();
        if (drawable instanceof Animatable) {
            ((Animatable) drawable).stop();
        }
        super.onDetachedFromWindow();
    }
}
