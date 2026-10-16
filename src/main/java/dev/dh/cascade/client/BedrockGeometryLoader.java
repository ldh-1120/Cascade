package dev.dh.cascade.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.dh.cascade.bedrock.BedrockGeometry;
import dev.dh.cascade.bedrock.BedrockGeometryParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.Reader;
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
                results.put(id, BedrockGeometryParser.parse(json));
            } catch (Exception exception) {
                LOGGER.error("Failed to load Bedrock geometry: {}", file, exception);
            }
        }

        return Map.copyOf(results);
    }

    @Override
    protected void apply(Map<ResourceLocation, List<BedrockGeometry>> result, ResourceManager manager, ProfilerFiller profiler) {
        geometries = result;
        LOGGER.info("Loaded {} Bedrock geometry files", result.size());

        for (Map.Entry<ResourceLocation, List<BedrockGeometry>> entry : result.entrySet()) {
            for (BedrockGeometry geometry : entry.getValue()) {
                int cubeCount = 0;
                for (BedrockGeometry.Bone bone : geometry.bones())
                    cubeCount += bone.cubes().size();

                LOGGER.info("Geometry {} ({}) - {} bones, {} cubes", entry.getKey(), geometry.identifier(), geometry.bones().size(), cubeCount);
            }
        }
    }

    public List<BedrockGeometry> get(ResourceLocation id) {
        return geometries.getOrDefault(id, List.of());
    }
}
