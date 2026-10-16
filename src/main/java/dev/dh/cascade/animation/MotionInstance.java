package dev.dh.cascade.animation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MotionInstance {
    private final AnimationPlayer player = new AnimationPlayer();
    private final Map<String, MotionLayer> layerMap = new LinkedHashMap<>();
    private final Collection<MotionLayer> layers = Collections.unmodifiableCollection(layerMap.values());

    public AnimationPlayer player() {
        return player;
    }

    public MotionLayer addLayer(String name, BoneMask mask, MotionLayer.Mode mode) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Invalid layer name");

        if (layerMap.containsKey(name))
            throw new IllegalArgumentException("Duplicate layer: " + name);

        MotionLayer layer = new MotionLayer(mask, mode);
        layerMap.put(name, layer);

        return layer;
    }

    public MotionLayer layer(String name) {
        MotionLayer layer = layerMap.get(name);
        if (layer == null)
            throw new IllegalArgumentException("Unknown layer: " + name);

        return layer;
    }

    public Collection<MotionLayer> layers() {
        return layers;
    }
}
