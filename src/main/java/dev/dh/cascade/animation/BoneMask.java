package dev.dh.cascade.animation;

import dev.dh.cascade.model.Skeleton;

import java.util.HashMap;
import java.util.Map;

public record BoneMask(Map<String, Float> weights, boolean descendants) {
    public BoneMask {
        weights = Map.copyOf(weights);
        if (weights.isEmpty())
            throw new IllegalArgumentException("Empty bone mask");

        for (Map.Entry<String, Float> entry : weights.entrySet()) {
            float weight = entry.getValue();
            if (entry.getKey().isBlank() || !Float.isFinite(weight) || weight < 0.0f || weight > 1.0f)
                throw new IllegalArgumentException("Invalid bone mask");
        }
    }

    public static BoneMask only(String... names) {
        return create(false, names);
    }

    public static BoneMask subtree(String... names) {
        return create(true, names);
    }

    private static BoneMask create(boolean descendants, String... names) {
        Map<String, Float> weights = new HashMap<>();
        for (String name : names)
            weights.put(name, 1.0f);

        return new BoneMask(weights, descendants);
    }

    public Bound bind(Skeleton skeleton) {
        for (String name : weights.keySet()) {
            if (skeleton.indexOf(name) < 0)
                throw new IllegalArgumentException("Unknown mask bone: " + name);
        }

        float[] values = new float[skeleton.boneCount()];
        for (int i = 0; i < values.length; i++) {
            Skeleton.Bone bone = skeleton.bone(i);
            int parent = bone.parentIndex();

            float inherited = descendants && parent >= 0 ? values[parent] : 0.0f;

            Float explicit = weights.get(bone.name());
            values[i] = explicit != null ? explicit : inherited;
        }

        return new Bound(skeleton, values);
    }

    public static final class Bound {
        private final Skeleton skeleton;
        private final float[] values;

        private Bound(Skeleton skeleton, float[] values) {
            this.skeleton = skeleton;
            this.values = values;
        }

        public Skeleton skeleton() {
            return skeleton;
        }

        public float weight(int index) {
            return values[index];
        }
    }
}
