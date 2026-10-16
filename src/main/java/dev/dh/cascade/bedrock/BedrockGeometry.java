package dev.dh.cascade.bedrock;

import com.google.gson.JsonElement;
import org.joml.Vector3f;

import java.util.List;

public record BedrockGeometry(String identifier, int textureWidth, int textureHeight, List<Bone> bones) {
    public BedrockGeometry {
        bones = List.copyOf(bones);
    }

    public record Bone(String name, String parent, Vector3f pivot, Vector3f rotation, List<Cube> cubes) {
        public Bone {
            cubes = List.copyOf(cubes);
        }
    }

    public record Cube(Vector3f origin, Vector3f size, Vector3f pivot, Vector3f rotation, float inflate, boolean mirror, JsonElement uv) {
        public Cube {
            uv = uv.deepCopy();
        }
    }
}
