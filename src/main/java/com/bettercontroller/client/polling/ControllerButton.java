package com.bettercontroller.client.polling;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum ControllerButton {
    SOUTH(GlfwGamepad.BUTTON_A, "SOUTH", "A", "CROSS", "XBOX_A", "PS_CROSS", "SWITCH_B", "NINTENDO_B"),
    EAST(GlfwGamepad.BUTTON_B, "EAST", "B", "CIRCLE", "XBOX_B", "PS_CIRCLE", "SWITCH_A", "NINTENDO_A"),
    WEST(GlfwGamepad.BUTTON_X, "WEST", "X", "SQUARE", "XBOX_X", "PS_SQUARE", "SWITCH_Y", "NINTENDO_Y"),
    NORTH(GlfwGamepad.BUTTON_Y, "NORTH", "Y", "TRIANGLE", "XBOX_Y", "PS_TRIANGLE", "SWITCH_X", "NINTENDO_X"),
    LEFT_BUMPER(GlfwGamepad.BUTTON_LEFT_BUMPER, "LEFT_BUMPER", "LB", "L1"),
    RIGHT_BUMPER(GlfwGamepad.BUTTON_RIGHT_BUMPER, "RIGHT_BUMPER", "RB", "R1"),
    BACK(GlfwGamepad.BUTTON_BACK, "BACK", "SELECT", "VIEW", "SHARE", "MINUS"),
    START(GlfwGamepad.BUTTON_START, "START", "MENU", "OPTIONS", "PLUS"),
    GUIDE(GlfwGamepad.BUTTON_GUIDE, "GUIDE", "HOME", "PS"),
    LEFT_STICK(GlfwGamepad.BUTTON_LEFT_THUMB, "LEFT_STICK", "L3"),
    RIGHT_STICK(GlfwGamepad.BUTTON_RIGHT_THUMB, "RIGHT_STICK", "R3"),
    DPAD_UP(GlfwGamepad.BUTTON_DPAD_UP, "DPAD_UP"),
    DPAD_RIGHT(GlfwGamepad.BUTTON_DPAD_RIGHT, "DPAD_RIGHT"),
    DPAD_DOWN(GlfwGamepad.BUTTON_DPAD_DOWN, "DPAD_DOWN"),
    DPAD_LEFT(GlfwGamepad.BUTTON_DPAD_LEFT, "DPAD_LEFT");

    private static final Map<String, ControllerButton> LOOKUP = new HashMap<String, ControllerButton>();

    static {
        for (ControllerButton button : values()) {
            for (String alias : button.aliases) {
                LOOKUP.put(normalize(alias), button);
            }
        }
    }

    private final int glfwButtonId;
    private final String[] aliases;

    ControllerButton(int glfwButtonId, String... aliases) {
        this.glfwButtonId = glfwButtonId;
        this.aliases = aliases;
    }

    public int glfwButtonId() {
        return glfwButtonId;
    }

    public static ControllerButton fromTokenOrNull(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        return LOOKUP.get(normalize(token));
    }

    private static String normalize(String token) {
        return token.trim()
            .toUpperCase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_');
    }
}
