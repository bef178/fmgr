package pd.droidapp.fmgr.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class Progressor<TKey> {

    private static final float START_TIME = 0f;
    private static final float END_TIME = 1f;

    private final Map<TKey, ValueAnimator> animators = new HashMap<>();
    private final Map<TKey, Float> times = new HashMap<>();

    public void start(TKey key, long duration, BiConsumer<Float, Float> onUpdated) {
        start(key, duration, new LinearInterpolator(), onUpdated);
    }

    public void start(TKey key, long duration, TimeInterpolator interpolator, BiConsumer<Float, Float> onUpdated) {
        if (key == null) {
            return;
        }

        cancel(key);

        ValueAnimator animator = ValueAnimator.ofFloat(START_TIME, END_TIME);
        animator.setDuration(duration);
        animator.setInterpolator(interpolator);

        animator.addUpdateListener(a -> {
            float time = a.getAnimatedFraction();
            times.put(key, time);
            float distance = (float) a.getAnimatedValue();
            float velocity = calculateVelocity(a.getInterpolator(), time);
            onUpdated.accept(distance, velocity);
        });

        animator.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationStart(Animator a) {
                times.put(key, START_TIME);
                TimeInterpolator interpolator = a.getInterpolator();
                float distance = interpolator.getInterpolation(START_TIME);
                float velocity = calculateVelocity(interpolator, START_TIME);
                onUpdated.accept(distance, velocity);
            }

            @Override
            public void onAnimationEnd(Animator a) {
                // atomic operation
                if (animators.remove(key, a)) {
                    times.remove(key);
                    TimeInterpolator interpolator = a.getInterpolator();
                    float distance = interpolator.getInterpolation(END_TIME);
                    float velocity = calculateVelocity(interpolator, END_TIME);
                    onUpdated.accept(distance, velocity);
                }
            }
        });
        animators.put(key, animator);
        animator.start();
    }

    // `time` in [0,1]
    private float calculateVelocity(TimeInterpolator interpolator, float time) {
        final float delta = 0.001f;

        // boundary
        float t1 = Math.max(0f, Math.min(1f, time - delta));
        float t2 = Math.max(0f, Math.min(1f, time + delta));

        float d1 = interpolator.getInterpolation(t1);
        float d2 = interpolator.getInterpolation(t2);

        // 中心差分法求导
        return (d2 - d1) / (t2 - t1);
    }

    public void cancel(TKey key) {
        ValueAnimator animator = animators.remove(key);
        if (animator != null) {
            animator.cancel();
        }
    }

    public Float getDistance(TKey key) {
        Animator a = animators.get(key);
        if (a == null) {
            return null;
        }
        Float time = times.get(key);
        if (time == null) {
            time = END_TIME;
        }
        return a.getInterpolator().getInterpolation(time);
    }

    public Float getVelocity(TKey key) {
        Animator a = animators.get(key);
        if (a == null) {
            return null;
        }
        Float time = times.get(key);
        if (time == null) {
            time = END_TIME;
        }
        return calculateVelocity(a.getInterpolator(), time);
    }
}
