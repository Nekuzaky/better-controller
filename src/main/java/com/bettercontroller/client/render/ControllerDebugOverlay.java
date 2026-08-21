package com.bettercontroller.client.render;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.input.ControllerRuntime;
import com.bettercontroller.client.polling.ControllerAxis;
import com.bettercontroller.client.polling.ControllerButton;
import com.bettercontroller.client.polling.ControllerMappings;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.translation.GameplayInputFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import java.util.Locale;

/**
 * The F8 diagnostic panel: what the pad is actually reporting, and what the mod made of it.
 *
 * <p>Worth its weight for the raw-versus-processed pair alone. "The stick feels wrong" is not
 * actionable; seeing raw LX at 0.5 while processed move is 1.0 says the deadzone curve is at
 * fault, and seeing them agree says the problem is further downstream. It is also the only place a
 * player whose pad has no GLFW mapping can find the GUID a mapping line needs.
 */
public final class ControllerDebugOverlay {
    private static final int LINE_HEIGHT = 10;
    private static final int MARGIN = 8;
    private static final int PANEL_BACKGROUND = 0x9A000000;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int WARNING_COLOR = 0xFFFFC66B;

    private boolean visible;

    public void toggleVisibility() {
        visible = !visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(Minecraft client, ControllerRuntime runtime) {
        if (!visible || client == null || client.fontRenderer == null || runtime == null) {
            return;
        }

        ControllerConfig config = runtime.latestConfig();
        if (config == null || !config.debugOverlayEnabled) {
            return;
        }

        String unmapped = runtime.unmappedJoystick();
        ControllerSnapshot snapshot = runtime.latestSnapshot();

        // A player whose pad has no GLFW mapping otherwise sees "Connected: false" and nothing
        // else, with no way to find the GUID a mapping line needs. Surface it where they will look.
        if ((snapshot == null || !snapshot.isConnected()) && unmapped != null) {
            draw(client, new String[] {
                "BetterController Debug (F8)",
                "No usable gamepad. This device has no GLFW mapping:",
                "  " + unmapped,
                "Add a line for it to config/" + ControllerMappings.MAPPINGS_FILE_NAME,
                ControllerMappings.DATABASE_URL,
            }, WARNING_COLOR);
            return;
        }

        GameplayInputFrame frame = runtime.latestFrame();
        if (snapshot == null || frame == null) {
            return;
        }

        draw(client, new String[] {
            "BetterController Debug (F8)",
            "Connected: " + snapshot.isConnected(),
            "Type: " + runtime.activeControllerType() + "   Glyphs: " + runtime.glyphs().activeGlyphSetName(),
            "Controller: " + snapshot.joystickName(),
            "Slot: " + snapshot.joystickId() + "   GUID: " + snapshot.joystickGuid(),
            formatAxes(snapshot),
            formatTriggers(snapshot),
            String.format(
                Locale.ROOT,
                "Processed move(%.3f, %.3f) look(%.3f, %.3f)",
                frame.processedMoveX(), frame.processedMoveY(),
                frame.processedLookX(), frame.processedLookY()
            ),
            "Buttons: " + pressedButtons(snapshot, runtime),
        }, TEXT_COLOR);
    }

    private static void draw(Minecraft client, String[] lines, int color) {
        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, client.fontRenderer.getStringWidth(line));
        }

        Gui.drawRect(
            MARGIN - 4,
            MARGIN - 4,
            MARGIN + widest + 6,
            MARGIN + (lines.length * LINE_HEIGHT) + 2,
            PANEL_BACKGROUND
        );
        for (int i = 0; i < lines.length; i++) {
            client.fontRenderer.drawString(lines[i], MARGIN, MARGIN + (i * LINE_HEIGHT), color);
        }
    }

    private static String formatAxes(ControllerSnapshot snapshot) {
        return String.format(
            Locale.ROOT,
            "Raw LX %.3f LY %.3f RX %.3f RY %.3f",
            snapshot.axis(ControllerAxis.LEFT_X),
            snapshot.axis(ControllerAxis.LEFT_Y),
            snapshot.axis(ControllerAxis.RIGHT_X),
            snapshot.axis(ControllerAxis.RIGHT_Y)
        );
    }

    private static String formatTriggers(ControllerSnapshot snapshot) {
        return String.format(
            Locale.ROOT,
            "Triggers LT %.3f RT %.3f",
            normalizeTrigger(snapshot.axis(ControllerAxis.LEFT_TRIGGER)),
            normalizeTrigger(snapshot.axis(ControllerAxis.RIGHT_TRIGGER))
        );
    }

    /** GLFW reports triggers over -1..1; the player thinks in 0..1. */
    private static float normalizeTrigger(float value) {
        float normalized = (value + 1.0F) * 0.5F;
        return Math.max(0.0F, Math.min(1.0F, normalized));
    }

    private static String pressedButtons(ControllerSnapshot snapshot, ControllerRuntime runtime) {
        StringBuilder builder = new StringBuilder();
        for (ControllerButton button : ControllerButton.values()) {
            if (!snapshot.isPressed(button)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            // Shown with the active family's names, so an Xbox pad says A and a PlayStation pad
            // says Cross for the same physical button.
            builder.append(runtime.glyphs().glyphForButton(button));
        }
        return builder.length() == 0 ? "none" : builder.toString();
    }
}
