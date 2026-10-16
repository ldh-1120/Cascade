package dev.dh.cascade.animation;

import dev.dh.cascade.bedrock.BedrockAnimation;

import java.util.Objects;

public class AnimationPlayer {
    private static final double TICKS_PER_SECOND = 20.0f;

    public sealed interface PoseNode permits ClipNode, BlendNode { }
    public record ClipNode(String animation, double startPlaybackSeconds) implements PoseNode { }
    public record BlendNode(PoseNode from, PoseNode to, double startTransitionSeconds, double durationSeconds) implements PoseNode { }
    public record Frame(PoseNode root, double playbackSeconds, double transitionSeconds) { }

    private PoseNode root;

    private String currentAnimation;
    private double currentStartSeconds;

    private double anchorTick;
    private double playbackClock;
    private double transitionClock;

    private float speed = 1.0f;
    private boolean paused;

    public void play(String name, double nowTick) {
        validateName(name);
        validateTick(nowTick);

        if (!isPlaying()) {
            playbackClock = 0.0;
            transitionClock = 0.0;
            anchorTick = nowTick;
        } else
            rebase(nowTick);

        paused = false;

        currentAnimation = name;
        currentStartSeconds = playbackClock;
        root = new ClipNode(name, playbackClock);
    }

    public void playIfChanged(String name, double nowTick) {
        if (!Objects.equals(currentAnimation, name))
            play(name, nowTick);
    }

    public void crossFade(String name, double nowTick, float durationSeconds) {
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

        root = prune(root, transitionClock);

        ClipNode target = new ClipNode(name, playbackClock);
        root = new BlendNode(root, target, transitionClock, durationSeconds);

        currentAnimation = name;
        currentStartSeconds = playbackClock;
    }

    public void stop() {
        root = null;
        currentAnimation = null;
        currentStartSeconds = 0.0;

        playbackClock = 0.0;
        transitionClock = 0.0;
        anchorTick = 0.0;

        paused = false;
    }

    public void pause(double nowTick) {
        validateTick(nowTick);

        if (!isPlaying() || paused)
            return;

        rebase(nowTick);
        paused = true;
    }

    public void resume(double nowTick) {
        validateTick(nowTick);

        if (!isPlaying() || !paused)
            return;

        anchorTick = nowTick;
        paused = false;
    }

    public void setSpeed(float newSpeed, double nowTick) {
        validateTick(nowTick);

        if (!Float.isFinite(newSpeed) || newSpeed < 0.0f)
            throw new IllegalArgumentException("Invalid speed");

        if (isPlaying())
            rebase(nowTick);

        speed = newSpeed;
    }

    public float getTimeSeconds(double nowTick) {
        validateTick(nowTick);

        if (!isPlaying())
            return 0.0f;

        return (float) Math.max(0.0, playbackTime(nowTick) - currentStartSeconds);
    }

    public Frame sampleFrame(double nowTick) {
        validateTick(nowTick);

        if (!isPlaying())
            return new Frame(null, 0.0, 0.0);

        return new Frame(root, playbackTime(nowTick), transitionTime(nowTick));
    }

    public boolean isFinished(AnimationClip clip, double nowTick) {
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

    private void rebase(double nowTick) {
        double delta = deltaSeconds(nowTick);

        playbackClock += delta * speed;
        transitionClock += delta;

        anchorTick = nowTick;
    }

    private double playbackTime(double nowTick) {
        return playbackClock + deltaSeconds(nowTick) * speed;
    }

    private double transitionTime(double nowTick) {
        return transitionClock + deltaSeconds(nowTick);
    }

    private double deltaSeconds(double nowTick) {
        if (paused)
            return 0.0;

        return Math.max(0.0, nowTick - anchorTick) / TICKS_PER_SECOND;
    }

    private static PoseNode prune(PoseNode node, double nowSeconds) {
        if (node instanceof ClipNode)
            return node;

        BlendNode blend = (BlendNode) node;

        double elapsed = nowSeconds - blend.startTransitionSeconds();
        if (elapsed >= blend.durationSeconds())
            return prune(blend.to(), nowSeconds);

        PoseNode from = prune(blend.from(), nowSeconds);
        PoseNode to = prune(blend.to(), nowSeconds);
        if (from == blend.from() && to == blend.to())
            return blend;

        return new BlendNode(from, to, blend.startTransitionSeconds(), blend.durationSeconds());
    }

    private static void validateName(String name) {
        Objects.requireNonNull(name, "Animation name");

        if (name.isBlank())
            throw new IllegalArgumentException("Animation name cannot be empty");
    }

    private static void validateTick(double tick) {
        if (!Double.isFinite(tick))
            throw new IllegalArgumentException("Invalid game tick");
    }
}
