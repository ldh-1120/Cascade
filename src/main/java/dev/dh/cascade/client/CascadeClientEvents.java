package dev.dh.cascade.client;

import dev.dh.cascade.Cascade;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = Cascade.MOD_ID, value = Dist.CLIENT)
public final class CascadeClientEvents {
    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(BedrockGeometryLoader.INSTANCE);
    }
}
