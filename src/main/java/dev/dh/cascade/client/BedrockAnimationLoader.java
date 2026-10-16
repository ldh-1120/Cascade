package dev.dh.cascade.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.dh.cascade.bedrock.BedrockAnimation;
import dev.dh.cascade.bedrock.BedrockAnimationParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public final class BedrockAnimationLoader extends SimplePreparableReloadListener<Map<ResourceLocation, Map<String, BedrockAnimation>>> {
    public static final BedrockAnimationLoader INSTANCE = new BedrockAnimationLoader();

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String DIRECTORY = "bedrock/animations";
    private static final String PREFIX = "bedrock/animations/";
    private static final String SUFFIX = ".animation.json";

    private volatile Map<ResourceLocation, Map<String, BedrockAnimation>> animations = Map.of();

    @Override
    protected Map<ResourceLocation, Map<String, BedrockAnimation>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Map<String, BedrockAnimation>> results = new HashMap<>();

        Map<ResourceLocation, Resource> resources = manager.listResources(DIRECTORY, id -> id.getPath().endsWith(SUFFIX));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation file = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String path = file.getPath();
                String name = path.substring(PREFIX.length(), path.length() - SUFFIX.length());
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), name);

                Map<String, BedrockAnimation> parsed = BedrockAnimationParser.parse(json);
                results.put(id, parsed);
            } catch (Exception exception) {
                LOGGER.error("Failed to load Bedrock animation file: {}", file, exception);
            }
        }

        return Map.copyOf(results);
    }

    @Override
    protected void apply(Map<ResourceLocation, Map<String, BedrockAnimation>> result, ResourceManager manager, ProfilerFiller profiler) {
        animations = result;

        int animationCount = 0;
        for (Map.Entry<ResourceLocation, Map<String, BedrockAnimation>> file : result.entrySet()) {
            for (BedrockAnimation animation : file.getValue().values()) {
                animationCount++;
                LOGGER.info("Animation {} from {}: {} bones, {}s, {}", animation.name(), file.getKey(), animation.bones().size(), animation.length(), animation.loop());
            }
        }

        LOGGER.info("Loaded {} Bedrock animation files ({} animations)", result.size(), animationCount);
    }

    public Map<String, BedrockAnimation> get(ResourceLocation id) {
        return animations.getOrDefault(id, Map.of());
    }

    public BedrockAnimation get(ResourceLocation id, String animationName) {
        return get(id).get(animationName);
    }
}
