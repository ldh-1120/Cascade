package dev.dh.cascade.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.dh.cascade.bedrock.BedrockGeometry;
import dev.dh.cascade.model.Skeleton;
import dev.dh.cascade.model.SkeletonPose;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class BedrockModelRenderer {
    private static final float UNIT = 1.0f / 16.0f;

    private final Skeleton skeleton;
    private final List<List<Quad>> quadsByBone;
    private final int quadCount;

    public BedrockModelRenderer(BedrockGeometry geometry, Skeleton skeleton) {
        this.skeleton = skeleton;

        List<List<Quad>> compiled = new ArrayList<>();
        for (int i = 0; i < skeleton.boneCount(); i++)
            compiled.add(new ArrayList<>());

        int count = 0;
        for (BedrockGeometry.Bone bone : geometry.bones()) {
            int index = skeleton.requireIndex(bone.name());
            List<Quad> quads = compiled.get(index);

            for (BedrockGeometry.Cube cube : bone.cubes())
                compileCube(geometry, bone, cube, quads);

            count += quads.size();
        }

        List<List<Quad>> immutable = new ArrayList<>();
        for (List<Quad> quads : compiled)
            immutable.add(List.copyOf(quads));

        quadsByBone = List.copyOf(immutable);
        quadCount = count;
    }

    public int quadCount() {
        return quadCount;
    }

    public void render(PoseStack stack, VertexConsumer consumer, int packedLight, SkeletonPose pose) {
        stack.pushPose();
        stack.scale(UNIT, UNIT, UNIT);

        for (int i = 0; i < skeleton.boneCount(); i++) {
            List<Quad> quads = quadsByBone.get(i);
            if (quads.isEmpty())
                continue;

            stack.pushPose();
            stack.mulPose(pose.getModelMatrix(skeleton.bone(i).name()));

            PoseStack.Pose transform = stack.last();
            for (Quad quad : quads) {
                float[] positions = quad.positions();
                float[] uv = quad.uv();

                for (int v = 0; v < 4; v++)
                    consumer.addVertex(transform, positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2])
                            .setColor(255, 255, 255, 255)
                            .setUv(uv[v * 2], uv[v * 2 + 1])
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(packedLight)
                            .setNormal(transform, quad.normal().x(), quad.normal().y(), quad.normal().z());
            }

            stack.popPose();
        }

        stack.popPose();
    }

    private static void compileCube(BedrockGeometry geometry, BedrockGeometry.Bone bone, BedrockGeometry.Cube cube, List<Quad> quads) {
        if (cube.mirror())
            throw new IllegalArgumentException("Mirrored UV is not supported yet");

        Vector3f origin = cube.origin();
        Vector3f size = cube.size();
        float inflate = cube.inflate();

        float x0 = origin.x() - inflate;
        float y0 = origin.y() - inflate;
        float z0 = origin.z() - inflate;

        float x1 = origin.x() + size.x() + inflate;
        float y1 = origin.y() + size.y() + inflate;
        float z1 = origin.z() + size.z() + inflate;

        Vector3f rotation = cube.rotation();
        Quaternionf quaternion = new Quaternionf().rotationXYZ((float) Math.toRadians(rotation.x()), (float) Math.toRadians(rotation.y()), (float) Math.toRadians(rotation.z()));

        addFace(geometry, bone, cube, quaternion, quads, "north", 0, 0, -1, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0);
        addFace(geometry, bone, cube, quaternion, quads, "south", 0, 0, 1, x1,y0,z1, x1,y1,z1, x0,y1,z1, x0,y0,z1);
        addFace(geometry, bone, cube, quaternion, quads, "east", 1, 0, 0, x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1);
        addFace(geometry, bone, cube, quaternion, quads, "west", -1, 0, 0, x0,y0,z1, x0,y1,z1, x0,y1,z0, x0,y0,z0);
        addFace(geometry, bone, cube, quaternion, quads, "up", 0, 1, 0, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0);
        addFace(geometry, bone, cube, quaternion, quads, "down", 0, -1, 0, x0,y0,z1, x0,y0,z0, x1,y0,z0, x1,y0,z1);
    }

    private static void addFace(BedrockGeometry geometry, BedrockGeometry.Bone bone, BedrockGeometry.Cube cube, Quaternionf rotation, List<Quad> quads, String face, float nx, float ny, float nz, float... vertices) {
        UvRect rect = readUv(cube, face);
        if (rect == null)
            return;

        float[] positions = new float[12];
        for (int i = 0; i < 4; i++) {
            Vector3f point = new Vector3f(vertices[i * 3], vertices[i * 3 + 1], vertices[i * 3 + 2]);
            point.sub(cube.pivot()).rotate(rotation).add(cube.pivot()).sub(bone.pivot());

            positions[i * 3] = point.x();
            positions[i * 3 + 1] = point.y();
            positions[i * 3 + 2] = point.z();
        }

        Vector3f normal = new Vector3f(nx, ny, nz).rotate(rotation);

        float u0 = rect.u();
        float v0 = rect.v();
        float u1 = u0 + rect.width();
        float v1 = v0 + rect.height();

        float[] corners = {u0, v1, u0, v0, u1, v0, u1, v1};
        if (face.equals("up") || face.equals("down"))
            corners = new float[] {u0, v0, u0, v1, u1, v1, u1, v0};

        float[] uv = new float[8];
        int turns = Math.floorMod(rect.rotation() / 90, 4);

        for (int i = 0; i < 4; i++) {
            int index = (i + turns) % 4;

            uv[i * 2] = corners[index * 2] / geometry.textureWidth();
            uv[i * 2 + 1] = corners[index * 2 + 1] / geometry.textureHeight();
        }

        quads.add(new Quad(positions, uv, normal));
    }

    private static UvRect readUv(BedrockGeometry.Cube cube, String face) {
        JsonElement element = cube.uv();
        if (element == null || element.isJsonNull())
            return null;

        float w = cube.size().x();
        float h = cube.size().y();
        float d = cube.size().z();

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();

            float u = array.get(0).getAsFloat();
            float v = array.get(1).getAsFloat();

            return switch (face) {
                case "east" -> new UvRect(u, v + d, d, h, 0);
                case "north" -> new UvRect(u + d, v + d, w, h, 0);
                case "west" -> new UvRect(u + d + w, v + d, d, h, 0);
                case "south" -> new UvRect(u + 2 * d + w, v + d, w, h, 0);
                case "up" -> new UvRect(u + d, v, w, d, 0);
                case "down" -> new UvRect(u + d + w, v, w, d, 0);
                default -> null;
            };
        }

        JsonObject faces = element.getAsJsonObject();
        if (!faces.has(face))
            return null;

        JsonObject entry = faces.getAsJsonObject(face);
        JsonArray offset = entry.getAsJsonArray("uv");

        float width = switch (face) {
            case "east", "west" -> d;
            default -> w;
        };

        float height = switch (face) {
            case "up", "down" -> d;
            default -> h;
        };

        if (entry.has("uv_size")) {
            JsonArray size = entry.getAsJsonArray("uv_size");

            width = size.get(0).getAsFloat();
            height = size.get(1).getAsFloat();
        }

        int rotation = entry.has("uv_rotation") ? entry.get("uv_rotation").getAsInt() : 0;
        if (rotation % 90 != 0)
            throw new IllegalArgumentException("Invalid UV rotation: " + rotation);

        return new UvRect(offset.get(0).getAsFloat(), offset.get(1).getAsFloat(), width, height, rotation);
    }

    private record Quad(float[] positions, float[] uv, Vector3f normal) { }

    private record UvRect(float u, float v, float width, float height, int rotation) { }
}
