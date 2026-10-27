package pd.droidapp.fmgr.util;

import android.animation.TimeInterpolator;
import android.view.Choreographer;

import java.util.function.BiConsumer;

public class HoldScrollProgressor {

    private static final float MAX_FRAME_TIME = 0.05f;

    private final TimeInterpolator pressingVelocity;
    private final TimeInterpolator holdingVelocity;
    private final TimeInterpolator releasingVelocity;
    private final float peakVelocity;
    private final long pressingDuration;
    private final long releasingDuration;
    private final BiConsumer<Float, Float> onUpdated;
    private final Choreographer.FrameCallback frameCallback = this::doFrame;

    private boolean running;
    private boolean releasing;
    private long phaseStart;
    private long lastFrame;
    private float releasingStartVelocity;
    private float distance;
    private float velocity;

    public HoldScrollProgressor(TimeInterpolator pressingVelocity,
            TimeInterpolator holdingVelocity, TimeInterpolator releasingVelocity,
            float peakVelocity, long pressingDuration, long releasingDuration,
            BiConsumer<Float, Float> onUpdated) {
        this.pressingVelocity = pressingVelocity;
        this.holdingVelocity = holdingVelocity;
        this.releasingVelocity = releasingVelocity;
        this.peakVelocity = peakVelocity;
        this.pressingDuration = pressingDuration;
        this.releasingDuration = releasingDuration;
        this.onUpdated = onUpdated;
    }

    public void press() {
        cancel();
        running = true;
        releasing = false;
        distance = 0f;
        velocity = 0f;
        phaseStart = System.nanoTime();
        lastFrame = phaseStart;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    public void release() {
        if (!running || releasing) {
            return;
        }
        if (velocity == 0f) {
            cancel();
            return;
        }
        releasing = true;
        releasingStartVelocity = velocity;
        phaseStart = System.nanoTime();
        lastFrame = phaseStart;
    }

    public void cancel() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
    }

    private void doFrame(long frameTimeNanos) {
        if (!running) {
            return;
        }
        float dt = Math.min((frameTimeNanos - lastFrame) / 1e9f, MAX_FRAME_TIME);
        lastFrame = frameTimeNanos;
        float elapsed = (frameTimeNanos - phaseStart) / 1e6f;

        if (!releasing) {
            float t = elapsed / pressingDuration;
            velocity = peakVelocity * (t < 1f
                    ? pressingVelocity.getInterpolation(t)
                    : holdingVelocity.getInterpolation(1f));
        } else {
            float t = elapsed / releasingDuration;
            if (t >= 1f) {
                velocity = 0f;
                running = false;
                onUpdated.accept(distance, velocity);
                return;
            }
            velocity = releasingStartVelocity * releasingVelocity.getInterpolation(t);
        }
        distance += velocity * dt;
        onUpdated.accept(distance, velocity);
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }
}
