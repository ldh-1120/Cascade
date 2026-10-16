package dev.dh.cascade.animation;

import dev.dh.cascade.bedrock.BedrockAnimation;

import java.util.Objects;

public class AnimationPlayer {
    private static final float TICKS_PER_SECOND = 20.0f;

    private String currentAnimation;
    private String previousAnimation;

    private float anchorTick;
    private float currentSeconds;
    private float previousSeconds;

    private float blendSeconds;
    private float blendDurationSeconds;

    private float speed = 1.0f;
    private boolean paused;

    public void play(String name, float nowTick) {
        validateName(name);
        validateTick(nowTick);

        currentAnimation = name;
        previousAnimation = null;

        anchorTick = nowTick;
        currentSeconds = 0.0f;
        previousSeconds = 0.0f;

        blendSeconds = 0.0f;
        blendDurationSeconds = 0.0f;
        paused = false;
    }

    public void playIfChanged(String name, float nowTick) {
        if (!Objects.equals(currentAnimation, name))
            play(name, nowTick);
    }

    public void crossFade(String name, float nowTick, float durationSeconds) {
        validateName(name);
        validateTick(nowTick);

        if (!Float.isFinite(durationSeconds) || durationSeconds < 0.0f)
            throw new IllegalArgumentException("Invalid transition duration");

        if (Objects.equals(currentAnimation, name))
            return;

        if (!isPlaying() || durationSeconds == 0.0f) {
            play(name, nowTick);
            return;
        }

        rebase(nowTick);

        previousAnimation = currentAnimation;
        previousSeconds = currentSeconds;

        currentAnimation = name;
        currentSeconds = 0.0f;

        blendSeconds = 0.0f;
        blendDurationSeconds = durationSeconds;
        anchorTick = nowTick;
    }

    public void stop() {
        currentAnimation = null;
        previousAnimation = null;

        currentSeconds = 0.0f;
        previousSeconds = 0.0f;

        blendSeconds = 0.0f;
        blendDurationSeconds = 0.0f;
        paused = false;
    }

    public void pause(float nowTick) {
        if (!isPlaying() || paused)
            return;

        rebase(nowTick);
        paused = true;
    }

    public void resume(float nowTick) {
        validateTick(nowTick);

        if (!isPlaying() || !paused)
            return;

        anchorTick = nowTick;
        paused = false;
    }

    public void setSpeed(float newSpeed, float nowTick) {
        validateTick(nowTick);

        if (!Float.isFinite(newSpeed) || newSpeed < 0.0f)
            throw new IllegalArgumentException("Invalid speed");

        if (isPlaying())
            rebase(nowTick);

        speed = newSpeed;
    }

    public float getTimeSeconds(float nowTick) {
        validateTick(nowTick);

        if (!isPlaying())
            return 0.0f;

        return currentSeconds + deltaSeconds(nowTick) * speed;
    }

    public Frame sampleFrame(float nowTick) {
        validateTick(nowTick);

        if (!isPlaying())
            return new Frame(null, 0.0f, null, 0.0f, 1.0f);

        float delta = deltaSeconds(nowTick);
        float currentTime = currentSeconds + delta * speed;
        if (previousAnimation == null)
            return new Frame(currentAnimation, currentTime, null, 0.0f, 1.0f);

        float progress = (blendSeconds + delta) / blendDurationSeconds;
        if (progress >= 1.0f)
            return new Frame(currentAnimation, currentTime, null, 0.0f, 1.0f);

        float previousTime = previousSeconds + delta * speed;

        return new Frame(currentAnimation, currentTime, previousAnimation, previousTime, Math.max(0.0f, progress));
    }

    public boolean isFinished(AnimationClip clip, float nowTick) {
        if (!isPlaying() || clip == null)
            return false;

        if (clip.loop() != BedrockAnimation.LoopMode.ONCE)
            return false;

        return getTimeSeconds(nowTick) >= clip.length();
    }

    public boolean isPlaying() {
        return currentAnimation != null;
    }

    public boolean isPaused() {
        return paused;
    }

    public String currentAnimation() {
        return currentAnimation;
    }

    public float speed() {
        return speed;
    }

    private void rebase(float nowTick) {
        validateTick(nowTick);

        float delta = deltaSeconds(nowTick);
        currentSeconds += delta * speed;

        if (previousAnimation != null) {
            previousSeconds += delta * speed;
            blendSeconds += delta;

            if (blendSeconds >= blendDurationSeconds)
                previousAnimation = null;
        }

        anchorTick = nowTick;
    }

    private float deltaSeconds(float nowTick) {
        if (paused)
            return 0.0f;

        return Math.max(0.0f, nowTick - anchorTick) / TICKS_PER_SECOND;
    }

    private static void validateName(String name) {
        Objects.requireNonNull(name, "Animation name");

        if (name.isBlank())
            throw new IllegalArgumentException("Animation name cannot be empty");
    }

    private static void validateTick(float nowTick) {
        if (!Float.isFinite(nowTick))
            throw new IllegalArgumentException("Invalid game tick");
    }

    public record Frame(String currentAnimation, float currentTime, String previousAnimation, float previousTime, float blendWeight) { }
}
