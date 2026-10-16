package dev.dh.cascade.bedrock;

import com.google.gson.*;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BedrockGeometryParser {
    public static List<BedrockGeometry> parse(JsonObject root) {
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        if (geometries == null)
            throw new JsonParseException("Missing minecraft:geometry");

        List<BedrockGeometry> results = new ArrayList<>();
        for (JsonElement element : geometries) {
            JsonObject geometry = element.getAsJsonObject();
            JsonObject description = geometry.getAsJsonObject("description");
            if (description == null)
                throw new JsonParseException("Missing geometry description");

            String identifier = description.get("identifier").getAsString();
            int textureWidth = integer(description, "texture_width", 64);
            int textureHeight = integer(description, "texture_height", 64);
            if (textureWidth <= 0 || textureHeight <= 0)
                throw new JsonParseException("Invalid texture dimensions");

            List<BedrockGeometry.Bone> bones = new ArrayList<>();
            Set<String> names = new HashSet<>();

            JsonArray boneArray = geometry.getAsJsonArray("bones");
            if (boneArray != null) {
                for (JsonElement boneElement : boneArray) {
                    JsonObject bone = boneElement.getAsJsonObject();
                    String name = bone.get("name").getAsString();
                    if (!names.add(name))
                        throw new JsonParseException("Duplicate bone: " + name);

                    String parent = bone.has("parent") ? bone.get("parent").getAsString() : null;

                    Vector3f pivot = vector(bone, "pivot", new Vector3f());
                    Vector3f rotation = vector(bone, "rotation", new Vector3f());

                    boolean mirror = bool(bone, "mirror", false);
                    float inflate = number(bone, "inflate", 0);

                    List<BedrockGeometry.Cube> cubes = new ArrayList<>();
                    JsonArray cubeArray = bone.getAsJsonArray("cubes");
                    if (cubeArray != null) {
                        for (JsonElement cubeElement : cubeArray) {
                            JsonObject cube = cubeElement.getAsJsonObject();

                            Vector3f origin = requiredVector(cube, "origin");
                            Vector3f size = requiredVector(cube, "size");

                            Vector3f center = new Vector3f(origin.x() + size.x() * 0.5f, origin.y() + size.y() * 0.5f, origin.z() + size.z() * 0.5f);
                            cubes.add(new BedrockGeometry.Cube(origin, size, vector(cube, "pivot", center), vector(cube, "rotation", new Vector3f()), number(cube, "inflate", inflate), bool(cube, "mirror", mirror), cube.has("uv") ? cube.get("uv") : JsonNull.INSTANCE));
                        }
                    }

                    bones.add(new BedrockGeometry.Bone(name, parent, pivot, rotation, cubes));
                }
            }

            for (BedrockGeometry.Bone bone : bones) {
                if (bone.parent() != null && !names.contains(bone.parent()))
                    throw new JsonParseException("Unknown parent bone: " + bone.parent());
            }

            results.add(new BedrockGeometry(identifier, textureWidth, textureHeight, bones));
        }

        return List.copyOf(results);
    }

    private static Vector3f requiredVector(JsonObject object, String key) {
        if (!object.has(key))
            throw new JsonParseException("Missing vector: " + key);

        return vector(object, key, new Vector3f());
    }

    private static Vector3f vector(JsonObject object, String key, Vector3f fallback) {
        if (!object.has(key))
            return fallback;

        JsonArray values = object.getAsJsonArray(key);
        if (values.size() != 3)
            throw new JsonParseException("Vector must have 3 components: " + key);

        return new Vector3f(values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat());
    }

    private static float number(JsonObject object, String key, float fallback) {
        return object.has(key) ? object.get(key).getAsFloat() : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        return object.has(key) ? object.get(key).getAsBoolean() : fallback;
    }
}
