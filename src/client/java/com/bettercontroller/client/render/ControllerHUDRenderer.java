package com.bettercontroller.client.render;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.glyph.ControllerGlyphService;
import com.bettercontroller.client.input.ControllerRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

public final class ControllerHUDRenderer {
    private static final int CHIP_HEIGHT = 12;
    private static final int CHIP_GAP = 4;
    private static final int CHIP_PADDING_X = 5;

    public void render(Minecraft client, GuiGraphicsExtractor context, ControllerRuntime runtime) {
        if (client == null || context == null || runtime == null) {
            return;
        }
        String runtimeStatus = runtime.runtimeStatusMessage();
        if (!runtimeStatus.isBlank() && client.screen == null) {
            renderStatusBanner(client, context, runtimeStatus);
        }

        ControllerConfig config = runtime.latestConfig();
        if (config == null || !config.hudHintsEnabled) {
            return;
        }
        if (client.screen != null || client.player == null || client.level == null) {
            return;
        }
        if (runtime.latestSnapshot() == null || !runtime.latestSnapshot().isConnected()) {
            return;
        }

        ControllerConfig.ResolvedLayout layout = runtime.activeLayout();
        if (layout == null) {
            return;
        }
        ControllerGlyphService glyphs = runtime.glyphs();

        List<Prompt> prompts = buildPrompts(client, layout);
        if (prompts.isEmpty()) {
            return;
        }

        int totalWidth = 0;
        int[] widths = new int[prompts.size()];
        for (int i = 0; i < prompts.size(); i++) {
            Prompt prompt = prompts.get(i);
            String buttonText = "[" + glyphs.glyphForBinding(prompt.button()) + "]";
            int width = (CHIP_PADDING_X * 2)
                + client.font.width(buttonText)
                + 4
                + client.font.width(prompt.label());
            widths[i] = width;
            totalWidth += width;
            if (i > 0) {
                totalWidth += CHIP_GAP;
            }
        }

        int x = (context.getScaledWindowWidth() - totalWidth) / 2;
        int y = context.getScaledWindowHeight() - 48;
        int accent = 0xFFE2E8F0;

        for (int i = 0; i < prompts.size(); i++) {
            Prompt prompt = prompts.get(i);
            int width = widths[i];
            String buttonText = "[" + glyphs.glyphForBinding(prompt.button()) + "]";

            context.fill(x, y, x + width, y + CHIP_HEIGHT, 0x8C0B0F14);
            context.fill(x, y + CHIP_HEIGHT - 1, x + width, y + CHIP_HEIGHT, 0x664E647A);

            int buttonX = x + CHIP_PADDING_X;
            int labelX = buttonX + client.font.width(buttonText) + 4;
            context.drawText(client.font, buttonText, buttonX, y + 2, accent, false);
            context.drawText(client.font, prompt.label(), labelX, y + 2, 0xFFF7FAFF, false);
            x += width + CHIP_GAP;
        }
    }

    private static List<Prompt> buildPrompts(Minecraft client, ControllerConfig.ResolvedLayout layout) {
        List<Prompt> prompts = new ArrayList<>(4);
        prompts.add(new Prompt(firstBinding(layout, "jump"), "Jump"));
        if (client.hitResult != null && client.hitResult.getType() != HitResult.Type.MISS) {
            prompts.add(new Prompt(firstBinding(layout, "attack"), "Mine/Attack"));
            prompts.add(new Prompt(firstBinding(layout, "use"), "Use"));
        } else {
            prompts.add(new Prompt(firstBinding(layout, "use"), "Use"));
        }
        prompts.add(new Prompt(firstBinding(layout, "inventory"), "Inventory"));
        return prompts;
    }

    private static String firstBinding(ControllerConfig.ResolvedLayout layout, String action) {
        if (layout == null || layout.actionBindings(action).isEmpty()) {
            return "?";
        }
        return layout.actionBindings(action).get(0);
    }

    private static void renderStatusBanner(Minecraft client, GuiGraphicsExtractor context, String message) {
        int textWidth = client.font.width(message);
        int width = textWidth + 16;
        int x = (context.getScaledWindowWidth() - width) / 2;
        int y = 14;
        context.fill(x, y, x + width, y + 14, 0xA1000000);
        context.fill(x, y + 13, x + width, y + 14, 0x884E647A);
        context.drawCenteredTextWithShadow(client.font, message, x + (width / 2), y + 3, 0xFFFFFFFF);
    }

    private record Prompt(String button, String label) {
    }
}
