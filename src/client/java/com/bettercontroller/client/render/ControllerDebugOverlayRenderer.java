package com.bettercontroller.client.render;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.input.ControllerRuntime;
import com.bettercontroller.client.polling.ControllerAxis;
import com.bettercontroller.client.polling.ControllerButton;
import com.bettercontroller.client.polling.ControllerMappings;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.translation.GameplayInputFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

public final class ControllerDebugOverlayRenderer {
    private boolean visible;

    public void toggleVisibility() {
        visible = !visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(MinecraftClient client, DrawContext context, ControllerRuntime runtime) {
        if (!visible || client == null || context == null || runtime == null) {
            return;
        }

        ControllerConfig config = runtime.latestConfig();
        if (config == null || !config.debugOverlayEnabled) {
            return;
        }

        ControllerSnapshot snapshot = runtime.latestSnapshot();
        GameplayInputFrame frame = runtime.latestFrame();
        if (snapshot == null || frame == null) {
            return;
        }

        int x = 8;
        int y = 8;
        int lineHeight = 10;

        // A player whose pad has no GLFW mapping sees "Connected: false" and nothing else, with no
        // way to find the GUID a mapping line needs. Surface it here, where they will look.
        String unmapped = runtime.unmappedJoystick();
        if (!snapshot.isConnected() && unmapped != null) {
            renderUnmappedNotice(client, context, unmapped);
            return;
        }

        String[] lines = new String[] {
            "BetterController Debug (F8)",
            "Connected: " + snapshot.isConnected(),
            "Type: " + runtime.activeControllerType(),
            "Glyph Set: " + runtime.glyphs().activeGlyphSetName(),
            "Controller: " + snapshot.joystickName(),
            "Slot: " + snapshot.joystickId() + "   GUID: " + snapshot.joystickGuid(),
            formatAxes("Raw", snapshot),
            formatTriggers(snapshot),
            String.format(Locale.ROOT, "Processed move(%.3f, %.3f) look(%.3f, %.3f)",
                frame.processedMoveX(), frame.processedMoveY(), frame.processedLookX(), frame.processedLookY()),
            "Buttons: " + pressedButtons(snapshot, runtime)
        };

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, client.textRenderer.getWidth(line));
        }

        context.fill(x - 4, y - 4, x + maxWidth + 6, y + (lines.length * lineHeight) + 2, 0x9A000000);
        for (int i = 0; i < lines.length; i++) {
            context.drawTextWithShadow(client.textRenderer, lines[i], x, y + (i * lineHeight), 0xFFFFFFFF);
        }
    }

    private static void renderUnmappedNotice(MinecraftClient client, DrawContext context, String unmapped) {
        String[] lines = new String[] {
            "BetterController Debug (F8)",
            "No usable gamepad. This device has no GLFW mapping:",
            "  " + unmapped,
            "Add a line for it to config/" + ControllerMappings.MAPPINGS_FILE_NAME,
            ControllerMappings.DATABASE_URL
        };

        int x = 8;
        int y = 8;
        int lineHeight = 10;
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, client.textRenderer.getWidth(line));
        }

        context.fill(x - 4, y - 4, x + maxWidth + 6, y + (lines.length * lineHeight) + 2, 0x9A000000);
        for (int i = 0; i < lines.length; i++) {
            context.drawTextWithShadow(client.textRenderer, lines[i], x, y + (i * lineHeight), 0xFFFFC66B);
        }
    }

    private static String formatAxes(String prefix, ControllerSnapshot snapshot) {
        return String.format(
            Locale.ROOT,
            "%s LX %.3f LY %.3f RX %.3f RY %.3f LT %.3f RT %.3f",
            prefix,
            snapshot.axis(ControllerAxis.LEFT_X),
            snapshot.axis(ControllerAxis.LEFT_Y),
            snapshot.axis(ControllerAxis.RIGHT_X),
            snapshot.axis(ControllerAxis.RIGHT_Y),
            snapshot.axis(ControllerAxis.LEFT_TRIGGER),
            snapshot.axis(ControllerAxis.RIGHT_TRIGGER)
        );
    }

    private static String pressedButtons(ControllerSnapshot snapshot, ControllerRuntime runtime) {
        StringBuilder builder = new StringBuilder();
        for (ControllerButton button : ControllerButton.values()) {
            if (snapshot.isPressed(button)) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(runtime.glyphs().glyphForButton(button));
            }
        }
        return builder.length() == 0 ? "none" : builder.toString();
    }

    private static String formatTriggers(ControllerSnapshot snapshot) {
        float left = normalizeTrigger(snapshot.axis(ControllerAxis.LEFT_TRIGGER));
        float right = normalizeTrigger(snapshot.axis(ControllerAxis.RIGHT_TRIGGER));
        return String.format(Locale.ROOT, "Triggers LT %.3f RT %.3f", left, right);
    }

    private static float normalizeTrigger(float value) {
        float normalized = (value + 1.0F) * 0.5F;
        return Math.max(0.0F, Math.min(1.0F, normalized));
    }
}
