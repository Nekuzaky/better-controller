package com.bettercontroller.client.gui;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerAxis;
import com.bettercontroller.client.polling.ControllerButton;
import com.bettercontroller.client.polling.ControllerSnapshot;
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
        releaseTriggers();
    }

    /**
     * GLFW reports a released trigger as -1, not 0, and forTest() leaves every axis at 0 --
     * which reads as a half-pulled trigger once it is normalized into 0..1. Until the snapshot
     * models the resting value itself, tests that care about the triggers have to say so.
     */
    private void releaseTriggers() {
        snapshot.simulateAxis(ControllerAxis.LEFT_TRIGGER, -1.0f);
        snapshot.simulateAxis(ControllerAxis.RIGHT_TRIGGER, -1.0f);
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
}
