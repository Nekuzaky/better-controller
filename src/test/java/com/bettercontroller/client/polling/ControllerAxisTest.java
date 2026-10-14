package com.bettercontroller.client.polling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerAxisTest {
    @Test
    void sticksRestAtZero() {
        assertEquals(0.0f, ControllerAxis.LEFT_X.restingValue());
        assertEquals(0.0f, ControllerAxis.RIGHT_Y.restingValue());
    }

    @Test
    void triggersRestAtMinusOne() {
        assertEquals(-1.0f, ControllerAxis.LEFT_TRIGGER.restingValue());
        assertEquals(-1.0f, ControllerAxis.RIGHT_TRIGGER.restingValue());
    }

    @Test
    void aReleasedTriggerNormalizesToZero() {
        assertEquals(0.0f, ControllerAxis.RIGHT_TRIGGER.normalizePull(-1.0f), 0.0001f);
    }

    @Test
    void aFullyPulledTriggerNormalizesToOne() {
        assertEquals(1.0f, ControllerAxis.RIGHT_TRIGGER.normalizePull(1.0f), 0.0001f);
    }

    @Test
    void aHalfPulledTriggerNormalizesToAHalf() {
        assertEquals(0.5f, ControllerAxis.RIGHT_TRIGGER.normalizePull(0.0f), 0.0001f);
    }

    @Test
    void outOfRangeTriggerReadingsAreClamped() {
        assertEquals(0.0f, ControllerAxis.LEFT_TRIGGER.normalizePull(-4.0f), 0.0001f);
        assertEquals(1.0f, ControllerAxis.LEFT_TRIGGER.normalizePull(4.0f), 0.0001f);
    }

    @Test
    void sticksArePassedThroughUntouched() {
        assertEquals(-0.8f, ControllerAxis.LEFT_X.normalizePull(-0.8f), 0.0001f);
        assertEquals(0.3f, ControllerAxis.LEFT_Y.normalizePull(0.3f), 0.0001f);
    }

    /**
     * The whole point of restingValue(): a fresh snapshot used to leave the trigger axes at 0,
     * which normalizes to 0.5 -- past the 0.45 default threshold, so attack and use read as
     * held down before the pad had been touched.
     */
    @Test
    void aFreshSnapshotDoesNotReportPulledTriggers() {
        ControllerSnapshot snapshot = ControllerSnapshot.forTest();

        for (ControllerAxis axis : new ControllerAxis[]{
            ControllerAxis.LEFT_TRIGGER, ControllerAxis.RIGHT_TRIGGER
        }) {
            assertEquals(0.0f, axis.normalizePull(snapshot.axis(axis)), 0.0001f);
        }
    }

    @Test
    void aDisconnectedSnapshotDoesNotReportPulledTriggers() {
        ControllerSnapshot snapshot = ControllerSnapshot.forTest();
        snapshot.simulateAxis(ControllerAxis.RIGHT_TRIGGER, 1.0f);

        snapshot.clearConnection();

        assertEquals(
            0.0f,
            ControllerAxis.RIGHT_TRIGGER.normalizePull(snapshot.axis(ControllerAxis.RIGHT_TRIGGER)),
            0.0001f
        );
        assertTrue(ControllerAxis.RIGHT_TRIGGER.normalizePull(
            snapshot.axis(ControllerAxis.RIGHT_TRIGGER)) < 0.45f);
    }
}
