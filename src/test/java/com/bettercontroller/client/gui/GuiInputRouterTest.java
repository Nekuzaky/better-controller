package com.bettercontroller.client.gui;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerButton;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.translation.GameplayAction;
import com.bettercontroller.client.translation.InputTranslator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiInputRouterTest {
    private GuiInputRouter router;
    private InputTranslator translator;
    private ControllerSnapshot snapshot;
    private ControllerConfig config;

    @BeforeEach
    void setUp() {
        router = new GuiInputRouter();
        translator = new InputTranslator();
        snapshot = ControllerSnapshot.forTest();
        config = ControllerConfig.createDefault();
    }

    /** One routed screen tick with whatever is currently pressed on the snapshot. */
    private GuiInputFrame route() {
        return router.route(snapshot, config, config.resolveLayout(), translator, false);
    }

    private void bind(String action, String... tokens) {
        config.bindings.put(action, List.of(tokens));
    }

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

    @Test
    void theDefaultBindingConfirms() {
        snapshot.simulateButton(ControllerButton.SOUTH, true);

        assertTrue(route().confirm());
    }

    @Test
    void rebindingConfirmStopsTheOldButtonConfirming() {
        bind("menu_confirm", "X");
        snapshot.simulateButton(ControllerButton.SOUTH, true);

        assertFalse(route().confirm(), "A still confirmed after menu_confirm was rebound to X");
    }

    @Test
    void rebindingConfirmMakesTheNewButtonConfirm() {
        bind("menu_confirm", "X");
        snapshot.simulateButton(ControllerButton.WEST, true);

        assertTrue(route().confirm());
    }

    /**
     * Deliberately kept: an action with nothing readable bound to it still answers to the
     * built-in default. Clearing every menu binding would otherwise strand the player in a
     * screen with no way to confirm or back out short of editing JSON by hand.
     */
    @Test
    void clearingABindingLeavesTheBuiltInDefaultAsASafetyNet() {
        bind("menu_back");
        snapshot.simulateButton(ControllerButton.EAST, true);

        assertTrue(route().back());
    }

    /** A typo leaves the action effectively unbound, so the same safety net applies. */
    @Test
    void anUnreadableBindingFallsBackToTheBuiltInDefault() {
        bind("menu_confirm", "RIGHT_BUMBER");
        snapshot.simulateButton(ControllerButton.SOUTH, true);

        assertTrue(route().confirm());
    }

    @Test
    void anEmptyLayoutStillNavigates() {
        config.bindings.clear();
        snapshot.simulateButton(ControllerButton.DPAD_DOWN, true);

        assertTrue(route().down());
    }

    /**
     * RB is bound to menu_tab_next and RT to menu_page_next. Before the fallback was gated,
     * RB satisfied the page action too, so one press moved both tab and page in the creative
     * inventory.
     */
    @Test
    void theTabBumperDoesNotAlsoTurnThePage() {
        snapshot.simulateButton(ControllerButton.RIGHT_BUMPER, true);

        GuiInputFrame frame = route();

        assertTrue(frame.tabNext(), "RB should still change tab");
        assertFalse(frame.pageNext(), "RB also turned the page; menu_page_next is bound to RT");
    }

    @Test
    void theBoundTriggerStillTurnsThePage() {
        bind("menu_page_next", "RB");
        snapshot.simulateButton(ControllerButton.RIGHT_BUMPER, true);

        assertTrue(route().pageNext());
    }

    @Test
    void hasRecognizedBindingReadsThroughAnInversionPrefix() {
        bind("menu_up", "-LEFT_Y");

        assertTrue(GuiInputRouter.hasRecognizedBinding(
            config.resolveLayout(),
            com.bettercontroller.client.translation.GameplayAction.MENU_UP
        ));
    }

    @Test
    void hasRecognizedBindingRejectsBlankAndUnknownTokens() {
        bind("menu_confirm", "", "  ", "NOT_A_BUTTON");

        assertFalse(GuiInputRouter.hasRecognizedBinding(
            config.resolveLayout(),
            com.bettercontroller.client.translation.GameplayAction.MENU_CONFIRM
        ));
    }

    // --- repeat timing -------------------------------------------------------
    // pulse() takes its clock reading as an argument, so a hold can be played out
    // instantly instead of sleeping through 300ms of initial delay.

    private boolean held(long atMs) {
        return router.pulse(GameplayAction.MENU_DOWN, true, true, 300L, 170L, atMs);
    }

    @Test
    void theFirstPressFiresImmediately() {
        assertTrue(held(1_000L));
    }

    @Test
    void aHoldIsQuietUntilTheInitialDelayHasPassed() {
        held(1_000L);

        assertFalse(held(1_200L), "repeated before the 300ms initial delay");
        assertFalse(held(1_299L));
    }

    @Test
    void aHoldRepeatsOnceTheInitialDelayHasPassed() {
        held(1_000L);

        assertTrue(held(1_300L));
    }

    @Test
    void repeatsAreSpacedByTheInterval() {
        held(1_000L);
        held(1_300L);

        assertFalse(held(1_400L), "repeated before the 170ms interval elapsed");
        assertTrue(held(1_470L));
    }

    @Test
    void releasingClearsTheHold() {
        held(1_000L);
        held(1_300L);

        router.pulse(GameplayAction.MENU_DOWN, false, true, 300L, 170L, 1_400L);

        assertTrue(held(1_500L), "a fresh press should fire immediately again");
    }

    @Test
    void aNonRepeatableActionNeverRepeats() {
        assertTrue(router.pulse(GameplayAction.MENU_BACK, true, false, 300L, 170L, 1_000L));
        assertFalse(router.pulse(GameplayAction.MENU_BACK, true, false, 300L, 170L, 9_000L));
    }
}
