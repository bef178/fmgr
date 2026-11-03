package pd.droidapp.glry;

import android.view.MotionEvent;

final class RotationGestureDetector {

    private final OnRotationListener listener;
    private final float touchSlop;
    private float previousAngle;
    private float accumulatedAngle;
    private boolean tracking;
    private boolean rotating;

    RotationGestureDetector(float touchSlop, OnRotationListener listener) {
        this.touchSlop = touchSlop;
        this.listener = listener;
    }

    void onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() >= 2) {
                    previousAngle = angle(event);
                    accumulatedAngle = 0f;
                    tracking = true;
                    rotating = false;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (tracking && event.getPointerCount() >= 2) {
                    float angle = angle(event);
                    float delta = angle - previousAngle;
                    if (delta > 180f) {
                        delta -= 360f;
                    } else if (delta < -180f) {
                        delta += 360f;
                    }
                    previousAngle = angle;
                    if (!rotating) {
                        accumulatedAngle += delta;
                        if (Math.abs(accumulatedAngle) < rotationSlop(event)) {
                            break;
                        }
                        rotating = true;
                    }
                    if (delta != 0f) {
                        listener.onRotation(delta,
                                (event.getX(0) + event.getX(1)) / 2f,
                                (event.getY(0) + event.getY(1)) / 2f);
                    }
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                tracking = false;
                rotating = false;
                break;
            default:
                break;
        }
    }

    private float rotationSlop(MotionEvent event) {
        float dx = event.getX(1) - event.getX(0);
        float dy = event.getY(1) - event.getY(0);
        float span = Math.max((float) Math.hypot(dx, dy), touchSlop);
        return (float) Math.toDegrees(2 * Math.atan(touchSlop / span));
    }

    private float angle(MotionEvent event) {
        float dx = event.getX(1) - event.getX(0);
        float dy = event.getY(1) - event.getY(0);
        return (float) Math.toDegrees(Math.atan2(dy, dx));
    }

    interface OnRotationListener {
        void onRotation(float delta, float focusX, float focusY);
    }
}
