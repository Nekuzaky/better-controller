package com.bettercontroller.client.polling;

/**
 * The GLFW gamepad constants, spelled out.
 *
 * <p>On every other version the mod reads these straight off {@code org.lwjgl.glfw.GLFW}. Here it
 * cannot: GLFW lives in an isolated classloader (see {@link GlfwBridge}) and naming that class
 * from ordinary mod code would try to load it through Minecraft's classloader, which has no idea
 * it exists.
 *
 * <p>Compile-time constants would in fact be folded into the bytecode and work by accident, but
 * that is a trap for whoever touches this next - one non-constant reference and the mod dies at
 * runtime with a NoClassDefFoundError. Writing the values out keeps the isolation honest.
 *
 * <p>Values verified against lwjgl-glfw 3.3.3. They are part of GLFW's public ABI and have not
 * changed since the gamepad API landed in 3.3.
 */
public final class GlfwGamepad {

    public static final int JOYSTICK_1 = 0;
    public static final int JOYSTICK_LAST = 15;

    public static final int AXIS_LEFT_X = 0;
    public static final int AXIS_LEFT_Y = 1;
    public static final int AXIS_RIGHT_X = 2;
    public static final int AXIS_RIGHT_Y = 3;
    public static final int AXIS_LEFT_TRIGGER = 4;
    public static final int AXIS_RIGHT_TRIGGER = 5;
    public static final int AXIS_COUNT = 6;

    public static final int BUTTON_A = 0;
    public static final int BUTTON_B = 1;
    public static final int BUTTON_X = 2;
    public static final int BUTTON_Y = 3;
    public static final int BUTTON_LEFT_BUMPER = 4;
    public static final int BUTTON_RIGHT_BUMPER = 5;
    public static final int BUTTON_BACK = 6;
    public static final int BUTTON_START = 7;
    public static final int BUTTON_GUIDE = 8;
    public static final int BUTTON_LEFT_THUMB = 9;
    public static final int BUTTON_RIGHT_THUMB = 10;
    public static final int BUTTON_DPAD_UP = 11;
    public static final int BUTTON_DPAD_RIGHT = 12;
    public static final int BUTTON_DPAD_DOWN = 13;
    public static final int BUTTON_DPAD_LEFT = 14;
    public static final int BUTTON_COUNT = 15;

    private GlfwGamepad() {
    }
}
