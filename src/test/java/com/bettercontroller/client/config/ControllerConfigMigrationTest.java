package com.bettercontroller.client.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ControllerConfigMigrationTest {
    @Test
    void defaultsUseTheSlowerMenuTiming() {
        ControllerConfig config = ControllerConfig.createDefault();

        assertEquals(11, config.schemaVersion);
        assertEquals(300, config.menuInitialRepeatDelayMs);
        assertEquals(170, config.menuRepeatIntervalMs);
    }

    @Test
    void bundledDefaultFileAgreesWithTheCode() throws IOException {
        // The bundled file is what a first run copies out, so a default changed only in the code
        // reaches nobody.
        ControllerConfig code = ControllerConfig.createDefault();
        ControllerConfig bundled = readBundledDefault();

        assertEquals(code.schemaVersion, bundled.schemaVersion);
        assertEquals(code.menuInitialRepeatDelayMs, bundled.menuInitialRepeatDelayMs);
        assertEquals(code.menuRepeatIntervalMs, bundled.menuRepeatIntervalMs);
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

    private static ControllerConfig readBundledDefault() throws IOException {
        try (InputStream stream = ControllerConfig.class.getClassLoader().getResourceAsStream("bettercontroller.default.json")) {
            assertNotNull(stream, "bettercontroller.default.json is missing from the classpath");
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return new Gson().fromJson(reader, ControllerConfig.class);
            }
        }
    }

    private static ControllerConfig configWithTiming(int initialDelayMs, int intervalMs) {
        ControllerConfig config = ControllerConfig.createDefault();
        config.menuInitialRepeatDelayMs = initialDelayMs;
        config.menuRepeatIntervalMs = intervalMs;
        return config;
    }
}
