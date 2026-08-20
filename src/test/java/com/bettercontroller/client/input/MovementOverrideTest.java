package com.bettercontroller.client.input;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The analog vector is applied after {@code KeyboardInput.tick}, which is the only point where it
 * survives long enough to be read. That timing makes the "is the stick actually being used" question
 * load-bearing: writing unconditionally would overwrite keyboard movement with a centred stick's
 * zero, so a player with a controller plugged in could not walk with the keyboard at all.
 */
class MovementOverrideTest {
    @Test
    void anIdleStickLeavesVanillaMovementAlone() {
        assertFalse(MinecraftInputApplier.shouldOverrideMovement(0.0f, 0.0f));
    }

    @Test
    void anyDeflectionTakesOver() {
        assertTrue(MinecraftInputApplier.shouldOverrideMovement(0.4f, 0.0f));
        assertTrue(MinecraftInputApplier.shouldOverrideMovement(0.0f, -0.4f));
        assertTrue(MinecraftInputApplier.shouldOverrideMovement(-0.3f, 0.3f));
    }

    @Test
    void evenTheSmallestNonZeroValueCounts() {
        // The translator zeroes everything inside the deadzone, so whatever reaches here is real.
        assertTrue(MinecraftInputApplier.shouldOverrideMovement(Float.MIN_VALUE, 0.0f));
    }
}
