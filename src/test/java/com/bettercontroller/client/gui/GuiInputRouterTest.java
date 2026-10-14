package com.bettercontroller.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiInputRouterTest {
    @Test
    void accelerationStillSpeedsTheDefaultRepeatUp() {
        assertTrue(GuiInputRouter.acceleratedInterval(170L) < 170L);
    }

    @Test
    void accelerationNeverGoesBelowTwoTicks() {
        assertEquals(100L, GuiInputRouter.acceleratedInterval(105L));
        assertEquals(100L, GuiInputRouter.acceleratedInterval(100L));
    }

    @Test
    void anIntervalSetBelowTwoTicksIsNotSlowedDown() {
        assertEquals(55L, GuiInputRouter.acceleratedInterval(55L));
        assertEquals(20L, GuiInputRouter.acceleratedInterval(20L));
    }
}
