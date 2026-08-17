package com.bettercontroller.client.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ControllerConfig {
    public int schemaVersion = 10;
    public boolean autoActivateOnController = true;

    /**
     * Pins the mod to one specific gamepad, so two game instances on the same machine can each
     * drive their own controller. Empty / -1 means "first gamepad found", the historical behaviour.
     */
    public String preferredControllerGuid = "";
    public int preferredJoystickIndex = -1;

    public float movementDeadzone = 0.14F;
    public float lookDeadzone = 0.07F;
    public float lookAntiDeadzone = 0.02F;
    public float lookSensitivityX = 12.0F;
    public float lookSensitivityY = 11.0F;
    public float lookSpeedMultiplier = 2.25F;
    public String lookResponseCurve = "linear";
    public boolean invertLookY = false;
    public boolean cameraSmoothing = false;
    public float cameraSmoothingStrength = 0.35F;
    public float triggerThreshold = 0.45F;
    public float menuAxisThreshold = 0.35F;
    public float menuAxisPressThreshold = 0.40F;
    public float menuAxisReleaseThreshold = 0.20F;
    public int menuInitialRepeatDelayMs = 140;
    public int menuRepeatIntervalMs = 55;

    public boolean hudHintsEnabled = true;
    public boolean debugOverlayEnabled = true;
    public boolean debugLogging = false;
    public boolean virtualKeyboardEnabled = true;

    public AxisBindings axes = defaultAxes();
    public LinkedHashMap<String, List<String>> bindings = defaultBindings();

    public void ensureDefaults() {
        ControllerConfig fallback = createDefault();

        if (schemaVersion <= 0) {
            schemaVersion = fallback.schemaVersion;
        }
        if (axes == null) {
            axes = fallback.axes;
        }
        if (bindings == null || bindings.isEmpty()) {
            bindings = fallback.bindings;
        }
        if (lookResponseCurve == null || lookResponseCurve.isBlank()) {
            lookResponseCurve = fallback.lookResponseCurve;
        }
        if (preferredControllerGuid == null) {
            preferredControllerGuid = fallback.preferredControllerGuid;
        }
        if (lookSpeedMultiplier <= 0.0F) {
            lookSpeedMultiplier = fallback.lookSpeedMultiplier;
        }
        if (lookAntiDeadzone < 0.0F || lookAntiDeadzone > 0.25F) {
            lookAntiDeadzone = fallback.lookAntiDeadzone;
        }
        if (menuAxisThreshold <= 0.0F) {
            menuAxisThreshold = fallback.menuAxisThreshold;
        }
        if (menuAxisPressThreshold <= 0.0F) {
            menuAxisPressThreshold = menuAxisThreshold > 0.0F
                ? menuAxisThreshold
                : fallback.menuAxisPressThreshold;
        }
        if (menuAxisReleaseThreshold <= 0.0F) {
            menuAxisReleaseThreshold = fallback.menuAxisReleaseThreshold;
        }
        if (menuAxisReleaseThreshold >= menuAxisPressThreshold) {
            menuAxisReleaseThreshold = Math.max(0.05F, menuAxisPressThreshold - 0.10F);
        }
        if (menuInitialRepeatDelayMs <= 0) {
            menuInitialRepeatDelayMs = fallback.menuInitialRepeatDelayMs;
        }
        if (menuRepeatIntervalMs <= 0) {
            menuRepeatIntervalMs = fallback.menuRepeatIntervalMs;
        }

        mergeMissingAxes(axes, fallback.axes);
        mergeMissingBindings(bindings, fallback.bindings);
    }

    public ResolvedLayout resolveLayout() {
        return new ResolvedLayout(axes, bindings);
    }

    public static ControllerConfig createDefault() {
        return new ControllerConfig();
    }

    private static AxisBindings defaultAxes() {
        AxisBindings result = new AxisBindings();
        result.move_x = "LEFT_X";
        result.move_y = "LEFT_Y";
        result.look_x = "RIGHT_X";
        result.look_y = "RIGHT_Y";
        return result;
    }

    private static LinkedHashMap<String, List<String>> defaultBindings() {
        return mapOfActions(
            entry("jump", "A"),
            entry("sneak", "R3"),
            entry("sprint", "L3"),
            entry("attack", "RT"),
            entry("use", "LT"),
            entry("inventory", "Y"),
            entry("drop_item", "B"),
            entry("swap_hands", "X"),
            entry("open_chat", "DPAD_UP"),
            entry("toggle_perspective", "DPAD_DOWN"),
            entry("pause", "START"),
            entry("player_list", "BACK"),
            entry("pick_block", "RB"),
            entry("hotbar_next", "RB"),
            entry("hotbar_previous", "LB"),
            entry("hotbar_1"),
            entry("hotbar_2"),
            entry("hotbar_3"),
            entry("hotbar_4"),
            entry("hotbar_5"),
            entry("hotbar_6"),
            entry("hotbar_7"),
            entry("hotbar_8"),
            entry("hotbar_9"),
            entry("menu_up", "DPAD_UP", "-LEFT_Y"),
            entry("menu_down", "DPAD_DOWN", "LEFT_Y"),
            entry("menu_left", "DPAD_LEFT", "-LEFT_X"),
            entry("menu_right", "DPAD_RIGHT", "LEFT_X"),
            entry("menu_confirm", "A"),
            entry("menu_back", "B"),
            entry("menu_page_next", "RT"),
            entry("menu_page_prev", "LT"),
            entry("menu_tab_next", "RB"),
            entry("menu_tab_prev", "LB")
        );
    }

    private static LinkedHashMap<String, List<String>> mapOfActions(ActionEntry... entries) {
        LinkedHashMap<String, List<String>> map = new LinkedHashMap<>();
        for (ActionEntry entry : entries) {
            map.put(entry.key, new ArrayList<>(Arrays.asList(entry.bindings)));
        }
        return map;
    }

    private static ActionEntry entry(String key, String... bindings) {
        return new ActionEntry(key, bindings);
    }

    private static void mergeMissingAxes(AxisBindings target, AxisBindings fallback) {
        if (target == null || fallback == null) {
            return;
        }
        if (target.move_x == null || target.move_x.isBlank()) {
            target.move_x = fallback.move_x;
        }
        if (target.move_y == null || target.move_y.isBlank()) {
            target.move_y = fallback.move_y;
        }
        if (target.look_x == null || target.look_x.isBlank()) {
            target.look_x = fallback.look_x;
        }
        if (target.look_y == null || target.look_y.isBlank()) {
            target.look_y = fallback.look_y;
        }
    }

    private static void mergeMissingBindings(
        LinkedHashMap<String, List<String>> target,
        LinkedHashMap<String, List<String>> fallback
    ) {
        for (Map.Entry<String, List<String>> entry : fallback.entrySet()) {
            target.putIfAbsent(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    private record ActionEntry(String key, String[] bindings) {
    }

    public static final class ResolvedLayout {
        private final AxisBindings axes;
        private final LinkedHashMap<String, List<String>> bindings;

        private ResolvedLayout(AxisBindings axes, LinkedHashMap<String, List<String>> bindings) {
            this.axes = axes;
            this.bindings = bindings;
        }

        public String axisToken(String axisKey) {
            return axes == null ? null : axes.axisToken(axisKey);
        }

        public List<String> actionBindings(String actionKey) {
            if (bindings == null) {
                return List.of();
            }
            List<String> value = bindings.get(actionKey);
            return value == null ? List.of() : value;
        }
    }

    public static final class AxisBindings {
        public String move_x = "LEFT_X";
        public String move_y = "LEFT_Y";
        public String look_x = "RIGHT_X";
        public String look_y = "RIGHT_Y";

        public String axisToken(String axisKey) {
            if (axisKey == null) {
                return null;
            }
            return switch (axisKey.toLowerCase(Locale.ROOT)) {
                case "move_x" -> move_x;
                case "move_y" -> move_y;
                case "look_x" -> look_x;
                case "look_y" -> look_y;
                default -> null;
            };
        }
    }
}
