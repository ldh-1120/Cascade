package dev.dh.cascade.animation;

import dev.dh.cascade.model.Skeleton;
import dev.dh.cascade.model.SkeletonPose;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AnimationBlender {
    private final Skeleton skeleton;
    private final List<SkeletonPose> buffers = new ArrayList<>();

    private int bufferIndex;

    public AnimationBlender(Skeleton skeleton) {
        this.skeleton = skeleton;
    }

    public void apply(AnimationPlayer.Frame frame, Map<String, AnimationClip> clips, SkeletonPose output) {
        if (output.skeleton() != skeleton)
            throw new IllegalArgumentException("Skeleton mismatch");

        bufferIndex = 0;
        if (frame.root() == null) {
            output.reset();
            return;
        }

        evaluate(frame.root(), frame.playbackSeconds(), frame.transitionSeconds(), clips, output);
    }

    private void evaluate(AnimationPlayer.PoseNode node, double playbackTime, double transitionTime, Map<String, AnimationClip> clips, SkeletonPose output) {
        if (node instanceof AnimationPlayer.ClipNode clipNode) {
            AnimationClip clip = clips.get(clipNode.animation());
            if (clip == null) {
                output.reset();
                return;
            }

            float seconds = (float) Math.max(0.0, playbackTime - clipNode.startPlaybackSeconds());
            AnimationSampler.apply(clip, output, seconds);

            return;
        }

        AnimationPlayer.BlendNode blend = (AnimationPlayer.BlendNode) node;
        double elapsed = transitionTime - blend.startTransitionSeconds();

        double alpha = elapsed / blend.durationSeconds();
        if (alpha <= 0.0) {
            evaluate(blend.from(), playbackTime, transitionTime, clips, output);
            return;
        }

        if (alpha >= 1.0) {
            evaluate(blend.to(), playbackTime, transitionTime, clips, output);
            return;
        }

        int savedIndex = bufferIndex;

        SkeletonPose from = acquireBuffer();
        SkeletonPose to = acquireBuffer();

        evaluate(blend.from(), playbackTime, transitionTime, clips, from);
        evaluate(blend.to(), playbackTime, transitionTime, clips, to);

        output.blendFrom(from, to, (float) alpha);

        bufferIndex = savedIndex;
    }

    private SkeletonPose acquireBuffer() {
        if (bufferIndex >= buffers.size())
            buffers.add(new SkeletonPose(skeleton));

        return buffers.get(bufferIndex++);
    }
}
