package com.bettercontroller.client.gui;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerAxis;
import com.bettercontroller.client.polling.ControllerButton;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.translation.GameplayAction;
import com.bettercontroller.client.translation.InputTranslator;

import java.util.EnumMap;
import java.util.List;

public final class GuiInputRouter {
    private final EnumMap<GameplayAction, Boolean> previousStates = new EnumMap<>(GameplayAction.class);
    private final EnumMap<GameplayAction, Long> holdStartMs = new EnumMap<>(GameplayAction.class);
    private final EnumMap<GameplayAction, Long> lastRepeatMs = new EnumMap<>(GameplayAction.class);
    private final EnumMap<GameplayAction, Boolean> axisLatchStates = new EnumMap<>(GameplayAction.class);

    public GuiInputFrame route(
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout,
        InputTranslator translator,
        boolean allowConfirmRepeat
    ) {
        long initialDelayMs = clampLong(config.menuInitialRepeatDelayMs, 60L, 400L, 140L);
        long repeatIntervalMs = clampLong(config.menuRepeatIntervalMs, 20L, 200L, 55L);
        if (allowConfirmRepeat) {
            initialDelayMs = Math.min(initialDelayMs, 150L);
            repeatIntervalMs = Math.min(repeatIntervalMs, 40L);
        }

        boolean upPressed = directionalPressedWithHysteresis(snapshot, config, layout, GameplayAction.MENU_UP);
        boolean downPressed = directionalPressedWithHysteresis(snapshot, config, layout, GameplayAction.MENU_DOWN);
        boolean leftPressed = directionalPressedWithHysteresis(snapshot, config, layout, GameplayAction.MENU_LEFT);
        boolean rightPressed = directionalPressedWithHysteresis(snapshot, config, layout, GameplayAction.MENU_RIGHT);
        boolean confirmPressed = actionPressedWithFallback(snapshot, config, layout, translator, GameplayAction.MENU_CONFIRM);
        boolean backPressed = actionPressedWithFallback(snapshot, config, layout, translator, GameplayAction.MENU_BACK);
        boolean pageNextPressed = actionPressedWithFallback(snapshot, config, layout, translator, GameplayAction.MENU_PAGE_NEXT);
        boolean pagePrevPressed = actionPressedWithFallback(snapshot, config, layout, translator, GameplayAction.MENU_PAGE_PREV);
        boolean tabNextPressed = actionPressedWithFallback(snapshot, config, layout, translator, GameplayAction.MENU_TAB_NEXT);
        boolean tabPrevPressed = actionPressedWithFallback(snapshot, config, layout, translator, GameplayAction.MENU_TAB_PREV);

        boolean upPulse = pulse(GameplayAction.MENU_UP, upPressed, true, initialDelayMs, repeatIntervalMs);
        boolean downPulse = pulse(GameplayAction.MENU_DOWN, downPressed, true, initialDelayMs, repeatIntervalMs);
        boolean leftPulse = pulse(GameplayAction.MENU_LEFT, leftPressed, true, initialDelayMs, repeatIntervalMs);
        boolean rightPulse = pulse(GameplayAction.MENU_RIGHT, rightPressed, true, initialDelayMs, repeatIntervalMs);
        boolean confirmPulse = pulse(GameplayAction.MENU_CONFIRM, confirmPressed, allowConfirmRepeat, initialDelayMs, repeatIntervalMs);
        boolean backPulse = pulse(GameplayAction.MENU_BACK, backPressed, false, initialDelayMs, repeatIntervalMs);
        boolean pageNextPulse = pulse(GameplayAction.MENU_PAGE_NEXT, pageNextPressed, false, initialDelayMs, repeatIntervalMs);
        boolean pagePrevPulse = pulse(GameplayAction.MENU_PAGE_PREV, pagePrevPressed, false, initialDelayMs, repeatIntervalMs);
        boolean tabNextPulse = pulse(GameplayAction.MENU_TAB_NEXT, tabNextPressed, false, initialDelayMs, repeatIntervalMs);
        boolean tabPrevPulse = pulse(GameplayAction.MENU_TAB_PREV, tabPrevPressed, false, initialDelayMs, repeatIntervalMs);

        return new GuiInputFrame(
            upPulse,
            downPulse,
            leftPulse,
            rightPulse,
            confirmPulse,
            backPulse,
            pageNextPulse,
            pagePrevPulse,
            tabNextPulse,
            tabPrevPulse
        );
    }

    public void reset() {
        previousStates.clear();
        holdStartMs.clear();
        lastRepeatMs.clear();
        axisLatchStates.clear();
    }

    private boolean pulse(
        GameplayAction action,
        boolean pressed,
        boolean repeatable,
        long initialDelayMs,
        long repeatIntervalMs
    ) {
        long now = System.currentTimeMillis();
        boolean previous = Boolean.TRUE.equals(previousStates.get(action));

        if (!pressed) {
            previousStates.put(action, false);
            holdStartMs.remove(action);
            lastRepeatMs.remove(action);
            return false;
        }

        if (!previous) {
            previousStates.put(action, true);
            holdStartMs.put(action, now);
            lastRepeatMs.put(action, now);
            return true;
        }

        previousStates.put(action, true);
        if (!repeatable) {
            return false;
        }

        long heldSince = holdStartMs.getOrDefault(action, now);
        long lastRepeat = lastRepeatMs.getOrDefault(action, now);
        long acceleratedInterval = Math.max(16L, repeatIntervalMs - 12L);
        long effectiveInterval = (now - heldSince) >= 900L ? acceleratedInterval : repeatIntervalMs;
        if ((now - heldSince) >= initialDelayMs && (now - lastRepeat) >= effectiveInterval) {
            lastRepeatMs.put(action, now);
            return true;
        }
        return false;
    }

    private static long clampLong(long value, long min, long max, long fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }

    private boolean directionalPressedWithHysteresis(
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout,
        GameplayAction action
    ) {
        if (action == null) {
            return false;
        }
        if (snapshot == null || config == null || layout == null) {
            axisLatchStates.put(action, false);
            return false;
        }

        float pressThreshold = clampFloat(
            config.menuAxisPressThreshold,
            0.2F,
            0.95F,
            clampFloat(config.menuAxisThreshold, 0.2F, 0.95F, 0.40F)
        );
        float releaseThreshold = clampFloat(config.menuAxisReleaseThreshold, 0.05F, 0.90F, 0.20F);
        if (releaseThreshold >= pressThreshold) {
            releaseThreshold = Math.max(0.05F, pressThreshold - 0.10F);
        }

        DirectionalReadState readState = readDirectionalBindings(snapshot, layout.actionBindings(action.configKey()));
        if (!readState.hasRecognizedBinding()) {
            readState = fallbackDirectionalReadState(snapshot, action);
        }

        boolean latchedAxisPressed = updateAxisLatch(action, readState.axisMagnitude(), readState.hasAxis(), pressThreshold, releaseThreshold);
        return readState.digitalPressed() || latchedAxisPressed;
    }

    private boolean updateAxisLatch(
        GameplayAction action,
        float axisMagnitude,
        boolean hasAxis,
        float pressThreshold,
        float releaseThreshold
    ) {
        if (action == null) {
            return false;
        }
        if (!hasAxis) {
            axisLatchStates.put(action, false);
            return false;
        }

        boolean latched = Boolean.TRUE.equals(axisLatchStates.get(action));
        if (latched) {
            if (axisMagnitude <= releaseThreshold) {
                axisLatchStates.put(action, false);
                return false;
            }
            return true;
        }

        if (axisMagnitude >= pressThreshold) {
            axisLatchStates.put(action, true);
            return true;
        }

        axisLatchStates.put(action, false);
        return false;
    }

    private static DirectionalReadState readDirectionalBindings(
        ControllerSnapshot snapshot,
        List<String> bindings
    ) {
        if (snapshot == null || bindings == null || bindings.isEmpty()) {
            return DirectionalReadState.empty();
        }

        boolean digitalPressed = false;
        boolean hasAxis = false;
        boolean hasRecognizedBinding = false;
        float axisMagnitude = 0.0F;

        for (String binding : bindings) {
            if (binding == null || binding.trim().isEmpty()) {
                continue;
            }

            boolean invertAxis = binding.startsWith("-");
            String normalized = invertAxis ? binding.substring(1) : binding;

            ControllerButton button = ControllerButton.fromTokenOrNull(normalized);
            if (button != null) {
                hasRecognizedBinding = true;
                if (snapshot.isPressed(button)) {
                    digitalPressed = true;
                }
                continue;
            }

            ControllerAxis axis = ControllerAxis.fromTokenOrNull(normalized);
            if (axis == null) {
                continue;
            }
            hasRecognizedBinding = true;
            hasAxis = true;

            float value = snapshot.axis(axis);
            if (invertAxis) {
                value = -value;
            }
            if (axis.isTrigger()) {
                value = normalizeTrigger(value);
            }
            axisMagnitude = Math.max(axisMagnitude, Math.max(0.0F, value));
        }

        return new DirectionalReadState(digitalPressed, hasAxis, hasRecognizedBinding, axisMagnitude);
    }

    private static DirectionalReadState fallbackDirectionalReadState(
        ControllerSnapshot snapshot,
        GameplayAction action
    ) {
        if (snapshot == null || action == null) {
            return DirectionalReadState.empty();
        }

        float leftX = snapshot.axis(ControllerAxis.LEFT_X);
        float leftY = snapshot.axis(ControllerAxis.LEFT_Y);
        switch (action) {
            case MENU_UP:
                return new DirectionalReadState(
                    snapshot.isPressed(ControllerButton.DPAD_UP),
                    true,
                    true,
                    Math.max(0.0F, -leftY)
                );
            case MENU_DOWN:
                return new DirectionalReadState(
                    snapshot.isPressed(ControllerButton.DPAD_DOWN),
                    true,
                    true,
                    Math.max(0.0F, leftY)
                );
            case MENU_LEFT:
                return new DirectionalReadState(
                    snapshot.isPressed(ControllerButton.DPAD_LEFT),
                    true,
                    true,
                    Math.max(0.0F, -leftX)
                );
            case MENU_RIGHT:
                return new DirectionalReadState(
                    snapshot.isPressed(ControllerButton.DPAD_RIGHT),
                    true,
                    true,
                    Math.max(0.0F, leftX)
                );
            default:
                return DirectionalReadState.empty();
        }
    }

    private static boolean actionPressedWithFallback(
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout,
        InputTranslator translator,
        GameplayAction action
    ) {
        if (snapshot == null || config == null || layout == null || translator == null || action == null) {
            return false;
        }
        if (translator.isActionPressed(snapshot, config, layout, action)) {
            return true;
        }
        return defaultMenuFallback(snapshot, config, action);
    }

    private static boolean defaultMenuFallback(
        ControllerSnapshot snapshot,
        ControllerConfig config,
        GameplayAction action
    ) {
        if (snapshot == null || config == null || action == null) {
            return false;
        }

        float axisThreshold = clampFloat(config.menuAxisThreshold, 0.2F, 0.95F, 0.35F);
        float triggerThreshold = clampFloat(config.triggerThreshold, 0.01F, 1.0F, 0.45F);
        float leftX = snapshot.axis(ControllerAxis.LEFT_X);
        float leftY = snapshot.axis(ControllerAxis.LEFT_Y);
        float leftTrigger = normalizeTrigger(snapshot.axis(ControllerAxis.LEFT_TRIGGER));
        float rightTrigger = normalizeTrigger(snapshot.axis(ControllerAxis.RIGHT_TRIGGER));

        switch (action) {
            case MENU_UP:
                return snapshot.isPressed(ControllerButton.DPAD_UP) || leftY <= -axisThreshold;
            case MENU_DOWN:
                return snapshot.isPressed(ControllerButton.DPAD_DOWN) || leftY >= axisThreshold;
            case MENU_LEFT:
                return snapshot.isPressed(ControllerButton.DPAD_LEFT) || leftX <= -axisThreshold;
            case MENU_RIGHT:
                return snapshot.isPressed(ControllerButton.DPAD_RIGHT) || leftX >= axisThreshold;
            case MENU_CONFIRM:
                return snapshot.isPressed(ControllerButton.SOUTH);
            case MENU_BACK:
                return snapshot.isPressed(ControllerButton.EAST);
            case MENU_PAGE_NEXT:
                return snapshot.isPressed(ControllerButton.RIGHT_BUMPER) || rightTrigger >= triggerThreshold;
            case MENU_PAGE_PREV:
                return snapshot.isPressed(ControllerButton.LEFT_BUMPER) || leftTrigger >= triggerThreshold;
            case MENU_TAB_NEXT:
                return snapshot.isPressed(ControllerButton.RIGHT_BUMPER);
            case MENU_TAB_PREV:
                return snapshot.isPressed(ControllerButton.LEFT_BUMPER);
            default:
                return false;
        }
    }

    private static float normalizeTrigger(float value) {
        float normalized = (value + 1.0F) * 0.5F;
        return Math.max(0.0F, Math.min(1.0F, normalized));
    }

    private static float clampFloat(float value, float min, float max, float fallback) {
        if (!Float.isFinite(value) || value < min || value > max) {
            return fallback;
        }
        return value;
    }

    /** A record on every other version; Java 8 spells it out. */
    private static final class DirectionalReadState {
        private final boolean digitalPressed;
        private final boolean hasAxis;
        private final boolean hasRecognizedBinding;
        private final float axisMagnitude;

        private DirectionalReadState(
            boolean digitalPressed,
            boolean hasAxis,
            boolean hasRecognizedBinding,
            float axisMagnitude
        ) {
            this.digitalPressed = digitalPressed;
            this.hasAxis = hasAxis;
            this.hasRecognizedBinding = hasRecognizedBinding;
            this.axisMagnitude = axisMagnitude;
        }

        private boolean digitalPressed() {
            return digitalPressed;
        }

        private boolean hasAxis() {
            return hasAxis;
        }

        private boolean hasRecognizedBinding() {
            return hasRecognizedBinding;
        }

        private float axisMagnitude() {
            return axisMagnitude;
        }

        private static DirectionalReadState empty() {
            return new DirectionalReadState(false, false, false, 0.0F);
        }
    }
}
