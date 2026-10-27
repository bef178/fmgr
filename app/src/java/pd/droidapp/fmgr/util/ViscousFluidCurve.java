package pd.droidapp.fmgr.util;

public final class ViscousFluidCurve {

    private static final float SCALE = 8f;
    private static final float E = (float) Math.exp(-1);
    private static final float NORMALIZE = 1f / position(1f);
    private static final float PEAK_AT = 0.125f;
    private static final float PEAK = velocity(PEAK_AT);

    private ViscousFluidCurve() {
    }

    public static float riseVelocity(float t) {
        return velocity(t * PEAK_AT) / PEAK;
    }

    public static float tailVelocity(float t) {
        return velocity(PEAK_AT + t * (1f - PEAK_AT)) / PEAK;
    }

    private static float position(float t) {
        float x = t * SCALE;
        if (x < 1f) {
            return x - (1f - (float) Math.exp(-x));
        }
        x = 1f - (float) Math.exp(1f - x);
        return E + x * (1f - E);
    }

    private static float velocity(float t) {
        float x = t * SCALE;
        float slope = x < 1f
                ? 1f - (float) Math.exp(-x)
                : (1f - E) * (float) Math.exp(1f - x);
        return SCALE * NORMALIZE * slope;
    }
}
