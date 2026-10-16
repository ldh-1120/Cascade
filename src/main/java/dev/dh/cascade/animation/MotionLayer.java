package dev.dh.cascade.animation;

import java.util.Objects;

public final class MotionLayer {
    public enum Mode {
        OVERRIDE,
        ADDITIVE
    }

    private final AnimationPlayer player = new AnimationPlayer();
    private final BoneMask mask;
    private final Mode mode;

    private double anchorTick;
    private double clockSeconds;

    private double fadeStartSeconds;
    private double fadeDurationSeconds;

    private float startWeight;
    private float targetWeight;

    private boolean paused;

    public MotionLayer(BoneMask mask, Mode mode) {
        this.mask = Objects.requireNonNull(mask);
        this.mode = Objects.requireNonNull(mode);
    }

    public AnimationPlayer player() {
        return player;
    }

    public BoneMask mask() {
        return mask;
    }

    public Mode mode() {
        return mode;
    }

    public void setWeight(float weight, double nowTick) {
        fadeTo(weight, nowTick, 0.0f);
    }

    public void fadeTo(float weight, double nowTick, float duration) {
        validateTick(nowTick);

        if (!Float.isFinite(weight) || weight < 0.0f || weight > 1.0f)
            throw new IllegalArgumentException("Invalid layer weight");

        if (!Float.isFinite(duration) || duration < 0.0f)
            throw new IllegalArgumentException("Invalid fade duration");

        double now = clock(nowTick);
        float current = weightAtTime(now);

        clockSeconds = now;
        anchorTick = nowTick;

        startWeight = current;
        targetWeight = weight;

        fadeStartSeconds = now;
        fadeDurationSeconds = duration;
    }

    public float weightAt(double nowTick) {
        validateTick(nowTick);
        return weightAtTime(clock(nowTick));
    }

    public void pause(double nowTick) {
        validateTick(nowTick);

        if (paused)
            return;

        clockSeconds = clock(nowTick);
        anchorTick = nowTick;
        paused = true;

        player.pause(nowTick);
    }

    public void resume(double nowTick) {
        validateTick(nowTick);

        if (!paused)
            return;

        anchorTick = nowTick;
        paused = false;

        player.resume(nowTick);
    }

    private float weightAtTime(double now) {
        if (fadeDurationSeconds <= 0.0)
            return targetWeight;

        double alpha = (now - fadeStartSeconds) / fadeDurationSeconds;
        alpha = Math.max(0.0, Math.min(1.0, alpha));

        return (float) (startWeight + (targetWeight - startWeight) * alpha);
    }

    private double clock(double nowTick) {
        if (paused)
            return clockSeconds;

        return clockSeconds + Math.max(0.0, nowTick - anchorTick) / 20.0;
    }

    private static void validateTick(double tick) {
        if (!Double.isFinite(tick))
            throw new IllegalArgumentException("Invalid game tick");
    }
}
