package dev.dh.cascade.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.dh.cascade.animation.AnimationBlender;
import dev.dh.cascade.animation.AnimationClip;
import dev.dh.cascade.api.MotionEntity;
import dev.dh.cascade.bedrock.BedrockAnimation;
import dev.dh.cascade.bedrock.BedrockGeometry;
import dev.dh.cascade.client.BedrockAnimationLoader;
import dev.dh.cascade.client.BedrockGeometryLoader;
import dev.dh.cascade.model.Skeleton;
import dev.dh.cascade.model.SkeletonPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CascadeEntityRenderer<T extends Entity & MotionEntity> extends EntityRenderer<T> {
    private final ResourceLocation id;
    private final ResourceLocation texture;

    private List<BedrockGeometry> loadedGeometries;
    private Map<String, BedrockAnimation> loadedAnimations;

    private BedrockModelRenderer model;
    private SkeletonPose pose;
    private AnimationBlender blender;

    private Map<String, AnimationClip> clips = Map.of();

    public CascadeEntityRenderer(EntityRendererProvider.Context context, ResourceLocation id) {
        super(context);

        this.id = Objects.requireNonNull(id);
        this.texture = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/entity/" + id.getPath() + ".png");

        this.shadowRadius = 0.35f;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack stack, MultiBufferSource buffers, int packedLight) {
        updateResources();

        if (model != null) {
            double nowTick = entity.tickCount + (double) partialTick;
            blender.apply(entity.motion().player().sampleFrame(nowTick), entity.motion().layers(), nowTick, clips, pose);

            stack.pushPose();

            try {
                stack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));

                VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity)));
                model.render(stack, consumer, packedLight, pose);
            } finally {
                stack.popPose();
            }
        }

        super.render(entity, entityYaw, partialTick, stack, buffers, packedLight);
    }

    private void updateResources() {
        BedrockGeometryLoader geometryLoader = BedrockGeometryLoader.INSTANCE;

        List<BedrockGeometry> geometries = geometryLoader.get(id);
        if (geometries != loadedGeometries) {
            loadedGeometries = geometries;

            model = null;
            pose = null;
            blender = null;

            loadedAnimations = null;
            clips = Map.of();

            if (!geometries.isEmpty()) {
                if (geometries.size() != 1)
                    throw new IllegalStateException("Expected one geometry in " + id);

                List<Skeleton> skeletons = geometryLoader.getSkeletons(id);
                if (skeletons.size() != 1)
                    throw new IllegalStateException("Skeleton missing for " + id);

                Skeleton skeleton = skeletons.get(0);

                model = new BedrockModelRenderer(geometries.get(0), skeleton);

                pose = new SkeletonPose(skeleton);
                blender = new AnimationBlender(skeleton);
            }
        }

        Map<String, BedrockAnimation> animations = BedrockAnimationLoader.INSTANCE.get(id);
        if (animations != loadedAnimations) {
            Map<String, AnimationClip> compiled = new HashMap<>();

            if (pose != null) {
                for (Map.Entry<String, BedrockAnimation> entry : animations.entrySet())
                    compiled.put(entry.getKey(), new AnimationClip(entry.getValue(), pose.skeleton()));
            }

            clips = Map.copyOf(compiled);
            loadedAnimations = animations;
        }
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }
}
