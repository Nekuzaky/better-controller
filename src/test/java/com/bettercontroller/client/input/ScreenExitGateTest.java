package com.bettercontroller.client.input;

import com.bettercontroller.client.translation.GameplayInputFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenExitGateTest {
    @Test
    void firstFrameBackDropsTapsAndHeldButtons() {
        ScreenExitGate gate = new ScreenExitGate();
        GameplayInputFrame frame = frame(true, true);

        gate.arm();
        gate.filter(frame);

        assertFalse(frame.jumpHeld());
        assertFalse(frame.dropTap());
        assertEquals(-1, frame.hotbarSelect());
    }

    @Test
    void heldButtonStaysBlockedUntilReleased() {
        ScreenExitGate gate = new ScreenExitGate();
        gate.arm();
        gate.filter(frame(true, false));

        GameplayInputFrame stillHeld = frame(true, false);
        gate.filter(stillHeld);
        assertFalse(stillHeld.jumpHeld());

        gate.filter(frame(false, false));

        GameplayInputFrame pressedAgain = frame(true, false);
        gate.filter(pressedAgain);
        assertTrue(pressedAgain.jumpHeld());
    }

    @Test
    void buttonPressedAfterTheScreenIsUntouched() {
        ScreenExitGate gate = new ScreenExitGate();
        gate.arm();
        gate.filter(frame(false, false));

        GameplayInputFrame frame = frame(true, true);
        gate.filter(frame);

        assertTrue(frame.jumpHeld());
        assertTrue(frame.dropTap());
    }

    @Test
    void unarmedGatePassesEverythingThrough() {
        ScreenExitGate gate = new ScreenExitGate();
        GameplayInputFrame frame = frame(true, true);

        gate.filter(frame);

        assertTrue(frame.jumpHeld());
        assertTrue(frame.dropTap());
        assertEquals(2, frame.hotbarSelect());
    }

    private static GameplayInputFrame frame(boolean jump, boolean drop) {
        GameplayInputFrame frame = new GameplayInputFrame();
        frame.clear();
        frame.setHolds(jump, false, false, false, false, false);
        frame.setTaps(false, false, drop, false, false, false, false);
        frame.setHotbar(0, drop ? 2 : -1);
        return frame;
    }
}
