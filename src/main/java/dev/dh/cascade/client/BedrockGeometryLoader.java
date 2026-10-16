package dev.dh.cascade.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.dh.cascade.bedrock.BedrockGeometry;
import dev.dh.cascade.bedrock.BedrockGeometryParser;
import dev.dh.cascade.model.Skeleton;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BedrockGeometryLoader extends SimplePreparableReloadListener<Map<ResourceLocation, List<BedrockGeometry>>> {
    public static final BedrockGeometryLoader INSTANCE = new BedrockGeometryLoader();

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String DIRECTORY = "bedrock/models";
    private static final String PREFIX = "bedrock/models/";
    private static final String SUFFIX = ".geo.json";

    private volatile Map<ResourceLocation, List<BedrockGeometry>> geometries = Map.of();
    private volatile Map<ResourceLocation, List<Skeleton>> skeletons = Map.of();

    @Override
    protected Map<ResourceLocation, List<BedrockGeometry>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, List<BedrockGeometry>> results = new HashMap<>();
        Map<ResourceLocation, Resource> resources = manager.listResources(DIRECTORY, id -> id.getPath().endsWith(SUFFIX));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation file = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                String path = file.getPath();
                String name = path.substring(PREFIX.length(), path.length() - SUFFIX.length());

                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), name);
                List<BedrockGeometry> models = BedrockGeometryParser.parse(json);

                for (BedrockGeometry geometry : models)
                    new Skeleton(geometry);

                results.put(id, models);
            } catch (Exception exception) {
                LOGGER.error("Failed to load Bedrock geometry: {}", file, exception);
            }
        }

        return Map.copyOf(results);
    }

    @Override
    protected void apply(Map<ResourceLocation, List<BedrockGeometry>> result, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, List<Skeleton>> compiled = new HashMap<>();
        for (Map.Entry<ResourceLocation, List<BedrockGeometry>> entry : result.entrySet()) {
            List<Skeleton> models = new ArrayList<>();
            for (BedrockGeometry geometry : entry.getValue()) {
                Skeleton skeleton = new Skeleton(geometry);
                models.add(skeleton);

                LOGGER.info("Skeleton {} ({}) loaded: {} bones", entry.getKey(), geometry.identifier(), skeleton.boneCount());
            }

            compiled.put(entry.getKey(), List.copyOf(models));
        }

        geometries = result;
        skeletons = Map.copyOf(compiled);

        LOGGER.info("Loaded {} Bedrock geometry files", result.size());
    }

    public List<BedrockGeometry> get(ResourceLocation id) {
        return geometries.getOrDefault(id, List.of());
    }

    public List<Skeleton> getSkeletons(ResourceLocation id) {
        return skeletons.getOrDefault(id, List.of());
    }
}
