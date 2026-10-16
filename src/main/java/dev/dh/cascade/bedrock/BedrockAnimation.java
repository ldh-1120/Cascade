package dev.dh.cascade.bedrock;

import java.util.List;
import java.util.Map;

public record BedrockAnimation(String name, float length, LoopMode loop, boolean overridePreviousAnimation, String blendWeight, String animationTimeUpdate, Map<String, BoneAnimation> bones) {
    public BedrockAnimation {
        bones = Map.copyOf(bones);
    }

    public enum LoopMode {
        ONCE,
        LOOP,
        HOLD
    }

    public enum Interpolation {
        LINEAR,
        CATMULLROM
    }

    public record VectorExpression(String x, String y, String z) { }

    public record Keyframe(float time, VectorExpression pre, VectorExpression post, Interpolation interpolation) { }

    public record BoneAnimation(List<Keyframe> position, List<Keyframe> rotation, List<Keyframe> scale) {
        public BoneAnimation {
            position = List.copyOf(position);
            rotation = List.copyOf(rotation);
            scale = List.copyOf(scale);
        }
    }
}
