package com.bettercontroller.client.polling;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller selection drives couch co-op: two game instances on one machine must each pin their
 * own pad. The decision is tested here without GLFW, through the device lookups it takes.
 */
class ControllerSelectionTest {
    private static final String XBOX_GUID = "78696e70757401000000000000000000";
    private static final String DUALSENSE_GUID = "030000004c050000e60c000000000000";

    /** Slots 0 and 2 hold identical Xbox pads, slot 1 a DualSense; slot 3 is empty. */
    private static final Map<Integer, String> DEVICES = Map.of(
        0, XBOX_GUID,
        1, DUALSENSE_GUID,
        2, XBOX_GUID
    );

    private static final IntPredicate USABLE = DEVICES::containsKey;
    private static final IntFunction<String> GUID_OF = DEVICES::get;

    private static int resolve(String guid, int index) {
        return ControllerPoller.resolvePreferred(guid, index, USABLE, GUID_OF);
    }

    @Test
    void noPreferenceIsReportedAsSuch() {
        assertFalse(ControllerPoller.hasPreference("", -1));
        assertFalse(ControllerPoller.hasPreference(null, -1));
        assertTrue(ControllerPoller.hasPreference(XBOX_GUID, -1));
        assertTrue(ControllerPoller.hasPreference("", 1));
    }

    @Test
    void guidSelectsTheMatchingPad() {
        assertEquals(1, resolve(DUALSENSE_GUID, -1));
    }

    @Test
    void guidMatchIsCaseInsensitiveAndTrimmed() {
        assertEquals(1, resolve("  " + DUALSENSE_GUID.toUpperCase(java.util.Locale.ROOT) + "  ", -1));
    }

    @Test
    void indexSelectsTheExactSlot() {
        assertEquals(2, resolve("", 2));
    }

    @Test
    void indexTellsIdenticalPadsApart() {
        assertEquals(0, resolve(XBOX_GUID, 0));
        assertEquals(2, resolve(XBOX_GUID, 2));
    }

    @Test
    void guidWinsWhenTheIndexContradictsIt() {
        assertEquals(1, resolve(DUALSENSE_GUID, 2), "slot 2 is an Xbox pad, so fall back to the GUID scan");
    }

    @Test
    void unknownGuidResolvesToNothingRatherThanAnotherPad() {
        assertEquals(-1, resolve("ffffffffffffffffffffffffffffffff", -1));
    }

    @Test
    void absentIndexResolvesToNothingRatherThanAnotherPad() {
        assertEquals(-1, resolve("", 3));
    }

    @Test
    void indexOutOfRangeIsIgnored() {
        assertEquals(-1, resolve("", 99));
        assertEquals(-1, resolve("", -5));
    }

    @Test
    void guidStillResolvesWhenTheIndexIsOutOfRange() {
        assertEquals(1, resolve(DUALSENSE_GUID, 99));
    }
}
