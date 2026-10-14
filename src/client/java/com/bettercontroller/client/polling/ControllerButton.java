package com.bettercontroller.client.polling;

import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum ControllerButton {
    SOUTH(GLFW.GLFW_GAMEPAD_BUTTON_A, "SOUTH", "A", "CROSS", "XBOX_A", "PS_CROSS", "SWITCH_B", "NINTENDO_B"),
    EAST(GLFW.GLFW_GAMEPAD_BUTTON_B, "EAST", "B", "CIRCLE", "XBOX_B", "PS_CIRCLE", "SWITCH_A", "NINTENDO_A"),
    WEST(GLFW.GLFW_GAMEPAD_BUTTON_X, "WEST", "X", "SQUARE", "XBOX_X", "PS_SQUARE", "SWITCH_Y", "NINTENDO_Y"),
    NORTH(GLFW.GLFW_GAMEPAD_BUTTON_Y, "NORTH", "Y", "TRIANGLE", "XBOX_Y", "PS_TRIANGLE", "SWITCH_X", "NINTENDO_X"),
    LEFT_BUMPER(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER, "LEFT_BUMPER", "LB", "L1"),
    RIGHT_BUMPER(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER, "RIGHT_BUMPER", "RB", "R1"),
    BACK(GLFW.GLFW_GAMEPAD_BUTTON_BACK, "BACK", "SELECT", "VIEW", "SHARE", "MINUS"),
    START(GLFW.GLFW_GAMEPAD_BUTTON_START, "START", "MENU", "OPTIONS", "PLUS"),
    GUIDE(GLFW.GLFW_GAMEPAD_BUTTON_GUIDE, "GUIDE", "HOME", "PS"),
    LEFT_STICK(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB, "LEFT_STICK", "L3"),
    RIGHT_STICK(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB, "RIGHT_STICK", "R3"),
    DPAD_UP(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP, "DPAD_UP"),
    DPAD_RIGHT(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT, "DPAD_RIGHT"),
    DPAD_DOWN(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN, "DPAD_DOWN"),
    DPAD_LEFT(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT, "DPAD_LEFT");

    private static final Map<String, ControllerButton> LOOKUP = new HashMap<>();

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
        if (token == null || token.isBlank()) {
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
