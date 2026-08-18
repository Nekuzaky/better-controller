package com.bettercontroller.client.gui;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.input.ControllerRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class BetterControllerSettingsScreen extends Screen {
    private static final int SLIDER_WIDTH = 286;
    private static final int CONTROL_HEIGHT = 20;
    private static final int FIRST_SLIDER_Y = 60;
    private static final int SLIDER_GAP = 26;

    private final Screen parent;
    private final ControllerRuntime runtime;

    private float movementDeadzone;
    private float lookSensitivityX;
    private float lookSensitivityY;
    private float lookSpeedMultiplier;
    private float triggerThreshold;

    public BetterControllerSettingsScreen(Screen parent, ControllerRuntime runtime) {
        super(Component.translatable("bettercontroller.screen.settings.title"));
        this.parent = parent;
        this.runtime = runtime;
        loadFromConfig();
    }

    private void loadFromConfig() {
        ControllerConfig config = runtime.latestConfig();
        if (config == null) {
            config = ControllerConfig.createDefault();
        }
        movementDeadzone = config.movementDeadzone;
        lookSensitivityX = config.lookSensitivityX;
        lookSensitivityY = config.lookSensitivityY;
        lookSpeedMultiplier = config.lookSpeedMultiplier;
        triggerThreshold = config.triggerThreshold;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int sliderX = centerX - (SLIDER_WIDTH / 2);
        int y = FIRST_SLIDER_Y;

        addDrawableChild(createSlider(
            sliderX, y, "Movement Deadzone", 0.0F, 0.40F, "%.2f",
            () -> movementDeadzone, value -> movementDeadzone = value
        ));
        y += SLIDER_GAP;

        addDrawableChild(createSlider(
            sliderX, y, "Look Sensitivity X", 0.5F, 30.0F, "%.1f",
            () -> lookSensitivityX, value -> lookSensitivityX = value
        ));
        y += SLIDER_GAP;

        addDrawableChild(createSlider(
            sliderX, y, "Look Sensitivity Y", 0.5F, 30.0F, "%.1f",
            () -> lookSensitivityY, value -> lookSensitivityY = value
        ));
        y += SLIDER_GAP;

        addDrawableChild(createSlider(
            sliderX, y, "Look Speed Multiplier", 0.5F, 4.0F, "%.2f",
            () -> lookSpeedMultiplier, value -> lookSpeedMultiplier = value
        ));
        y += SLIDER_GAP;

        addDrawableChild(createSlider(
            sliderX, y, "Trigger Threshold", 0.05F, 1.0F, "%.2f",
            () -> triggerThreshold, value -> triggerThreshold = value
        ));
        y += SLIDER_GAP + 6;

        addDrawableChild(Button.builder(
            Component.literal("Reset to defaults"),
            b -> resetToDefaults()
        ).bounds(sliderX, y, (SLIDER_WIDTH / 2) - 4, CONTROL_HEIGHT).build());

        addDrawableChild(Button.builder(
            Component.literal("Open config file"),
            b -> openConfigFile()
        ).bounds(sliderX + (SLIDER_WIDTH / 2) + 4, y, (SLIDER_WIDTH / 2) - 4, CONTROL_HEIGHT).build());

        addDrawableChild(Button.builder(
            Component.literal("Done"),
            b -> saveAndClose()
        ).bounds(sliderX, y + SLIDER_GAP, SLIDER_WIDTH, CONTROL_HEIGHT).build());
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.font, this.title, this.width / 2, 18, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(
            this.font,
            Component.literal("Other options can be edited in bettercontroller.json"),
            this.width / 2,
            36,
            0x8FA0B5C5
        );
    }

    @Override
    public void close() {
        saveAndClose();
    }

    private void saveAndClose() {
        runtime.updateConfig(this::applyToConfig);
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    private void applyToConfig(ControllerConfig config) {
        config.movementDeadzone = movementDeadzone;
        config.lookSensitivityX = lookSensitivityX;
        config.lookSensitivityY = lookSensitivityY;
        config.lookSpeedMultiplier = lookSpeedMultiplier;
        config.triggerThreshold = triggerThreshold;
    }

    private void resetToDefaults() {
        runtime.resetToDefaults();
        loadFromConfig();
        this.clearAndInit();
    }

    private void openConfigFile() {
        Util.getOperatingSystem().open(runtime.configPath().toFile());
    }

    private static AbstractSliderButton createSlider(
        int x,
        int y,
        String label,
        float min,
        float max,
        String format,
        Supplier<Float> getter,
        Consumer<Float> setter
    ) {
        double normalized = normalize(getter.get(), min, max);
        return new ConfigSlider(x, y, normalized, label, min, max, format, setter);
    }

    private static double normalize(float value, float min, float max) {
        if (max <= min) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, (value - min) / (double) (max - min)));
    }

    private static final class ConfigSlider extends AbstractSliderButton {
        private final String label;
        private final float min;
        private final float max;
        private final String format;
        private final Consumer<Float> setter;

        ConfigSlider(int x, int y, double initialValue, String label, float min, float max, String format, Consumer<Float> setter) {
            super(x, y, SLIDER_WIDTH, CONTROL_HEIGHT, Component.empty(), initialValue);
            this.label = label;
            this.min = min;
            this.max = max;
            this.format = format;
            this.setter = setter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            float value = currentValue();
            setMessage(Component.literal(label + ": " + String.format(Locale.ROOT, format, value)));
        }

        @Override
        protected void applyValue() {
            float value = currentValue();
            setter.accept(value);
        }

        private float currentValue() {
            return min + ((float) value) * (max - min);
        }
    }
}
