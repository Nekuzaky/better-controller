package com.bettercontroller.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * Shared state for controller-driven inventory slot selection across navigation and rendering.
 */
public final class ControllerInventorySelectionState {
    private Screen trackedScreen;
    private int selectedSlotId = -1;
    private long lastSelectionChangeMs;

    public void onScreenChanged(Screen screen) {
        if (screen == trackedScreen) {
            return;
        }
        trackedScreen = screen;
        selectedSlotId = -1;
        lastSelectionChangeMs = 0L;
    }

    public void setSelectedSlot(AbstractContainerScreen<?> screen, Slot slot) {
        if (screen == null || slot == null) {
            return;
        }
        if (trackedScreen != screen || selectedSlotId != slot.index) {
            lastSelectionChangeMs = System.currentTimeMillis();
        }
        trackedScreen = screen;
        selectedSlotId = slot.index;
    }

    public Slot resolveSelectedSlot(AbstractContainerScreen<?> screen) {
        if (screen == null || trackedScreen != screen || selectedSlotId < 0 || screen.getMenu() == null) {
            return null;
        }
        List<Slot> slots = screen.getMenu().slots;
        for (Slot slot : slots) {
            if (slot != null && slot.index == selectedSlotId) {
                return slot;
            }
        }
        return null;
    }

    public long lastSelectionChangeMs() {
        return lastSelectionChangeMs;
    }

    public void clear() {
        trackedScreen = null;
        selectedSlotId = -1;
        lastSelectionChangeMs = 0L;
    }
}
