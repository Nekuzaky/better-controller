package com.bettercontroller.client.render;

import com.bettercontroller.BetterControllerMod;
import com.bettercontroller.client.gui.ControllerInventorySelectionState;
import com.bettercontroller.client.input.ControllerRuntime;
import com.bettercontroller.client.mixin.HandledScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/**
 * Draws a controller-driven selection highlight on the currently focused inventory slot.
 */
public final class ControllerInventoryHighlightRenderer {
    private static final int SLOT_SIZE = 20;
    private static boolean warnedMissingHandledScreenAccessor;

    public void render(Minecraft client, GuiGraphicsExtractor context, ControllerRuntime runtime) {
        if (client == null || context == null || runtime == null) {
            return;
        }
        if (!(client.gui.screen() instanceof AbstractContainerScreen<?> handledScreen)) {
            return;
        }
        if (runtime.latestSnapshot() == null || !runtime.latestSnapshot().isConnected()) {
            return;
        }
        if (runtime.latestConfig() == null || !runtime.latestConfig().autoActivateOnController) {
            return;
        }

        ControllerInventorySelectionState selectionState = runtime.inventorySelectionState();
        Slot selectedSlot = selectionState.resolveSelectedSlot(handledScreen);
        if (selectedSlot != null && selectedSlot.isActive()) {
            renderSelectionHighlight(handledScreen, context, selectedSlot, selectionState);
        }
    }

    private static void renderSelectionHighlight(
        AbstractContainerScreen<?> handledScreen,
        GuiGraphicsExtractor context,
        Slot selectedSlot,
        ControllerInventorySelectionState selectionState
    ) {
        int screenX = handledScreenX(handledScreen);
        int screenY = handledScreenY(handledScreen);

        int left = screenX + selectedSlot.x - 2;
        int top = screenY + selectedSlot.y - 2;
        int right = left + SLOT_SIZE;
        int bottom = top + SLOT_SIZE;

        long now = System.currentTimeMillis();
        double pulse = 0.5D + (0.5D * Math.sin(now * 0.009D));
        long sinceChange = Math.max(0L, now - selectionState.lastSelectionChangeMs());
        double settle = Math.min(1.0D, sinceChange / 240.0D);
        int grow = (int) Math.round((1.0D - settle) * 1.5D);
        int glowPad = 2 + (int) Math.round((0.5D + 0.5D * Math.sin(now * 0.006D + 1.45D)) * 2.0D);

        int outerAlpha = (int) (52 + (pulse * 28));
        int borderAlpha = (int) (176 + (pulse * 54));
        int innerAlpha = (int) (28 + (settle * 22));

        context.fill(left - glowPad, top - glowPad, right + glowPad, bottom + glowPad, (outerAlpha << 24) | 0x30B8FF);
        context.fill(left, top, right, bottom, (innerAlpha << 24) | 0x58C8FF);
        context.outline(
            left - grow,
            top - grow,
            SLOT_SIZE + (grow * 2),
            SLOT_SIZE + (grow * 2),
            (borderAlpha << 24) | 0xE7F7FF
        );
        context.outline(left + 1, top + 1, SLOT_SIZE - 2, SLOT_SIZE - 2, 0x66FFFFFF);
    }

    private static int handledScreenX(AbstractContainerScreen<?> screen) {
        if (screen instanceof HandledScreenAccessor accessor) {
            return accessor.bettercontroller$getX();
        }
        warnHandledAccessorMissing();
        return 0;
    }

    private static int handledScreenY(AbstractContainerScreen<?> screen) {
        if (screen instanceof HandledScreenAccessor accessor) {
            return accessor.bettercontroller$getY();
        }
        warnHandledAccessorMissing();
        return 0;
    }

    private static void warnHandledAccessorMissing() {
        if (warnedMissingHandledScreenAccessor) {
            return;
        }
        warnedMissingHandledScreenAccessor = true;
        BetterControllerMod.LOGGER.warn("AbstractContainerScreen accessor unavailable; inventory highlight position fallback in use.");
    }
}
