package dev.dh.cascade.animation;

import dev.dh.cascade.bedrock.BedrockAnimation;
import dev.dh.cascade.model.Skeleton;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AnimationClip {
    private final float length;
    private final BedrockAnimation.LoopMode loop;
    private final List<BoneTrack> tracks;

    public AnimationClip(BedrockAnimation animation, Skeleton skeleton) {
        length = animation.length();
        loop = animation.loop();

        List<BoneTrack> compiled = new ArrayList<>();
        for (Map.Entry<String, BedrockAnimation.BoneAnimation> entry : animation.bones().entrySet()) {
            String name = entry.getKey();
            if (skeleton.indexOf(name) < 0)
                throw new IllegalArgumentException("Unknown animation bone: " + name);

            BedrockAnimation.BoneAnimation bone = entry.getValue();
            compiled.add(new BoneTrack(name, compile(bone.position()), compile(bone.rotation()), compile(bone.scale())));
        }

        tracks = List.copyOf(compiled);
    }

    private static List<Keyframe> compile(List<BedrockAnimation.Keyframe> frames) {
        List<Keyframe> results = new ArrayList<>(frames.size());
        for (BedrockAnimation.Keyframe frame : frames) {
            if (frame.pre() == null || frame.post() == null)
                throw new IllegalArgumentException("Empty keyframe at " + frame.time());

            results.add(new Keyframe(frame.time(), parse(frame.pre()), parse(frame.post()), frame.interpolation()));
        }

        return List.copyOf(results);
    }

    private static Vector3f parse(BedrockAnimation.VectorExpression expression) {
        return new Vector3f(parseNumber(expression.x()), parseNumber(expression.y()), parseNumber(expression.z()));
    }

    private static float parseNumber(String expression) {
        try {
            float value = Float.parseFloat(expression);
            if (!Float.isFinite(value))
                throw new IllegalArgumentException("Non-finite animation value: " + expression);

            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("MoLang is not supported yet: " + expression, exception);
        }
    }

    public float length() {
        return length;
    }

    public BedrockAnimation.LoopMode loop() {
        return loop;
    }

    public List<BoneTrack> tracks() {
        return tracks;
    }

    public record Keyframe(float time, Vector3f pre, Vector3f post, BedrockAnimation.Interpolation interpolation) { }

    public record BoneTrack(String name, List<Keyframe> position, List<Keyframe> rotation, List<Keyframe> scale) {
        public BoneTrack {
            position = List.copyOf(position);
            rotation = List.copyOf(rotation);
            scale = List.copyOf(scale);
        }
    }
}
