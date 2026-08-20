package com.bettercontroller.client.config;

import com.bettercontroller.BetterControllerMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Locale;

public final class ControllerConfigManager {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();
    private static final String DEFAULT_CONFIG_RESOURCE = "bettercontroller.default.json";
    private static final long CHECK_INTERVAL_MS = 500L;
    /** GLFW_JOYSTICK_LAST; kept as a literal so config sanitising stays free of GLFW. */
    private static final int MAX_JOYSTICK_INDEX = 15;

    private final Path configPath;
    private ControllerConfig config;
    private FileTime lastKnownModifiedTime;
    private Long lastKnownFileSize;
    private long lastFileCheckMs;

    /**
     * @param configDirectory the game's config directory. Forge hands this over at pre-init
     *                        rather than exposing a global the way FabricLoader does.
     */
    public ControllerConfigManager(Path configDirectory) {
        this.configPath = configDirectory.resolve(BetterControllerMod.CONFIG_FILE_NAME);
    }

    public Path configPath() {
        return configPath;
    }

    public ControllerConfig getConfig() {
        if (config == null) {
            return load();
        }

        long now = System.currentTimeMillis();
        if (now - lastFileCheckMs < CHECK_INTERVAL_MS) {
            return config;
        }
        lastFileCheckMs = now;

        try {
            if (!Files.exists(configPath)) {
                BetterControllerMod.logger().info("Controller config file missing. Recreating defaults.");
                return load();
            }

            FileTime currentModifiedTime = Files.getLastModifiedTime(configPath);
            long currentFileSize = Files.size(configPath);
            boolean modifiedChanged = lastKnownModifiedTime == null
                || currentModifiedTime.compareTo(lastKnownModifiedTime) != 0;
            boolean sizeChanged = lastKnownFileSize == null
                || currentFileSize != lastKnownFileSize;
            if (modifiedChanged || sizeChanged) {
                BetterControllerMod.logger().info("Reloading controller config from disk.");
                return load();
            }
        } catch (IOException exception) {
            BetterControllerMod.logger().warn("Failed to check config timestamp: {}", exception.getMessage());
        }

        return config;
    }

    public ControllerConfig load() {
        ensureConfigFileExists();

        ControllerConfig loaded = readConfigFromDisk();
        int loadedSchemaVersion = loaded.schemaVersion;
        loaded.ensureDefaults();
        sanitize(loaded);

        int targetSchema = ControllerConfig.createDefault().schemaVersion;
        if (loadedSchemaVersion < targetSchema) {
            loaded.schemaVersion = targetSchema;
            writeConfig(loaded);
            BetterControllerMod.logger().info("Migrated controller config to schema v{}.", loaded.schemaVersion);
        }

        this.config = loaded;
        this.lastKnownModifiedTime = readLastModified();
        this.lastKnownFileSize = readFileSize();
        return loaded;
    }

    public ControllerConfig save(ControllerConfig updatedConfig) {
        if (updatedConfig == null) {
            return getConfig();
        }

        updatedConfig.ensureDefaults();
        sanitize(updatedConfig);
        updatedConfig.schemaVersion = ControllerConfig.createDefault().schemaVersion;
        writeConfig(updatedConfig);

        this.config = updatedConfig;
        this.lastKnownModifiedTime = readLastModified();
        this.lastKnownFileSize = readFileSize();
        return updatedConfig;
    }

    private void ensureConfigFileExists() {
        if (Files.exists(configPath)) {
            return;
        }

        try {
            Files.createDirectories(configPath.getParent());
            try (InputStream resourceStream =
                     ControllerConfigManager.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
                if (resourceStream != null) {
                    Files.copy(resourceStream, configPath);
                } else {
                    writeConfig(ControllerConfig.createDefault());
                }
            }
            BetterControllerMod.logger().info("Created controller config at {}", configPath);
        } catch (IOException exception) {
            BetterControllerMod.logger().error("Could not create controller config file: {}", exception.getMessage());
        }
    }

    private ControllerConfig readConfigFromDisk() {
        if (!Files.exists(configPath)) {
            return ControllerConfig.createDefault();
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            JsonReader jsonReader = new JsonReader(reader);
            // Gson 2.8 is what 1.12.2 ships; setStrictness/Strictness only arrived in 2.11.
            jsonReader.setLenient(true);

            ControllerConfig loaded = GSON.fromJson(jsonReader, ControllerConfig.class);
            if (loaded == null) {
                BetterControllerMod.logger().warn("Controller config was empty. Falling back to defaults.");
                return ControllerConfig.createDefault();
            }
            return loaded;
        } catch (Exception exception) {
            BetterControllerMod.logger().error("Invalid controller config. Falling back to defaults. {}", exception.getMessage());
            return ControllerConfig.createDefault();
        }
    }

    private void writeConfig(ControllerConfig toWrite) {
        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            GSON.toJson(toWrite, writer);
        } catch (IOException exception) {
            BetterControllerMod.logger().error("Could not write controller config: {}", exception.getMessage());
        }
    }

    private FileTime readLastModified() {
        try {
            if (Files.exists(configPath)) {
                return Files.getLastModifiedTime(configPath);
            }
        } catch (IOException exception) {
            BetterControllerMod.logger().warn("Failed reading controller config timestamp: {}", exception.getMessage());
        }
        return null;
    }

    private Long readFileSize() {
        try {
            if (Files.exists(configPath)) {
                return Files.size(configPath);
            }
        } catch (IOException exception) {
            BetterControllerMod.logger().warn("Failed reading controller config size: {}", exception.getMessage());
        }
        return null;
    }

    private static void sanitize(ControllerConfig config) {
        config.movementDeadzone = clamp(config.movementDeadzone, 0.0F, 0.95F, 0.14F);
        config.lookDeadzone = clamp(config.lookDeadzone, 0.0F, 0.95F, 0.07F);
        config.lookAntiDeadzone = clamp(config.lookAntiDeadzone, 0.0F, 0.25F, 0.02F);
        config.lookSensitivityX = clamp(config.lookSensitivityX, 0.1F, 80.0F, 12.0F);
        config.lookSensitivityY = clamp(config.lookSensitivityY, 0.1F, 80.0F, 11.0F);
        config.lookSpeedMultiplier = clamp(config.lookSpeedMultiplier, 0.5F, 4.0F, 2.25F);
        config.triggerThreshold = clamp(config.triggerThreshold, 0.01F, 1.0F, 0.45F);
        config.menuAxisThreshold = clamp(config.menuAxisThreshold, 0.2F, 0.95F, 0.35F);
        config.menuAxisPressThreshold = clamp(
            config.menuAxisPressThreshold,
            0.2F, 0.95F,
            clamp(config.menuAxisThreshold, 0.2F, 0.95F, 0.40F)
        );
        config.menuAxisReleaseThreshold = clamp(config.menuAxisReleaseThreshold, 0.05F, 0.90F, 0.20F);
        if (config.menuAxisReleaseThreshold >= config.menuAxisPressThreshold) {
            config.menuAxisReleaseThreshold = Math.max(0.05F, config.menuAxisPressThreshold - 0.10F);
        }
        config.menuInitialRepeatDelayMs = clampInt(config.menuInitialRepeatDelayMs, 60, 400, 140);
        config.menuRepeatIntervalMs = clampInt(config.menuRepeatIntervalMs, 20, 200, 55);
        config.cameraSmoothingStrength = clamp(config.cameraSmoothingStrength, 0.0F, 1.0F, 0.35F);
        config.lookResponseCurve = normalizeResponseCurve(config.lookResponseCurve);
        config.preferredControllerGuid = normalizeGuid(config.preferredControllerGuid);
        config.preferredJoystickIndex = clampInt(config.preferredJoystickIndex, -1, MAX_JOYSTICK_INDEX, -1);
    }

    private static String normalizeGuid(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private static String normalizeResponseCurve(String value) {
        if (value == null) {
            return "linear";
        }
        String curve = value.toLowerCase(Locale.ROOT);
        if ("linear".equals(curve) || "exponential_light".equals(curve) || "exponential_strong".equals(curve)) {
            return curve;
        }
        return "linear";
    }

    private static float clamp(float value, float min, float max, float fallback) {
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }
}
