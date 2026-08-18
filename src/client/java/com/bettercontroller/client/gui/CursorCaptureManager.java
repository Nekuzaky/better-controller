package com.bettercontroller.client.gui;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Centralised hide/show of the OS cursor while a controller is driving the GUI.
 * The cursor is captured only when the controller has been active in the inventory recently;
 * any mouse activity or screen exit restores it.
 */
public final class CursorCaptureManager {
    private static final long ACTIVE_WINDOW_MS = 1500L;

    private boolean captured;
    private long lastControllerInputMs;

    public void noteControllerInput() {
        lastControllerInputMs = System.currentTimeMillis();
    }

    public void sync(Minecraft client, boolean wantCapture) {
        if (client == null || client.getWindow() == null) {
            captured = false;
            return;
        }
        long handle = client.getWindow().getHandle();
        if (handle == 0L) {
            captured = false;
            return;
        }
        boolean withinWindow = (System.currentTimeMillis() - lastControllerInputMs) <= ACTIVE_WINDOW_MS;
        boolean shouldCapture = wantCapture && withinWindow;

        if (shouldCapture && !captured) {
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
            GLFW.glfwSetCursorPos(handle, 1.0D, 1.0D);
            captured = true;
        } else if (!shouldCapture && captured) {
            release(client);
        }
    }

    public void release(Minecraft client) {
        if (!captured) {
            return;
        }
        if (client != null && client.getWindow() != null) {
            long handle = client.getWindow().getHandle();
            if (handle != 0L) {
                GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            }
        }
        captured = false;
    }
}
