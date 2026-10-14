package com.bettercontroller.client.render;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.glyph.ControllerGlyphService;
import com.bettercontroller.client.input.ControllerRuntime;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.polling.ControllerType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.math.RayTraceResult;

import java.util.List;

/**
 * The in-world HUD: a banner when a controller connects or disconnects, and a row of button
 * prompts above the hotbar. Same content and placement as the Fabric versions.
 *
 * <p>This runs every rendered frame, so the prompt strings are built once and rebuilt only when
 * what they say can change - a different config, or a different pad and therefore different
 * glyphs. Drawing them allocates nothing.
 */
public final class ControllerHudRenderer {
    private static final int CHIP_HEIGHT = 12;
    private static final int CHIP_GAP = 4;
    private static final int CHIP_PADDING_X = 5;
    private static final int CHIP_BOTTOM_OFFSET = 48;
    private static final int CHIP_BACKGROUND = 0x8C0B0F14;
    private static final int CHIP_UNDERLINE = 0x664E647A;
    private static final int GLYPH_COLOR = 0xFFE2E8F0;
    private static final int LABEL_COLOR = 0xFFF7FAFF;

    private static final int BANNER_TOP = 14;
    private static final int BANNER_HEIGHT = 14;
    private static final int BANNER_BACKGROUND = 0xA1000000;
    private static final int BANNER_UNDERLINE = 0x884E647A;

    /** Looking at nothing: what the triggers would do is not worth the space. */
    private static final String[] IDLE_LABELS = {"Jump", "Use", "Inventory"};
    private static final String[] IDLE_ACTIONS = {"jump", "use", "inventory"};
    /** Looking at a block or an entity. */
    private static final String[] AIMED_LABELS = {"Jump", "Mine/Attack", "Use", "Inventory"};
    private static final String[] AIMED_ACTIONS = {"jump", "attack", "use", "inventory"};

    private final String[] idleGlyphs = new String[IDLE_ACTIONS.length];
    private final String[] aimedGlyphs = new String[AIMED_ACTIONS.length];
    private ControllerConfig cachedConfig;
    private ControllerType cachedType;

    public void render(Minecraft client, ControllerRuntime runtime, int screenWidth, int screenHeight) {
        if (client == null || runtime == null || client.fontRenderer == null || client.currentScreen != null) {
            return;
        }
        String status = runtime.runtimeStatusMessage();
        if (!status.isEmpty()) {
            renderStatusBanner(client.fontRenderer, status, screenWidth);
        }

        ControllerConfig config = runtime.latestConfig();
        ControllerSnapshot snapshot = runtime.latestSnapshot();
        ControllerConfig.ResolvedLayout layout = runtime.activeLayout();
        if (config == null || !config.hudHintsEnabled || layout == null) {
            return;
        }
        if (client.player == null || client.world == null || snapshot == null || !snapshot.isConnected()) {
            return;
        }

        refreshGlyphs(config, layout, runtime.glyphs());
        boolean aimed = client.objectMouseOver != null
            && client.objectMouseOver.typeOfHit != RayTraceResult.Type.MISS;
        if (aimed) {
            renderPrompts(client.fontRenderer, aimedGlyphs, AIMED_LABELS, screenWidth, screenHeight);
        } else {
            renderPrompts(client.fontRenderer, idleGlyphs, IDLE_LABELS, screenWidth, screenHeight);
        }
    }

    private void refreshGlyphs(
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout,
        ControllerGlyphService glyphs
    ) {
        ControllerType type = glyphs.activeControllerType();
        if (config == cachedConfig && type == cachedType) {
            return;
        }
        cachedConfig = config;
        cachedType = type;
        for (int i = 0; i < IDLE_ACTIONS.length; i++) {
            idleGlyphs[i] = "[" + glyphs.glyphForBinding(firstBinding(layout, IDLE_ACTIONS[i])) + "]";
        }
        for (int i = 0; i < AIMED_ACTIONS.length; i++) {
            aimedGlyphs[i] = "[" + glyphs.glyphForBinding(firstBinding(layout, AIMED_ACTIONS[i])) + "]";
        }
    }

    private static void renderPrompts(
        FontRenderer font,
        String[] glyphs,
        String[] labels,
        int screenWidth,
        int screenHeight
    ) {
        int totalWidth = 0;
        for (int i = 0; i < glyphs.length; i++) {
            totalWidth += chipWidth(font, glyphs[i], labels[i]) + (i > 0 ? CHIP_GAP : 0);
        }

        int x = (screenWidth - totalWidth) / 2;
        int y = screenHeight - CHIP_BOTTOM_OFFSET;
        for (int i = 0; i < glyphs.length; i++) {
            int width = chipWidth(font, glyphs[i], labels[i]);
            Gui.drawRect(x, y, x + width, y + CHIP_HEIGHT, CHIP_BACKGROUND);
            Gui.drawRect(x, y + CHIP_HEIGHT - 1, x + width, y + CHIP_HEIGHT, CHIP_UNDERLINE);

            int glyphX = x + CHIP_PADDING_X;
            int labelX = glyphX + font.getStringWidth(glyphs[i]) + 4;
            font.drawString(glyphs[i], glyphX, y + 2, GLYPH_COLOR);
            font.drawString(labels[i], labelX, y + 2, LABEL_COLOR);
            x += width + CHIP_GAP;
        }
    }

    private static int chipWidth(FontRenderer font, String glyph, String label) {
        return (CHIP_PADDING_X * 2) + font.getStringWidth(glyph) + 4 + font.getStringWidth(label);
    }

    private static void renderStatusBanner(FontRenderer font, String message, int screenWidth) {
        int width = font.getStringWidth(message) + 16;
        int x = (screenWidth - width) / 2;
        int y = BANNER_TOP;
        Gui.drawRect(x, y, x + width, y + BANNER_HEIGHT, BANNER_BACKGROUND);
        Gui.drawRect(x, y + BANNER_HEIGHT - 1, x + width, y + BANNER_HEIGHT, BANNER_UNDERLINE);
        font.drawStringWithShadow(message, x + ((width - font.getStringWidth(message)) / 2), y + 3, 0xFFFFFFFF);
    }

    private static String firstBinding(ControllerConfig.ResolvedLayout layout, String action) {
        List<String> bindings = layout.actionBindings(action);
        return bindings.isEmpty() ? "?" : bindings.get(0);
    }
}
