package com.bettercontroller.client.polling;

import org.lwjgl.glfw.GLFWGamepadState;

public final class ControllerSnapshot {
    private static final ControllerButton[] BUTTONS = ControllerButton.values();
    private static final ControllerAxis[] AXES = ControllerAxis.values();

    private int joystickId = -1;
    private String joystickName = "Unknown";
    private String joystickGuid = "";
    private ControllerType controllerType = ControllerType.NONE;
    private final boolean[] buttons = new boolean[BUTTONS.length];
    private final float[] axes = new float[AXES.length];

    public void update(
        int joystickId,
        String joystickName,
        String joystickGuid,
        ControllerType controllerType,
        GLFWGamepadState state
    ) {
        this.joystickId = joystickId;
        this.joystickName = joystickName == null ? "Unknown" : joystickName;
        this.joystickGuid = joystickGuid == null ? "" : joystickGuid;
        this.controllerType = controllerType == null ? ControllerType.GENERIC : controllerType;

        for (int i = 0; i < BUTTONS.length; i++) {
            buttons[i] = state.buttons(BUTTONS[i].glfwButtonId()) != 0;
        }
        refreshAxes(state);
    }

    /**
     * Refreshes only the analog axes. Called once per rendered frame so the camera reads a stick
     * position sampled this frame instead of one sampled up to a full client tick ago.
     */
    public void refreshAxes(GLFWGamepadState state) {
        for (int i = 0; i < AXES.length; i++) {
            axes[i] = state.axes(AXES[i].glfwAxisId());
        }
    }

    public void clearConnection() {
        joystickId = -1;
        joystickName = "Disconnected";
        joystickGuid = "";
        controllerType = ControllerType.NONE;
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = false;
        }
        for (int i = 0; i < axes.length; i++) {
            axes[i] = 0.0F;
        }
    }

    public int joystickId() {
        return joystickId;
    }

    public String joystickName() {
        return joystickName;
    }

    public String joystickGuid() {
        return joystickGuid;
    }

    public ControllerType controllerType() {
        return controllerType;
    }

    public boolean isConnected() {
        return joystickId >= 0;
    }

    public static ControllerSnapshot forTest() {
        ControllerSnapshot s = new ControllerSnapshot();
        s.joystickId = 0;
        s.controllerType = ControllerType.GENERIC;
        return s;
    }

    public void simulateButton(ControllerButton button, boolean pressed) {
        buttons[button.ordinal()] = pressed;
    }

    public void simulateAxis(ControllerAxis axis, float value) {
        axes[axis.ordinal()] = value;
    }

    public boolean isPressed(ControllerButton button) {
        return buttons[button.ordinal()];
    }

    public float axis(ControllerAxis axis) {
        return axes[axis.ordinal()];
    }
}
