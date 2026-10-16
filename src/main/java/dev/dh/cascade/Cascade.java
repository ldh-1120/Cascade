package dev.dh.cascade;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Cascade.MOD_ID)
public final class Cascade {
    public static final String MOD_ID = "cascade";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Cascade() {
        LOGGER.info("Cascade initialized");
    }
}
