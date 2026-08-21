package com.bettercontroller.client.polling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerAliasParsingTest {
    @Test
    void buttonAliasesResolve() {
        assertEquals(ControllerButton.SOUTH, ControllerButton.fromTokenOrNull("A"));
        assertEquals(ControllerButton.SOUTH, ControllerButton.fromTokenOrNull("cross"));
        assertEquals(ControllerButton.EAST, ControllerButton.fromTokenOrNull("switch_a"));
        assertEquals(ControllerButton.START, ControllerButton.fromTokenOrNull("options"));
    }

    @Test
    void axisAliasesResolve() {
        assertEquals(ControllerAxis.LEFT_X, ControllerAxis.fromTokenOrNull("lx"));
        assertEquals(ControllerAxis.RIGHT_Y, ControllerAxis.fromTokenOrNull("right_y"));
        assertEquals(ControllerAxis.LEFT_TRIGGER, ControllerAxis.fromTokenOrNull("l2"));
        assertEquals(ControllerAxis.RIGHT_TRIGGER, ControllerAxis.fromTokenOrNull("rt"));
    }
}
