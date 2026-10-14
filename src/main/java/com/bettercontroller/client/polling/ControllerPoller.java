package com.bettercontroller.client.polling;

import com.bettercontroller.BetterControllerMod;

import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * Finds the controller and reads it.
 *
 * <p>Identical in behaviour to the Fabric versions, except that every GLFW call goes through
 * {@link GlfwBridge} instead of {@code org.lwjgl.glfw.GLFW} - 1.12.2 runs on LWJGL 2 and GLFW is
 * kept in a classloader of its own.
 */
public final class ControllerPoller {
    private final GlfwBridge glfw;
    private final ControllerSnapshot snapshot = new ControllerSnapshot();

    // Filled by the bridge on every read; indexed by GLFW id, not by enum ordinal.
    private final float[] rawAxes = new float[GlfwGamepad.AXIS_COUNT];
    private final boolean[] rawButtons = new boolean[GlfwGamepad.BUTTON_COUNT];

    // Hoisted so the per-tick lookups below stay allocation-free: a bound method reference
    // allocates each time it is evaluated, and findJoystick runs every tick.
    private final IntPredicate usableGamepad = new IntPredicate() {
        @Override
        public boolean test(int joystickId) {
            return isUsableGamepad(joystickId);
        }
    };
    private final IntFunction<String> guidOf = new IntFunction<String>() {
        @Override
        public String apply(int joystickId) {
            return glfw.joystickGuid(joystickId);
        }
    };

    private int activeJoystickId = -1;
    private ControllerType activeControllerType = ControllerType.NONE;
    private boolean warnedUnresolvedPreference;
    private int unmappedJoystickId = -1;
    private String unmappedJoystick;

    public ControllerPoller(GlfwBridge glfw) {
        this.glfw = glfw;
    }

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
                BetterControllerMod.logger().info("Controller disconnected.");
                activeJoystickId = -1;
                activeControllerType = ControllerType.NONE;
                snapshot.clearConnection();
            }
            return snapshot;
        }

        String joystickName = glfw.joystickName(detectedJoystick);
        String joystickGuid = glfw.joystickGuid(detectedJoystick);
        ControllerType detectedType = ControllerTypeDetector.detect(joystickName, joystickGuid);

        if (activeJoystickId != detectedJoystick) {
            activeJoystickId = detectedJoystick;
            activeControllerType = detectedType;
            BetterControllerMod.logger().info(
                "Controller connected: {} (type: {}, guid: {})",
                joystickName.isEmpty() ? "Unknown" : joystickName,
                activeControllerType,
                joystickGuid.isEmpty() ? "n/a" : joystickGuid
            );
        }
        if (activeControllerType != detectedType) {
            BetterControllerMod.logger().info(
                "Controller type changed from {} to {} ({}).",
                activeControllerType,
                detectedType,
                joystickName.isEmpty() ? "Unknown" : joystickName
            );
        }

        if (!glfw.readGamepad(activeJoystickId, rawAxes, rawButtons)) {
            snapshot.clearConnection();
            return snapshot;
        }

        snapshot.update(activeJoystickId, joystickName, joystickGuid, detectedType, rawAxes, rawButtons);
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
        if (!glfw.readGamepad(activeJoystickId, rawAxes, rawButtons)) {
            return snapshot;
        }
        snapshot.refreshAxes(rawAxes);
        return snapshot;
    }

    public ControllerType activeControllerType() {
        return activeControllerType;
    }

    private int findJoystick(String preferredGuid, int preferredIndex) {
        if (hasPreference(preferredGuid, preferredIndex)) {
            int preferred = resolvePreferred(preferredGuid, preferredIndex, usableGamepad, guidOf);
            if (preferred != -1) {
                warnedUnresolvedPreference = false;
                return preferred;
            }
            // The reason there is no controller is the preference, not a missing mapping. Leaving a
            // stale unmapped device here would make the F8 overlay blame the wrong thing.
            clearUnmappedJoystick();
            warnUnresolvedPreference(preferredGuid, preferredIndex);
            return -1;
        }
        warnedUnresolvedPreference = false;

        if (isUsableGamepad(activeJoystickId)) {
            clearUnmappedJoystick();
            return activeJoystickId;
        }

        for (int joystickId = GlfwGamepad.JOYSTICK_1; joystickId <= GlfwGamepad.JOYSTICK_LAST; joystickId++) {
            if (isUsableGamepad(joystickId)) {
                clearUnmappedJoystick();
                return joystickId;
            }
        }

        scanForUnmappedJoystick();
        return -1;
    }

    /**
     * A joystick GLFW has no mapping for is not reported as a gamepad, so it is invisible to the
     * rest of the mod. Naming it - with the GUID a mapping line needs - turns a dead end into
     * something the player can act on.
     */
    private void scanForUnmappedJoystick() {
        for (int joystickId = GlfwGamepad.JOYSTICK_1; joystickId <= GlfwGamepad.JOYSTICK_LAST; joystickId++) {
            if (!glfw.joystickPresent(joystickId) || glfw.joystickIsGamepad(joystickId)) {
                continue;
            }
            if (unmappedJoystickId != joystickId) {
                // Built once per device change, not once per tick.
                String name = glfw.joystickName(joystickId);
                String guid = glfw.joystickGuid(joystickId);
                String shownName = name.isEmpty() ? "Unknown" : name;
                String shownGuid = guid.isEmpty() ? "n/a" : guid;
                unmappedJoystickId = joystickId;
                unmappedJoystick = shownName + "  " + shownGuid;
                BetterControllerMod.logger().warn(
                    "'{}' (guid: {}) is connected, but GLFW has no gamepad mapping for it, so it cannot be used. "
                        + "Add a line for it to config/{} - mappings are published at {}.",
                    shownName,
                    shownGuid,
                    ControllerMappings.MAPPINGS_FILE_NAME,
                    ControllerMappings.DATABASE_URL
                );
            }
            return;
        }
        clearUnmappedJoystick();
    }

    private void clearUnmappedJoystick() {
        unmappedJoystickId = -1;
        unmappedJoystick = null;
    }

    /** Name and GUID of a connected-but-unmapped joystick, or null. Shown by the F8 overlay. */
    public String unmappedJoystick() {
        return unmappedJoystick;
    }

    static boolean hasPreference(String preferredGuid, int preferredIndex) {
        return (preferredGuid != null && !preferredGuid.trim().isEmpty()) || preferredIndex >= 0;
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
        boolean wantsGuid = preferredGuid != null && !preferredGuid.trim().isEmpty();
        String wantedGuid = wantsGuid ? preferredGuid.trim() : null;

        if (preferredIndex >= GlfwGamepad.JOYSTICK_1 && preferredIndex <= GlfwGamepad.JOYSTICK_LAST
            && usable.test(preferredIndex)
            && (!wantsGuid || matchesGuid(guidOf.apply(preferredIndex), wantedGuid))) {
            return preferredIndex;
        }

        if (wantsGuid) {
            for (int joystickId = GlfwGamepad.JOYSTICK_1; joystickId <= GlfwGamepad.JOYSTICK_LAST; joystickId++) {
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

    private boolean isUsableGamepad(int joystickId) {
        return joystickId >= GlfwGamepad.JOYSTICK_1
            && joystickId <= GlfwGamepad.JOYSTICK_LAST
            && glfw.joystickPresent(joystickId)
            && glfw.joystickIsGamepad(joystickId);
    }

    private void warnUnresolvedPreference(String preferredGuid, int preferredIndex) {
        if (warnedUnresolvedPreference) {
            return;
        }
        warnedUnresolvedPreference = true;
        BetterControllerMod.logger().warn(
            "No controller matches the configured preference (guid: {}, index: {}). "
                + "Ignoring every other gamepad; clear preferredControllerGuid / preferredJoystickIndex to use any.",
            preferredGuid == null || preferredGuid.trim().isEmpty() ? "any" : preferredGuid,
            preferredIndex
        );
    }
}
