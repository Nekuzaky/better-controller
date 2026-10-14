package com.bettercontroller.client.chat;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.gui.ControllerVirtualKeyboardScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;

/**
 * Opens BetterController's in-game virtual keyboard for the currently focused text field.
 */
public final class VirtualKeyboardLauncher {
    private static final long OPEN_COOLDOWN_MS = 180L;
    private static final long PENDING_TIMEOUT_MS = 3500L;

    private long lastLaunchTimestamp;
    private boolean pendingOpen;
    private long pendingSince;

    public boolean tryOpen(ControllerConfig config) {
        if (!isEnabled(config)) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return false;
        }
        if (client.gui.screen() == null || client.gui.screen() instanceof ControllerVirtualKeyboardScreen) {
            return false;
        }

        long now = monotonicNowMs();
        if ((now - lastLaunchTimestamp) < OPEN_COOLDOWN_MS) {
            return false;
        }

        Screen screen = client.gui.screen();
        EditBox targetField = resolveTargetTextField(screen);
        if (targetField == null) {
            return false;
        }

        lastLaunchTimestamp = now;
        client.gui.setScreen(new ControllerVirtualKeyboardScreen(screen, targetField));
        pendingOpen = false;
        pendingSince = 0L;
        return true;
    }

    public void requestOpenOnNextTextScreen(ControllerConfig config) {
        if (!isEnabled(config)) {
            return;
        }
        pendingOpen = true;
        pendingSince = monotonicNowMs();
    }

    public void processPending(ControllerConfig config) {
        if (!pendingOpen || !isEnabled(config)) {
            return;
        }
        if (tryOpen(config)) {
            return;
        }
        if ((monotonicNowMs() - pendingSince) > PENDING_TIMEOUT_MS) {
            pendingOpen = false;
            pendingSince = 0L;
        }
    }

    private static EditBox resolveTargetTextField(Screen screen) {
        if (screen == null) {
            return null;
        }
        if (screen.getFocused() instanceof EditBox focusedTextField) {
            return focusedTextField;
        }
        for (GuiEventListener element : screen.children()) {
            if (element instanceof EditBox textField) {
                return textField;
            }
        }
        return null;
    }

    private static boolean isEnabled(ControllerConfig config) {
        return config != null && config.virtualKeyboardEnabled;
    }

    /**
     * Elapsed time comes off nanoTime: the wall clock is not monotonic, and an NTP correction
     * stepping it mid-game would skew every interval measured against it.
     */
    private static long monotonicNowMs() {
        return System.nanoTime() / 1_000_000L;
    }

}
