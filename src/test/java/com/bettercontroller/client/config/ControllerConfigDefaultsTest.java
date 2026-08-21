package com.bettercontroller.client.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerConfigDefaultsTest {
    @Test
    void ensureDefaultsBackfillsMissingBindings() {
        ControllerConfig config = ControllerConfig.createDefault();
        config.bindings = new LinkedHashMap<>();

        config.ensureDefaults();

        assertNotNull(config.bindings);
        assertTrue(config.bindings.containsKey("jump"));
        assertTrue(config.bindings.containsKey("attack"));
        assertTrue(config.bindings.containsKey("menu_confirm"));
    }

    @Test
    void ensureDefaultsBackfillsMissingAxes() {
        ControllerConfig config = ControllerConfig.createDefault();
        config.axes = new ControllerConfig.AxisBindings();
        config.axes.move_x = null;
        config.axes.look_y = "";

        config.ensureDefaults();

        assertEquals("LEFT_X", config.axes.move_x);
        assertEquals("RIGHT_Y", config.axes.look_y);
    }

    @Test
    void resolveLayoutReturnsGlobalBindings() {
        ControllerConfig config = ControllerConfig.createDefault();
        ControllerConfig.ResolvedLayout layout = config.resolveLayout();

        assertNotNull(layout);
        assertEquals("LEFT_X", layout.axisToken("move_x"));
        assertFalse(layout.actionBindings("jump").isEmpty());
        assertTrue(layout.actionBindings("nonexistent_action").isEmpty());
    }

    @Test
    void ensureDefaultsKeepsExistingValidValues() {
        ControllerConfig config = ControllerConfig.createDefault();
        config.movementDeadzone = 0.20F;
        config.bindings.put("jump", new ArrayList<String>(java.util.Arrays.asList("X")));

        config.ensureDefaults();

        assertEquals(0.20F, config.movementDeadzone);
        assertEquals("X", config.bindings.get("jump").get(0));
    }
}
