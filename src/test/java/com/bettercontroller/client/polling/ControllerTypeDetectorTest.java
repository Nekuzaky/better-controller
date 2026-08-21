package com.bettercontroller.client.polling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerTypeDetectorTest {
    @Test
    void detectsXboxFromName() {
        ControllerType type = ControllerTypeDetector.detect("Xbox Wireless Controller", "");
        assertEquals(ControllerType.XBOX, type);
    }

    @Test
    void detectsPlayStationFromName() {
        ControllerType type = ControllerTypeDetector.detect("DualSense Wireless Controller", "");
        assertEquals(ControllerType.PLAYSTATION, type);
    }

    @Test
    void detectsSwitchFromName() {
        ControllerType type = ControllerTypeDetector.detect("Nintendo Switch Pro Controller", "");
        assertEquals(ControllerType.SWITCH, type);
    }

    @Test
    void fallsBackToGenericWhenUnknown() {
        ControllerType type = ControllerTypeDetector.detect("Arcade Stick", "abcd");
        assertEquals(ControllerType.GENERIC, type);
    }
}
