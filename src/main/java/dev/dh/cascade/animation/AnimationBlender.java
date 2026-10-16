package dev.dh.cascade.animation;

import dev.dh.cascade.model.SkeletonPose;

import java.util.Map;

public final class AnimationBlender {
    public static void apply(AnimationPlayer.Frame frame, Map<String, AnimationClip> clips, SkeletonPose output, SkeletonPose previousBuffer, SkeletonPose currentBuffer) {
        if (frame.currentAnimation() == null) {
            output.reset();
            return;
        }

        AnimationClip current = clips.get(frame.currentAnimation());
        if (current == null) {
            output.reset();
            return;
        }

        if (frame.previousAnimation() == null || frame.blendWeight() >= 1.0f) {
            AnimationSampler.apply(current, output, frame.currentTime());
            return;
        }

        AnimationClip previous = clips.get(frame.previousAnimation());
        if (previous == null)
            previousBuffer.reset();
        else
            AnimationSampler.apply(previous, previousBuffer, frame.previousTime());

        AnimationSampler.apply(current, currentBuffer, frame.currentTime());
        output.blendFrom(previousBuffer, currentBuffer, frame.blendWeight());
    }
}
