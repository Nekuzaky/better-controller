package com.bettercontroller.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerConfigSanitizeTest {
    private static ControllerConfig sanitized(java.util.function.Consumer<ControllerConfig> setup) {
        ControllerConfig config = ControllerConfig.createDefault();
        setup.accept(config);
        ControllerConfigManager.sanitize(config);
        return config;
    }

    @Test
    void theDefaultsSurviveSanitizingUnchanged() {
        ControllerConfig before = ControllerConfig.createDefault();
        ControllerConfig after = sanitized(config -> { });

        assertEquals(before.menuInitialRepeatDelayMs, after.menuInitialRepeatDelayMs);
        assertEquals(before.menuRepeatIntervalMs, after.menuRepeatIntervalMs);
        assertEquals(before.lookSensitivityX, after.lookSensitivityX);
        assertEquals(before.preferredJoystickIndex, after.preferredJoystickIndex);
    }

    // --- ranged magnitudes clamp to the nearest bound -------------------------

    @Test
    void aRepeatIntervalPastTheTopClampsToTheTop() {
        assertEquals(200, sanitized(c -> c.menuRepeatIntervalMs = 500).menuRepeatIntervalMs);
    }

    @Test
    void aRepeatIntervalBelowTheBottomClampsToTheBottom() {
        assertEquals(20, sanitized(c -> c.menuRepeatIntervalMs = 1).menuRepeatIntervalMs);
    }

    @Test
    void anInitialDelayPastTheTopClampsToTheTop() {
        assertEquals(400, sanitized(c -> c.menuInitialRepeatDelayMs = 9_000).menuInitialRepeatDelayMs);
    }

    @Test
    void aSensitivityPastTheTopClampsToTheTop() {
        assertEquals(80.0f, sanitized(c -> c.lookSensitivityX = 500.0f).lookSensitivityX);
    }

    /** Ints and floats answer an out-of-range value the same way now. */
    @Test
    void intsAndFloatsAgreeOnWhatOutOfRangeMeans() {
        ControllerConfig config = sanitized(c -> {
            c.lookSensitivityX = 500.0f;
            c.menuRepeatIntervalMs = 500;
        });

        assertEquals(80.0f, config.lookSensitivityX);
        assertEquals(200, config.menuRepeatIntervalMs);
    }

    // --- an index is not a magnitude ------------------------------------------

    @Test
    void anImpossibleJoystickIndexFallsBackToFirstFound() {
        assertEquals(-1, sanitized(c -> c.preferredJoystickIndex = 99).preferredJoystickIndex);
        assertEquals(-1, sanitized(c -> c.preferredJoystickIndex = -7).preferredJoystickIndex);
    }

    @Test
    void aUsableJoystickIndexIsKept() {
        assertEquals(3, sanitized(c -> c.preferredJoystickIndex = 3).preferredJoystickIndex);
    }

    @Test
    void aNonFiniteFloatFallsBackToItsDefault() {
        assertEquals(0.14f, sanitized(c -> c.movementDeadzone = Float.NaN).movementDeadzone);
        assertEquals(12.0f, sanitized(c -> c.lookSensitivityX = Float.POSITIVE_INFINITY).lookSensitivityX);
    }

    @Test
    void theReleaseThresholdIsPushedBelowThePressThreshold() {
        ControllerConfig config = sanitized(c -> {
            c.menuAxisPressThreshold = 0.40f;
            c.menuAxisReleaseThreshold = 0.60f;
        });

        org.junit.jupiter.api.Assertions.assertTrue(
            config.menuAxisReleaseThreshold < config.menuAxisPressThreshold
        );
    }
}
