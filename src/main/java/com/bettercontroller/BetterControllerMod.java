package com.bettercontroller;

import com.bettercontroller.client.BetterControllerEventHandler;
import com.bettercontroller.client.input.ControllerRuntime;
import com.bettercontroller.client.polling.GlfwBridge;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

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

    /** LWJGL 2 Keyboard.KEY_F8, written out for the same reason as the codes in the keyboard
     * overlay: nothing in this mod links against a Keyboard class of either LWJGL generation. */
    private static final int DEFAULT_DEBUG_OVERLAY_KEY = 66;

    private static Logger logger;
    private static ControllerRuntime runtime;

    public static Logger logger() {
        return logger;
    }

    public static ControllerRuntime runtime() {
        return runtime;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        logger.info("BetterController {} starting on Minecraft 1.12.2", VERSION);

        Path configDirectory = event.getModConfigurationDirectory().toPath();

        // Null when GLFW cannot be had on this machine. That is a dormant mod, not a crash: the
        // player keeps a working game and gets one warning in the log saying why.
        GlfwBridge glfw = GlfwBridge.createOrNull(configDirectory);
        if (glfw == null) {
            logger.warn("BetterController is inactive: no usable GLFW. The game is unaffected.");
            return;
        }

        runtime = new ControllerRuntime(glfw, configDirectory);

        // A real key binding rather than polling F8 directly, so it appears in the Controls screen
        // and a player whose F8 is already taken can move it.
        KeyBinding debugOverlayKey = new KeyBinding(
            "key.bettercontroller.debug_overlay", DEFAULT_DEBUG_OVERLAY_KEY, "key.categories.bettercontroller");
        ClientRegistry.registerKeyBinding(debugOverlayKey);

        MinecraftForge.EVENT_BUS.register(new BetterControllerEventHandler(runtime, debugOverlayKey));
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                runtime.shutdown();
            }
        }, "BetterController-GLFW-shutdown"));
    }
}
