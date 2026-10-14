package com.bettercontroller.client.polling;

import com.bettercontroller.BetterControllerMod;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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

    /**
     * Applies {@value #MAPPINGS_FILE_NAME} from the config directory, if the player put one there.
     *
     * @return the number of mapping lines applied, or 0 when there is no file or it could not be
     *         used. Must be called on the render thread, like every other GLFW call.
     */
    public static int applyUserMappings(Path configDir) {
        if (configDir == null) {
            return 0;
        }

        Path mappingsFile = configDir.resolve(MAPPINGS_FILE_NAME);
        if (!Files.isRegularFile(mappingsFile)) {
            return 0;
        }

        String contents;
        try {
            contents = Files.readString(mappingsFile, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            BetterControllerMod.LOGGER.warn(
                "Could not read {}: {}", mappingsFile, exception.getMessage()
            );
            return 0;
        }

        if (contents.isBlank()) {
            return 0;
        }

        // Off-heap and explicitly freed: GLFW wants a NUL-terminated C string, and the published
        // database is far too large for LWJGL's 64 KB stack.
        ByteBuffer encoded = MemoryUtil.memUTF8(contents, true);
        boolean accepted;
        try {
            accepted = GLFW.glfwUpdateGamepadMappings(encoded);
        } finally {
            MemoryUtil.memFree(encoded);
        }

        int lines = countMappingLines(contents);
        if (!accepted) {
            BetterControllerMod.LOGGER.warn(
                "GLFW rejected {}. Check that it uses the SDL_GameControllerDB format.", mappingsFile
            );
            return 0;
        }

        BetterControllerMod.LOGGER.info("Applied {} controller mapping(s) from {}.", lines, mappingsFile);
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
