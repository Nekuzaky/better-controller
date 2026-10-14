package com.bettercontroller.client.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerConfigTokenCheckTest {
    @Test
    void theShippedDefaultsAreAllReadable() {
        assertEquals(List.of(), ControllerConfig.createDefault().unreadableTokens());
    }

    @Test
    void aMisspelledButtonIsReported() {
        ControllerConfig config = ControllerConfig.createDefault();
        config.bindings.put("menu_confirm", new ArrayList<>(List.of("RIGHT_BUMBER")));

        assertEquals(List.of("menu_confirm: RIGHT_BUMBER"), config.unreadableTokens());
    }

    @Test
    void aReadableTokenAlongsideABadOneOnlyReportsTheBadOne() {
        ControllerConfig config = ControllerConfig.createDefault();
        config.bindings.put("jump", new ArrayList<>(List.of("A", "NOPE")));

        assertEquals(List.of("jump: NOPE"), config.unreadableTokens());
    }

    @Test
    void anInvertedAxisIsReadThroughItsPrefix() {
        ControllerConfig config = ControllerConfig.createDefault();
        config.bindings.put("menu_up", new ArrayList<>(List.of("-LEFT_Y")));

        assertEquals(List.of(), config.unreadableTokens());
    }

    @Test
    void anInvertedNonsenseAxisIsStillReported() {
        ControllerConfig config = ControllerConfig.createDefault();
        config.bindings.put("menu_up", new ArrayList<>(List.of("-LEFT_Z")));

        assertEquals(List.of("menu_up: -LEFT_Z"), config.unreadableTokens());
    }

    @Test
    void aBadMovementAxisIsReported() {
        ControllerConfig config = ControllerConfig.createDefault();
        config.axes.move_x = "LEFT_Z";

        assertEquals(List.of("axes.move_x: LEFT_Z"), config.unreadableTokens());
    }

    @Test
    void blankAndClearedBindingsAreNotComplainedAbout() {
        ControllerConfig config = ControllerConfig.createDefault();
        config.bindings.put("hotbar_1", new ArrayList<>(List.of("", "   ")));
        config.bindings.put("hotbar_2", new ArrayList<>());

        assertEquals(List.of(), config.unreadableTokens());
    }

    @Test
    void everyProblemIsListedNotJustTheFirst() {
        ControllerConfig config = ControllerConfig.createDefault();
        config.bindings.put("jump", new ArrayList<>(List.of("NOPE")));
        config.bindings.put("sneak", new ArrayList<>(List.of("ALSO_NOPE")));

        List<String> problems = config.unreadableTokens();

        assertEquals(2, problems.size());
        assertTrue(problems.contains("jump: NOPE"));
        assertTrue(problems.contains("sneak: ALSO_NOPE"));
    }
}
