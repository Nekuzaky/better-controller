package com.bettercontroller.client.polling;

import com.bettercontroller.BetterControllerMod;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

public final class ControllerPoller {
    private final GLFWGamepadState state = GLFWGamepadState.create();
    private final ControllerSnapshot snapshot = new ControllerSnapshot();
    private int activeJoystickId = -1;
    private ControllerType activeControllerType = ControllerType.NONE;

    public ControllerSnapshot pollSnapshot() {
        int detectedJoystick = findJoystick();
        if (detectedJoystick == -1) {
            if (activeJoystickId != -1) {
                BetterControllerMod.LOGGER.info("Controller disconnected.");
                activeJoystickId = -1;
                activeControllerType = ControllerType.NONE;
                snapshot.clearConnection();
            }
            return snapshot;
        }

        String joystickName = GLFW.glfwGetJoystickName(detectedJoystick);
        String joystickGuid = GLFW.glfwGetJoystickGUID(detectedJoystick);
        ControllerType detectedType = ControllerTypeDetector.detect(joystickName, joystickGuid);

        if (activeJoystickId != detectedJoystick) {
            activeJoystickId = detectedJoystick;
            activeControllerType = detectedType;
            BetterControllerMod.LOGGER.info(
                "Controller connected: {} (type: {}, guid: {})",
                joystickName != null ? joystickName : "Unknown",
                activeControllerType,
                joystickGuid != null ? joystickGuid : "n/a"
            );
        }
        if (activeControllerType != detectedType) {
            BetterControllerMod.LOGGER.info(
                "Controller type changed from {} to {} ({}).",
                activeControllerType,
                detectedType,
                joystickName != null ? joystickName : "Unknown"
            );
        }

        if (!GLFW.glfwGetGamepadState(activeJoystickId, state)) {
            snapshot.clearConnection();
            return snapshot;
        }

        snapshot.update(activeJoystickId, joystickName, joystickGuid, detectedType, state);
        activeControllerType = detectedType;
        return snapshot;
    }

    /**
     * Re-reads the analog axes of the already-detected controller. Cheap enough to run every
     * rendered frame: one GLFW call, no device enumeration, no name/GUID lookup, no allocation.
     * Returns the same reused snapshot, untouched when no controller is connected.
     */
    public ControllerSnapshot refreshAxes() {
        if (activeJoystickId == -1 || !snapshot.isConnected()) {
            return snapshot;
        }
        if (!GLFW.glfwGetGamepadState(activeJoystickId, state)) {
            return snapshot;
        }
        snapshot.refreshAxes(state);
        return snapshot;
    }

    public ControllerType activeControllerType() {
        return activeControllerType;
    }

    private int findJoystick() {
        if (activeJoystickId != -1 && GLFW.glfwJoystickPresent(activeJoystickId) && GLFW.glfwJoystickIsGamepad(activeJoystickId)) {
            return activeJoystickId;
        }

        for (int joystickId = GLFW.GLFW_JOYSTICK_1; joystickId <= GLFW.GLFW_JOYSTICK_LAST; joystickId++) {
            if (GLFW.glfwJoystickPresent(joystickId) && GLFW.glfwJoystickIsGamepad(joystickId)) {
                return joystickId;
            }
        }

        return -1;
    }
}
