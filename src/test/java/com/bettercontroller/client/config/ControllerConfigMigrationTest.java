package com.bettercontroller.client.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ControllerConfigMigrationTest {
    @Test
    void defaultsUseTheSlowerMenuTiming() {
        ControllerConfig config = ControllerConfig.createDefault();

        assertEquals(12, config.schemaVersion);
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
    void timingIsLeftAloneOnceTheFileIsAtTheTimingSchema() {
        ControllerConfig config = configWithTiming(140, 55);

        config.migrateFrom(11);

        assertEquals(140, config.menuInitialRepeatDelayMs);
        assertEquals(55, config.menuRepeatIntervalMs);
    }

    @Test
    void bundledDefaultAgreesOnPickBlock() throws IOException {
        assertEquals(
            ControllerConfig.createDefault().bindings.get("pick_block"),
            readBundledDefault().bindings.get("pick_block")
        );
    }

    @Test
    void pickBlockNoLongerSharesAButtonWithTheHotbar() {
        ControllerConfig config = ControllerConfig.createDefault();

        assertNotEquals(
            config.bindings.get("pick_block"),
            config.bindings.get("hotbar_next"),
            "pick_block and hotbar_next both fire on one press when they share a button"
        );
    }

    @Test
    void anUntouchedPickBlockIsMovedOffTheHotbarBumper() {
        ControllerConfig config = configWithPickBlock("RB");

        config.migrateFrom(11);

        assertEquals(List.of("DPAD_LEFT"), config.bindings.get("pick_block"));
    }

    @Test
    void aPickBlockTheUserChoseIsKept() {
        ControllerConfig config = configWithPickBlock("NORTH");

        config.migrateFrom(11);

        assertEquals(List.of("NORTH"), config.bindings.get("pick_block"));
    }

    @Test
    void aPickBlockWithExtraButtonsIsKept() {
        ControllerConfig config = ControllerConfig.createDefault();
        config.bindings.put("pick_block", new ArrayList<>(List.of("RB", "NORTH")));

        config.migrateFrom(11);

        assertEquals(List.of("RB", "NORTH"), config.bindings.get("pick_block"));
    }

    @Test
    void pickBlockIsLeftAloneOnceTheFileIsAtTheCurrentSchema() {
        ControllerConfig config = configWithPickBlock("RB");

        config.migrateFrom(12);

        assertEquals(List.of("RB"), config.bindings.get("pick_block"));
    }

    private static ControllerConfig configWithPickBlock(String token) {
        ControllerConfig config = ControllerConfig.createDefault();
        config.bindings.put("pick_block", new ArrayList<>(List.of(token)));
        return config;
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
