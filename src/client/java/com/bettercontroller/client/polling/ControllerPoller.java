package com.bettercontroller.client.polling;

import com.bettercontroller.BetterControllerMod;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import java.util.function.IntFunction;
import java.util.function.IntPredicate;

public final class ControllerPoller {
    private final GLFWGamepadState state = GLFWGamepadState.create();
    private final ControllerSnapshot snapshot = new ControllerSnapshot();
    private int activeJoystickId = -1;
    private ControllerType activeControllerType = ControllerType.NONE;
    private boolean warnedUnresolvedPreference;

    public ControllerSnapshot pollSnapshot() {
        return pollSnapshot("", -1);
    }

    /**
     * @param preferredGuid  GLFW GUID to pin to, or blank for no preference. Identifies a device
     *                       <em>model</em>, so two identical pads share one GUID - use the index
     *                       to tell those apart.
     * @param preferredIndex GLFW joystick slot to pin to, or -1 for no preference.
     */
    public ControllerSnapshot pollSnapshot(String preferredGuid, int preferredIndex) {
        int detectedJoystick = findJoystick(preferredGuid, preferredIndex);
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

    private int findJoystick(String preferredGuid, int preferredIndex) {
        if (hasPreference(preferredGuid, preferredIndex)) {
            // Method references without captures link once and are reused, so this stays
            // allocation-free per tick - and keeps GLFW out of this class's static init,
            // which is what lets the selection logic be unit tested without natives.
            int preferred = resolvePreferred(
                preferredGuid,
                preferredIndex,
                ControllerPoller::isUsableGamepad,
                GLFW::glfwGetJoystickGUID
            );
            if (preferred != -1) {
                warnedUnresolvedPreference = false;
                return preferred;
            }
            warnUnresolvedPreference(preferredGuid, preferredIndex);
            return -1;
        }
        warnedUnresolvedPreference = false;

        if (isUsableGamepad(activeJoystickId)) {
            return activeJoystickId;
        }

        for (int joystickId = GLFW.GLFW_JOYSTICK_1; joystickId <= GLFW.GLFW_JOYSTICK_LAST; joystickId++) {
            if (isUsableGamepad(joystickId)) {
                return joystickId;
            }
        }

        return -1;
    }

    static boolean hasPreference(String preferredGuid, int preferredIndex) {
        return (preferredGuid != null && !preferredGuid.isBlank()) || preferredIndex >= 0;
    }

    /**
     * Picks the joystick the config asked for. The index wins when it points at a usable gamepad
     * that also satisfies the GUID (if one is set), which is what tells two identical pads apart;
     * otherwise the first gamepad matching the GUID is used. Returns -1 when the preference cannot
     * be honoured - deliberately, rather than grabbing some other pad, so a second game instance
     * never steals the first one's controller.
     *
     * <p>Package-private and parameterised over the device lookups so it can be tested without GLFW.
     */
    static int resolvePreferred(
        String preferredGuid,
        int preferredIndex,
        IntPredicate usable,
        IntFunction<String> guidOf
    ) {
        boolean wantsGuid = preferredGuid != null && !preferredGuid.isBlank();
        String wantedGuid = wantsGuid ? preferredGuid.trim() : null;

        if (preferredIndex >= GLFW.GLFW_JOYSTICK_1 && preferredIndex <= GLFW.GLFW_JOYSTICK_LAST
            && usable.test(preferredIndex)
            && (!wantsGuid || matchesGuid(guidOf.apply(preferredIndex), wantedGuid))) {
            return preferredIndex;
        }

        if (wantsGuid) {
            for (int joystickId = GLFW.GLFW_JOYSTICK_1; joystickId <= GLFW.GLFW_JOYSTICK_LAST; joystickId++) {
                if (usable.test(joystickId) && matchesGuid(guidOf.apply(joystickId), wantedGuid)) {
                    return joystickId;
                }
            }
        }

        return -1;
    }

    private static boolean matchesGuid(String candidate, String wanted) {
        return candidate != null && candidate.equalsIgnoreCase(wanted);
    }

    private static boolean isUsableGamepad(int joystickId) {
        return joystickId >= GLFW.GLFW_JOYSTICK_1
            && joystickId <= GLFW.GLFW_JOYSTICK_LAST
            && GLFW.glfwJoystickPresent(joystickId)
            && GLFW.glfwJoystickIsGamepad(joystickId);
    }

    private void warnUnresolvedPreference(String preferredGuid, int preferredIndex) {
        if (warnedUnresolvedPreference) {
            return;
        }
        warnedUnresolvedPreference = true;
        BetterControllerMod.LOGGER.warn(
            "No controller matches the configured preference (guid: {}, index: {}). "
                + "Ignoring every other gamepad; clear preferredControllerGuid / preferredJoystickIndex to use any.",
            preferredGuid == null || preferredGuid.isBlank() ? "any" : preferredGuid,
            preferredIndex
        );
    }
}
