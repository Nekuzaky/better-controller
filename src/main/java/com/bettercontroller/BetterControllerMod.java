package com.bettercontroller;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = BetterControllerMod.MOD_ID,
    name = BetterControllerMod.MOD_NAME,
    version = BetterControllerMod.VERSION,
    clientSideOnly = true,
    acceptedMinecraftVersions = "[1.12.2]"
)
public final class BetterControllerMod {
    public static final String MOD_ID = "bettercontroller";
    public static final String MOD_NAME = "BetterController";
    public static final String VERSION = "0.3.0";
    public static final String CONFIG_FILE_NAME = "bettercontroller.json";

    private static Logger logger;

    public static Logger logger() {
        return logger;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        logger.info("BetterController {} starting on Minecraft 1.12.2", VERSION);
    }
}
