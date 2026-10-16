package dev.dh.cascade.model;

import dev.dh.cascade.bedrock.BedrockGeometry;
import org.joml.Vector3f;

import java.util.*;

public class Skeleton {
    private final List<Bone> bones;
    private final Map<String, Integer> indices;

    public Skeleton(BedrockGeometry geometry) {
        Map<String, BedrockGeometry.Bone> source = new LinkedHashMap<>();

        for (BedrockGeometry.Bone bone : geometry.bones()) {
            if (bone.name() == null || bone.name().isBlank())
                throw new IllegalArgumentException("Bone name is empty");

            if (source.putIfAbsent(bone.name(), bone) != null)
                throw new IllegalArgumentException("Duplicate bone: " + bone.name());
        }

        List<BedrockGeometry.Bone> ordered = new ArrayList<>();
        Map<String, Integer> states = new HashMap<>();
        for (BedrockGeometry.Bone bone : geometry.bones())
            visit(bone, source, states, ordered);

        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++)
            indexMap.put(ordered.get(i).name(), i);

        List<List<Integer>> childLists = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++)
            childLists.add(new ArrayList<>());

        for (int i = 0; i < ordered.size(); i++) {
            String parent = ordered.get(i).parent();
            if (parent != null)
                childLists.get(indexMap.get(parent)).add(i);
        }

        List<Bone> result = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            BedrockGeometry.Bone bone = ordered.get(i);
            String parentName = bone.parent();

            int parentIndex = parentName == null ? -1 : indexMap.get(parentName);
            Vector3f parentPivot = parentIndex < 0 ? new Vector3f() : ordered.get(parentIndex).pivot();

            Vector3f pivot = bone.pivot();
            Vector3f offset = new Vector3f(pivot.x() - parentPivot.x(), pivot.y() - parentPivot.y(), pivot.z() - parentPivot.z());

            result.add(new Bone(bone.name(), parentIndex, pivot, offset, bone.rotation(), List.copyOf(childLists.get(i))));
        }

        bones = List.copyOf(result);
        indices = Map.copyOf(indexMap);
    }

    private static void visit(BedrockGeometry.Bone bone, Map<String, BedrockGeometry.Bone> source, Map<String, Integer> states, List<BedrockGeometry.Bone> ordered) {
        int state = states.getOrDefault(bone.name(), 0);
        if (state == 1)
            throw new IllegalArgumentException("Cyclic bone: " + bone.name());

        if (state == 2)
            return;

        states.put(bone.name(), 1);
        if (bone.parent() != null) {
            BedrockGeometry.Bone parent = source.get(bone.parent());
            if (parent == null)
                throw new IllegalArgumentException("Missing parent: " + bone.parent());

            visit(parent, source, states, ordered);
        }

        states.put(bone.name(), 2);
        ordered.add(bone);
    }

    public int boneCount() {
        return bones.size();
    }

    public int indexOf(String name) {
        return indices.getOrDefault(name, -1);
    }

    public Bone bone(int index) {
        return bones.get(index);
    }

    public int requireIndex(String name) {
        int index = indexOf(name);

        if (index < 0)
            throw new IllegalArgumentException("Unknown bone: " + name);

        return index;
    }

    public List<Bone> bones() {
        return bones;
    }

    public record Bone(String name, int parentIndex, Vector3f pivot, Vector3f localOffset, Vector3f bindRotation, List<Integer> children) {}
}
