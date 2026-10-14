package com.bettercontroller.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerConfigMigrationTest {
    @Test
    void defaultsUseTheSlowerMenuTiming() {
        ControllerConfig config = ControllerConfig.createDefault();

        assertEquals(11, config.schemaVersion);
        assertEquals(300, config.menuInitialRepeatDelayMs);
        assertEquals(170, config.menuRepeatIntervalMs);
    }

    @Test
    void oldDefaultTimingIsMovedToTheNewDefault() {
        ControllerConfig config = configWithTiming(140, 55);

        config.migrateFrom(10);

        assertEquals(300, config.menuInitialRepeatDelayMs);
        assertEquals(170, config.menuRepeatIntervalMs);
    }

    @Test
    void customTimingIsKept() {
        ControllerConfig config = configWithTiming(200, 55);

        config.migrateFrom(10);

        assertEquals(200, config.menuInitialRepeatDelayMs);
        assertEquals(55, config.menuRepeatIntervalMs);
    }

    @Test
    void currentSchemaIsLeftAlone() {
        ControllerConfig config = configWithTiming(140, 55);

        config.migrateFrom(11);

        assertEquals(140, config.menuInitialRepeatDelayMs);
        assertEquals(55, config.menuRepeatIntervalMs);
    }

    private static ControllerConfig configWithTiming(int initialDelayMs, int intervalMs) {
        ControllerConfig config = ControllerConfig.createDefault();
        config.menuInitialRepeatDelayMs = initialDelayMs;
        config.menuRepeatIntervalMs = intervalMs;
        return config;
    }
}
