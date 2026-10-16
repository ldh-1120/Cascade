package dev.dh.cascade.model;

import dev.dh.cascade.animation.BoneMask;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

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

    public void copyFrom(SkeletonPose source) {
        if (source.skeleton != skeleton)
            throw new IllegalArgumentException("Skeleton mismatch");

        if (source == this)
            return;

        for (int i = 0; i < skeleton.boneCount(); i++) {
            translations[i].set(source.translations[i]);
            rotations[i].set(source.rotations[i]);
            scales[i].set(source.scales[i]);
        }

        dirty = true;
    }

    public void applyMaskedOverride(SkeletonPose overlay, BoneMask.Bound mask, float weight) {
        validateLayer(overlay, mask, weight);

        for (int i = 0; i < skeleton.boneCount(); i++) {
            float alpha = weight * mask.weight(i);
            if (alpha <= 0.0f)
                continue;

            translations[i].lerp(overlay.translations[i], alpha);
            rotations[i].slerp(overlay.rotations[i], alpha).normalize();
            scales[i].lerp(overlay.scales[i], alpha);
        }

        dirty = true;
    }

    public void applyMaskedAdditive(SkeletonPose delta, BoneMask.Bound mask, float weight) {
        validateLayer(delta, mask, weight);

        Quaternionf weightedRotation = new Quaternionf();
        for (int i = 0; i < skeleton.boneCount(); i++) {
            float alpha = weight * mask.weight(i);
            if (alpha <= 0.0f)
                continue;

            translations[i].fma(alpha, delta.translations[i]);
            weightedRotation.identity().slerp(delta.rotations[i], alpha);
            rotations[i].mul(weightedRotation).normalize();

            Vector3f scale = delta.scales[i];
            scales[i].mul(1.0f + (scale.x() - 1.0f) * alpha, 1.0f + (scale.y() - 1.0f) * alpha, 1.0f + (scale.z() - 1.0f) * alpha);
        }

        dirty = true;
    }

    private void validateLayer(SkeletonPose other, BoneMask.Bound mask, float weight) {
        if (other == this)
            throw new IllegalArgumentException("Layer pose must be separate");

        if (other.skeleton != skeleton || mask.skeleton() != skeleton)
            throw new IllegalArgumentException("Skeleton mismatch");

        if (!Float.isFinite(weight) || weight < 0.0f || weight > 1.0f)
            throw new IllegalArgumentException("Invalid layer weight");
    }

    public void solveTwoBoneIk(String rootName, String middleName, String endName, Vector3fc target, Vector3fc pole) {
        solveTwoBoneIk(rootName, middleName, endName, target, pole, 0.0f, 180.0f);
    }

    public void solveTwoBoneIk(String rootName, String middleName, String endName, Vector3fc target, Vector3fc pole, float minBendDegrees, float maxBendDegrees) {
        int rootIndex = skeleton.requireIndex(rootName);
        int middleIndex = skeleton.requireIndex(middleName);
        int endIndex = skeleton.requireIndex(endName);

        if (skeleton.bone(middleIndex).parentIndex() != rootIndex || skeleton.bone(endIndex).parentIndex() != middleIndex)
            throw new IllegalArgumentException("IK bones must form a direct chain");

        if (target == null || pole == null)
            throw new IllegalArgumentException("IK target and pole are required");

        if (!Float.isFinite(minBendDegrees) || !Float.isFinite(maxBendDegrees) || minBendDegrees < 0.0f || maxBendDegrees > 180.0f || minBendDegrees > maxBendDegrees)
            throw new IllegalArgumentException("Invalid IK bend limits");

        if (!Float.isFinite(target.x()) || !Float.isFinite(target.y()) || !Float.isFinite(target.z()) || !Float.isFinite(pole.x()) || !Float.isFinite(pole.y()) || !Float.isFinite(pole.z()))
            throw new IllegalArgumentException("Invalid IK coordinates");

        for (int i = middleIndex; i >= 0; ) {
            Vector3f scale = scales[i];
            if (Math.abs(scale.x() - 1.0f) > 0.0001f || Math.abs(scale.y() - 1.0f) > 0.0001f || Math.abs(scale.z() - 1.0f) > 0.0001f)
                throw new IllegalArgumentException("Scaled IK chain is unsupported");

            i = skeleton.bone(i).parentIndex();
        }

        updateMatrices();

        Vector3f root = modelMatrices[rootIndex].getTranslation(new Vector3f());
        Vector3f middle = modelMatrices[middleIndex].getTranslation(new Vector3f());
        Vector3f end = modelMatrices[endIndex].getTranslation(new Vector3f());

        float upperLength = root.distance(middle);
        float lowerLength = middle.distance(end);

        if (upperLength < 0.001f || lowerLength < 0.001f)
            throw new IllegalArgumentException("IK bone length is zero");

        Vector3f direction = new Vector3f(target).sub(root);
        float distance = direction.length();

        if (distance < 0.0001f) {
            direction.set(end).sub(root);
            if (direction.lengthSquared() < 0.00000001f)
                direction.set(middle).sub(root);
        }

        direction.normalize();

        float physicalMin = Math.abs(upperLength - lowerLength) + 0.0001f;
        float physicalMax = upperLength + lowerLength - 0.0001f;

        float limitedMin = (float) Math.sqrt(Math.max(0.0, upperLength * upperLength + lowerLength * lowerLength + 2.0 * upperLength * lowerLength * Math.cos(Math.toRadians(maxBendDegrees))));
        float limitedMax = (float) Math.sqrt(Math.max(0.0, upperLength * upperLength + lowerLength * lowerLength + 2.0 * upperLength * lowerLength * Math.cos(Math.toRadians(minBendDegrees))));

        float minimum = Math.max(physicalMin, Math.min(physicalMax, limitedMin));
        float maximum = Math.max(minimum, Math.max(physicalMin, Math.min(physicalMax, limitedMax)));

        float reach = Math.max(minimum, Math.min(distance, maximum));
        Vector3f reachableTarget = new Vector3f(root).fma(reach, direction);

        Vector3f bend = new Vector3f(pole).sub(root);
        bend.fma(-bend.dot(direction), direction);

        if (bend.lengthSquared() < 0.00000001f) {
            bend.set(middle).sub(root);
            bend.fma(-bend.dot(direction), direction);
        }

        if (bend.lengthSquared() < 0.00000001f) {
            bend.set(Math.abs(direction.z()) < 0.9f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0));
            bend.fma(-bend.dot(direction), direction);
        }

        bend.normalize();

        float along = (upperLength * upperLength + reach * reach - lowerLength * lowerLength) / (2.0f * reach);
        float height = (float) Math.sqrt(Math.max(0.0f, upperLength * upperLength - along * along));

        Vector3f kneeTarget = new Vector3f(root).fma(along, direction).fma(height, bend);
        rotateBoneTowards(rootIndex, new Vector3f(middle).sub(root), new Vector3f(kneeTarget).sub(root));

        updateMatrices();

        middle = modelMatrices[middleIndex].getTranslation(new Vector3f());
        end = modelMatrices[endIndex].getTranslation(new Vector3f());

        rotateBoneTowards(middleIndex, new Vector3f(end).sub(middle), new Vector3f(reachableTarget).sub(middle));
    }

    private void rotateBoneTowards(int index, Vector3f from, Vector3f to) {
        updateMatrices();

        Quaternionf worldRotation = modelMatrices[index].getNormalizedRotation(new Quaternionf());
        Quaternionf delta = new Quaternionf().rotationTo(from.normalize(), to.normalize());
        Quaternionf localCorrection = new Quaternionf(worldRotation).invert().mul(delta).mul(worldRotation);

        rotations[index].mul(localCorrection).normalize();

        dirty = true;
    }

    public void rotateLocalDegrees(String boneName, float x, float y, float z) {
        int index = skeleton.requireIndex(boneName);
        rotations[index].rotateXYZ((float) Math.toRadians(x), (float) Math.toRadians(y), (float) Math.toRadians(z)).normalize();

        dirty = true;
    }

    public void addTranslation(String boneName, float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
            throw new IllegalArgumentException("Invalid translation");

        int index = skeleton.requireIndex(boneName);
        translations[index].add(x, y, z);

        dirty = true;
    }
}
