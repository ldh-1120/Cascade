package dev.dh.cascade.animation;

import dev.dh.cascade.model.Skeleton;
import dev.dh.cascade.model.SkeletonPose;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AnimationBlender {
    private final Skeleton skeleton;
    private final SkeletonPose identityPose;

    private final List<SkeletonPose> buffers = new ArrayList<>();
    private final Map<BoneMask, BoneMask.Bound> masks = new HashMap<>();

    private int bufferIndex;

    public AnimationBlender(Skeleton skeleton) {
        this.skeleton = skeleton;
        this.identityPose = new SkeletonPose(skeleton);
    }

    public void apply(AnimationPlayer.Frame baseFrame, Map<String, AnimationClip> clips, SkeletonPose output) {
        validateOutput(output);

        bufferIndex = 0;
        evaluate(baseFrame.root(), baseFrame.playbackSeconds(), baseFrame.transitionSeconds(), clips, output, null);
    }

    public void apply(AnimationPlayer.Frame baseFrame, Iterable<MotionLayer> layers, double nowTick, Map<String, AnimationClip> clips, SkeletonPose output) {
        apply(baseFrame, clips, output);

        for (MotionLayer layer : layers) {
            float weight = layer.weightAt(nowTick);
            if (weight <= 0.0f)
                continue;

            AnimationPlayer.Frame frame = layer.player().sampleFrame(nowTick);
            if (frame.root() == null)
                continue;

            BoneMask.Bound mask = masks.computeIfAbsent(layer.mask(), key -> key.bind(skeleton));

            bufferIndex = 0;

            SkeletonPose layerPose = acquireBuffer();
            SkeletonPose baseline = layer.mode() == MotionLayer.Mode.OVERRIDE ? output : identityPose;

            evaluate(frame.root(), frame.playbackSeconds(), frame.transitionSeconds(), clips, layerPose, baseline);
            if (layer.mode() == MotionLayer.Mode.OVERRIDE)
                output.applyMaskedOverride(layerPose, mask, weight);
            else
                output.applyMaskedAdditive(layerPose, mask, weight);
        }
    }

    private void evaluate(AnimationPlayer.PoseNode node, double playbackTime, double transitionTime, Map<String, AnimationClip> clips, SkeletonPose output, SkeletonPose baseline) {
        if (node == null) {
            initialize(output, baseline);
            return;
        }

        if (node instanceof AnimationPlayer.ClipNode clipNode) {
            initialize(output, baseline);

            AnimationClip clip = clips.get(clipNode.animation());
            if (clip == null)
                return;

            float time = (float) Math.max(0.0, playbackTime - clipNode.startPlaybackSeconds());
            AnimationSampler.applyInto(clip, output, time);

            return;
        }

        AnimationPlayer.BlendNode blend = (AnimationPlayer.BlendNode) node;
        double elapsed = transitionTime - blend.startTransitionSeconds();

        double alpha = elapsed / blend.durationSeconds();
        if (alpha <= 0.0) {
            evaluate(blend.from(), playbackTime, transitionTime, clips, output, baseline);
            return;
        }

        if (alpha >= 1.0) {
            evaluate(blend.to(), playbackTime, transitionTime, clips, output, baseline);
            return;
        }

        int savedIndex = bufferIndex;

        SkeletonPose from = acquireBuffer();
        SkeletonPose to = acquireBuffer();

        evaluate(blend.from(), playbackTime, transitionTime, clips, from, baseline);
        evaluate(blend.to(), playbackTime, transitionTime, clips, to, baseline);

        output.blendFrom(from, to, (float) alpha);

        bufferIndex = savedIndex;
    }

    private void initialize(SkeletonPose output, SkeletonPose baseline) {
        if (baseline == null)
            output.reset();
        else
            output.copyFrom(baseline);
    }

    private void validateOutput(SkeletonPose output) {
        if (output.skeleton() != skeleton)
            throw new IllegalArgumentException("Skeleton mismatch");
    }

    private SkeletonPose acquireBuffer() {
        if (bufferIndex >= buffers.size())
            buffers.add(new SkeletonPose(skeleton));

        return buffers.get(bufferIndex++);
    }
}
