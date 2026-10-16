package dev.dh.cascade.animation;

import dev.dh.cascade.bedrock.BedrockAnimation;
import dev.dh.cascade.model.SkeletonPose;
import org.joml.Vector3f;

import java.util.List;

public final class AnimationSampler {
    public static void apply(AnimationClip animation, SkeletonPose pose, float timeSeconds) {
        pose.reset();
        applyInto(animation, pose, timeSeconds);
    }

    public static void applyInto(AnimationClip animation, SkeletonPose pose, float timeSeconds) {
        if (!Float.isFinite(timeSeconds))
            throw new IllegalArgumentException("Invalid animation time");

        float time = normalizeTime(animation, timeSeconds);
        for (AnimationClip.BoneTrack track : animation.tracks()) {
            String name = track.name();
            if (!track.position().isEmpty()) {
                Vector3f value = sample(track.position(), time);
                pose.setTranslation(name, value.x(), value.y(), value.z());
            }

            if (!track.rotation().isEmpty()) {
                Vector3f value = sample(track.rotation(), time);
                pose.setRotationDegrees(name, value.x(), value.y(), value.z());
            }

            if (!track.scale().isEmpty()) {
                Vector3f value = sample(track.scale(), time);
                pose.setScale(name, value.x(), value.y(), value.z());
            }
        }
    }

    private static float normalizeTime(AnimationClip animation, float time) {
        float length = animation.length();
        if (length <= 0.0f)
            return 0.0f;

        if (animation.loop() == BedrockAnimation.LoopMode.LOOP) {
            float wrapped = time % length;
            return wrapped < 0.0f ? wrapped + length : wrapped;
        }

        return Math.max(0.0f, Math.min(time, length));
    }

    private static Vector3f sample(List<AnimationClip.Keyframe> frames, float time) {
        AnimationClip.Keyframe first = frames.get(0);
        if (time < first.time())
            return first.pre();

        if (time == first.time())
            return first.post();

        int lastIndex = frames.size() - 1;
        AnimationClip.Keyframe last = frames.get(lastIndex);
        if (time >= last.time())
            return last.post();

        int leftIndex = findPreviousFrame(frames, time);

        AnimationClip.Keyframe current = frames.get(leftIndex);
        AnimationClip.Keyframe next = frames.get(leftIndex + 1);
        if (time == current.time())
            return current.post();

        float duration = next.time() - current.time();
        float alpha = (time - current.time()) / duration;

        Vector3f from = current.post();
        Vector3f to = next.pre();
        if (current.interpolation() == BedrockAnimation.Interpolation.LINEAR)
            return lerp(from, to, alpha);

        Vector3f previous = leftIndex > 0 ? frames.get(leftIndex - 1).post() : from;
        Vector3f following = leftIndex + 2 < frames.size() ? frames.get(leftIndex + 2).pre() : to;

        return catmullRom(previous, from, to, following, alpha);
    }

    private static int findPreviousFrame(List<AnimationClip.Keyframe> frames, float time) {
        int low = 0;
        int high = frames.size() - 1;
        while (low + 1 < high) {
            int middle = low + (high - low) / 2;
            if (frames.get(middle).time() <= time)
                low = middle;
            else
                high = middle;
        }

        return low;
    }

    private static Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t);
    }

    private static Vector3f catmullRom(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
        return new Vector3f(catmull(p0.x(), p1.x(), p2.x(), p3.x(), t), catmull(p0.y(), p1.y(), p2.y(), p3.y(), t), catmull(p0.z(), p1.z(), p2.z(), p3.z(), t));
    }

    private static float catmull(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;

        return 0.5f * (2.0f * p1 + (-p0 + p2) * t + (2.0f * p0 - 5.0f * p1 + 4.0f * p2 - p3) * t2 + (-p0 + 3.0f * p1 - 3.0f * p2 + p3) * t3);
    }
}
