package dev.dh.cascade.bedrock;

import com.google.gson.*;

import java.util.*;

public final class BedrockAnimationParser {
    public static Map<String, BedrockAnimation> parse(JsonObject root) {
        JsonObject animations = root.getAsJsonObject("animations");
        if (animations == null)
            throw new JsonParseException("Missing animations");

        Map<String, BedrockAnimation> results = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
            String name = entry.getKey();
            JsonObject object = entry.getValue().getAsJsonObject();

            Map<String, BedrockAnimation.BoneAnimation> bones = parseBones(object);

            float length = calculateLength(bones);
            if (object.has("animation_length"))
                length = object.get("animation_length").getAsFloat();

            if (!Float.isFinite(length) || length < 0)
                throw new JsonParseException("Invalid animation length: " + name);

            BedrockAnimation animation = new BedrockAnimation(name, length, parseLoop(object), getBoolean(object, "override_previous_animation", false), getExpression(object, "blend_weight", "1.0"), getExpression(object, "anim_time_update", null), bones);
            results.put(name, animation);
        }

        return Map.copyOf(results);
    }

    private static Map<String, BedrockAnimation.BoneAnimation> parseBones(JsonObject animation) {
        JsonObject boneObject = animation.getAsJsonObject("bones");
        if (boneObject == null)
            return Map.of();

        Map<String, BedrockAnimation.BoneAnimation> bones = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : boneObject.entrySet()) {
            JsonObject bone = entry.getValue().getAsJsonObject();
            bones.put(entry.getKey(), new BedrockAnimation.BoneAnimation(parseChannel(bone, "position"), parseChannel(bone, "rotation"), parseChannel(bone, "scale")));
        }

        return Map.copyOf(bones);
    }

    private static List<BedrockAnimation.Keyframe> parseChannel(JsonObject bone, String channel) {
        if (!bone.has(channel))
            return List.of();

        JsonElement element = bone.get(channel);
        if (!element.isJsonObject()) {
            BedrockAnimation.VectorExpression value = parseVector(element);

            return List.of(new BedrockAnimation.Keyframe(0.0f, value, value, BedrockAnimation.Interpolation.LINEAR));
        }

        JsonObject object = element.getAsJsonObject();
        if (object.isEmpty())
            return List.of();

        List<BedrockAnimation.Keyframe> frames = new ArrayList<>();
        Set<Float> times = new HashSet<>();

        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            float time = parseTime(entry.getKey());
            if (!times.add(time))
                throw new JsonParseException("Duplicate keyframe time: " + time);

            JsonElement value = entry.getValue();
            if (!value.isJsonObject()) {
                BedrockAnimation.VectorExpression expression = parseVector(value);
                frames.add(new BedrockAnimation.Keyframe(time, expression, expression, BedrockAnimation.Interpolation.LINEAR));

                continue;
            }

            JsonObject frame = value.getAsJsonObject();
            BedrockAnimation.VectorExpression pre = frame.has("pre") ? parseVector(frame.get("pre")) : null;
            BedrockAnimation.VectorExpression post = frame.has("post") ? parseVector(frame.get("post")) : null;
            if (pre == null)
                pre = post;

            if (post == null)
                post = pre;

            BedrockAnimation.Interpolation interpolation = parseInterpolation(frame);
            frames.add(new BedrockAnimation.Keyframe(time, pre, post, interpolation));
        }

        frames.sort(Comparator.comparingDouble(BedrockAnimation.Keyframe::time));

        return List.copyOf(frames);
    }

    private static BedrockAnimation.VectorExpression parseVector(JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.size() == 1) {
                String value = parseComponent(array.get(0));

                return new BedrockAnimation.VectorExpression(value, value, value);
            }

            if (array.size() != 3)
                throw new JsonParseException("Expected 1 or 3 vector components");

            return new BedrockAnimation.VectorExpression(
                    parseComponent(array.get(0)),
                    parseComponent(array.get(1)),
                    parseComponent(array.get(2))
            );
        }

        String value = parseComponent(element);

        return new BedrockAnimation.VectorExpression(value, value, value);
    }

    private static String parseComponent(JsonElement element) {
        if (!element.isJsonPrimitive())
            throw new JsonParseException("Expected number or MoLang expression");

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber() && !primitive.isString())
            throw new JsonParseException("Invalid animation component");

        return primitive.getAsString();
    }

    private static float parseTime(String value) {
        try {
            float time = Float.parseFloat(value);
            if (!Float.isFinite(time) || time < 0)
                throw new NumberFormatException(value);

            return time;
        } catch (NumberFormatException exception) {
            throw new JsonParseException("Invalid keyframe time: " + value, exception);
        }
    }

    private static BedrockAnimation.Interpolation parseInterpolation(JsonObject frame) {
        if (!frame.has("lerp_mode"))
            return BedrockAnimation.Interpolation.LINEAR;

        String mode = frame.get("lerp_mode").getAsString();
        return switch (mode.toLowerCase(java.util.Locale.ROOT)) {
            case "linear" -> BedrockAnimation.Interpolation.LINEAR;
            case "catmullrom" -> BedrockAnimation.Interpolation.CATMULLROM;
            default -> throw new JsonParseException("Unsupported interpolation: " + mode);
        };
    }

    private static BedrockAnimation.LoopMode parseLoop(JsonObject object) {
        if (!object.has("loop"))
            return BedrockAnimation.LoopMode.ONCE;

        JsonElement value = object.get("loop");
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean())
            return value.getAsBoolean() ? BedrockAnimation.LoopMode.LOOP : BedrockAnimation.LoopMode.ONCE;

        String mode = value.getAsString();
        if ("hold_on_last_frame".equals(mode))
            return BedrockAnimation.LoopMode.HOLD;

        throw new JsonParseException("Invalid loop mode: " + mode);
    }

    private static float calculateLength(Map<String, BedrockAnimation.BoneAnimation> bones) {
        float length = 0.0f;
        for (BedrockAnimation.BoneAnimation bone : bones.values()) {
            length = Math.max(length, lastTime(bone.position()));
            length = Math.max(length, lastTime(bone.rotation()));
            length = Math.max(length, lastTime(bone.scale()));
        }

        return length;
    }

    private static float lastTime(List<BedrockAnimation.Keyframe> frames) {
        if (frames.isEmpty())
            return 0.0f;

        return frames.get(frames.size() - 1).time();
    }

    private static boolean getBoolean(JsonObject object, String name, boolean fallback) {
        return object.has(name) ? object.get(name).getAsBoolean() : fallback;
    }

    private static String getExpression(JsonObject object, String name, String fallback) {
        return object.has(name) ? parseComponent(object.get(name)) : fallback;
    }
}
