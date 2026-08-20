package com.bettercontroller.client.polling;

import com.bettercontroller.BetterControllerMod;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Teaches GLFW about controllers it does not already know.
 *
 * <p>GLFW only reports a joystick as a <em>gamepad</em> when it has an SDL mapping for that
 * device's GUID, and {@link ControllerPoller} only drives gamepads - a pad with no mapping is not
 * badly detected, it is invisible. GLFW ships a mapping database, but it is a snapshot taken when
 * that GLFW version was built, so pads released since, and DirectInput-only devices, fall through.
 *
 * <p>Dropping a mapping file next to the config fixes those without waiting for a GLFW update. The
 * database is not bundled: the published one is around a megabyte of text, which would dwarf a mod
 * that is deliberately small, for a case most players never hit.
 */
public final class ControllerMappings {
    public static final String MAPPINGS_FILE_NAME = "bettercontroller-mappings.txt";
    public static final String DATABASE_URL = "https://github.com/mdqinc/SDL_GameControllerDB";

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /**
     * Applies {@value #MAPPINGS_FILE_NAME} from the config directory, if the player put one there.
     *
     * @return the number of mapping lines applied, or 0 when there is no file or it could not be
     *         used. Must be called on the render thread, like every other GLFW call.
     */
    public static int applyUserMappings(GlfwBridge glfw, Path configDir) {
        if (glfw == null || configDir == null) {
            return 0;
        }

        Path mappingsFile = configDir.resolve(MAPPINGS_FILE_NAME);
        if (!Files.isRegularFile(mappingsFile)) {
            return 0;
        }

        String contents;
        try {
            // Files.readString is Java 11; 1.12.2 is stuck on 8.
            contents = new String(Files.readAllBytes(mappingsFile), UTF_8);
        } catch (IOException exception) {
            BetterControllerMod.logger().warn(
                "Could not read {}: {}", mappingsFile, exception.getMessage()
            );
            return 0;
        }

        if (contents.trim().isEmpty()) {
            return 0;
        }

        // The off-heap encode-and-free lives in GlfwBridge: LWJGL's MemoryUtil is inside the
        // isolated classloader and cannot be named from here.
        if (!glfw.updateGamepadMappings(contents)) {
            BetterControllerMod.logger().warn(
                "GLFW rejected {}. Check that it uses the SDL_GameControllerDB format.", mappingsFile
            );
            return 0;
        }

        int lines = countMappingLines(contents);
        BetterControllerMod.logger().info("Applied {} controller mapping(s) from {}.", lines, mappingsFile);
        return lines;
    }

    /** Counts the lines GLFW would treat as mappings: blanks and {@code #} comments do not count. */
    static int countMappingLines(String contents) {
        if (contents == null) {
            return 0;
        }

        int count = 0;
        for (String line : contents.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                count++;
            }
        }
        return count;
    }

    private ControllerMappings() {
    }
}
