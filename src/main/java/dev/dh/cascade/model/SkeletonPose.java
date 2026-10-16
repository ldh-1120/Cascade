package dev.dh.cascade.model;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SkeletonPose {
    private final Skeleton skeleton;

    private final Vector3f[] translations;
    private final Quaternionf[] rotations;
    private final Vector3f[] scales;

    private final Matrix4f[] localMatrices;
    private final Matrix4f[] modelMatrices;

    private boolean dirty = true;

    public SkeletonPose(Skeleton skeleton) {
        this.skeleton = skeleton;

        int count = skeleton.boneCount();

        translations = new Vector3f[count];
        rotations = new Quaternionf[count];
        scales = new Vector3f[count];

        localMatrices = new Matrix4f[count];
        modelMatrices = new Matrix4f[count];

        for (int i = 0; i < count; i++) {
            translations[i] = new Vector3f();
            rotations[i] = new Quaternionf();
            scales[i] = new Vector3f(1, 1, 1);

            localMatrices[i] = new Matrix4f();
            modelMatrices[i] = new Matrix4f();
        }
    }

    public void reset() {
        for (int i = 0; i < skeleton.boneCount(); i++) {
            translations[i].zero();
            rotations[i].identity();
            scales[i].set(1, 1, 1);
        }

        dirty = true;
    }

    public void setTranslation(String boneName, float x, float y, float z) {
        int index = skeleton.requireIndex(boneName);
        translations[index].set(x, y, z);

        dirty = true;
    }

    public void setRotationDegrees(String boneName, float x, float y, float z) {
        int index = skeleton.requireIndex(boneName);
        rotations[index].rotationXYZ((float) Math.toRadians(x), (float) Math.toRadians(y), (float) Math.toRadians(z));

        dirty = true;
    }

    public void setScale(String boneName, float x, float y, float z) {
        int index = skeleton.requireIndex(boneName);
        scales[index].set(x, y, z);

        dirty = true;
    }

    public Matrix4f getLocalMatrix(String boneName) {
        updateMatrices();

        int index = skeleton.requireIndex(boneName);
        return new Matrix4f(localMatrices[index]);
    }

    public Matrix4f getModelMatrix(String boneName) {
        updateMatrices();

        int index = skeleton.requireIndex(boneName);
        return new Matrix4f(modelMatrices[index]);
    }

    public Vector3f getModelPivot(String boneName) {
        updateMatrices();

        int index = skeleton.requireIndex(boneName);

        return modelMatrices[index].getTranslation(new Vector3f());
    }

    public void blendFrom(SkeletonPose from, SkeletonPose to, float weight) {
        if (from.skeleton != skeleton || to.skeleton != skeleton)
            throw new IllegalArgumentException("Skeleton mismatch");

        if (this == from || this == to)
            throw new IllegalArgumentException("Output pose must be separate");

        if (!Float.isFinite(weight))
            throw new IllegalArgumentException("Invalid blend weight");

        float alpha = Math.max(0.0f, Math.min(1.0f, weight));
        for (int i = 0; i < skeleton.boneCount(); i++) {
            translations[i].set(from.translations[i]).lerp(to.translations[i], alpha);
            rotations[i].set(from.rotations[i]).slerp(to.rotations[i], alpha).normalize();
            scales[i].set(from.scales[i]).lerp(to.scales[i], alpha);
        }

        dirty = true;
    }

    private void updateMatrices() {
        if (!dirty)
            return;

        for (int i = 0; i < skeleton.boneCount(); i++) {
            Skeleton.Bone bone = skeleton.bone(i);

            Vector3f offset = bone.localOffset();
            Vector3f bindRotation = bone.bindRotation();
            Vector3f translation = translations[i];

            Matrix4f local = localMatrices[i];
            local.identity()
                    .translate(offset.x() + translation.x(), offset.y() + translation.y(), offset.z() + translation.z())
                    .rotateXYZ((float) Math.toRadians(bindRotation.x()), (float) Math.toRadians(bindRotation.y()), (float) Math.toRadians(bindRotation.z()))
                    .rotate(rotations[i])
                    .scale(scales[i]);

            int parentIndex = bone.parentIndex();

            if (parentIndex < 0)
                modelMatrices[i].set(local);
            else
                modelMatrices[i].set(modelMatrices[parentIndex]).mul(local);
        }

        dirty = false;
    }

    public Skeleton skeleton() {
        return skeleton;
    }
}
