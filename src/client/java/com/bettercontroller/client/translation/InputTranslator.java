package com.bettercontroller.client.translation;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerAxis;
import com.bettercontroller.client.polling.ControllerButton;
import com.bettercontroller.client.polling.ControllerSnapshot;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

public final class InputTranslator {
    /** Smoothing strengths are expressed per client tick (50 ms) and rescaled to the frame delta. */
    private static final float SMOOTHING_REFERENCE_SECONDS = 0.05F;

    private static final GameplayAction[] HOTBAR_ACTIONS = {
        GameplayAction.HOTBAR_1, GameplayAction.HOTBAR_2, GameplayAction.HOTBAR_3,
        GameplayAction.HOTBAR_4, GameplayAction.HOTBAR_5, GameplayAction.HOTBAR_6,
        GameplayAction.HOTBAR_7, GameplayAction.HOTBAR_8, GameplayAction.HOTBAR_9
    };

    private final EnumMap<GameplayAction, Boolean> previousActionStates = new EnumMap<>(GameplayAction.class);
    private final GameplayInputFrame reusableFrame = new GameplayInputFrame();
    private float smoothedLookX;
    private float smoothedLookY;

    public GameplayInputFrame emptyFrame() {
        reusableFrame.clear();
        return reusableFrame;
    }

    /**
     * Translates everything that is tick-paced: movement, holds, taps, hotbar. Look is deliberately
     * excluded - the camera is driven per rendered frame through {@link #updateLook}, so sampling it
     * here would both stale the stick to 20 Hz and advance the smoothing state twice.
     */
    public GameplayInputFrame translate(
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout
    ) {
        if (snapshot == null || config == null || layout == null) {
            return emptyFrame();
        }

        reusableFrame.clear();
        translateMovement(snapshot, config, layout, reusableFrame);
        translateHolds(snapshot, config, layout, reusableFrame);
        translateTaps(snapshot, config, layout, reusableFrame);
        translateHotbar(snapshot, config, layout, reusableFrame);
        return reusableFrame;
    }

    /**
     * Samples the look axes and writes the resulting delta into {@code frame}. Called once per
     * rendered frame; {@code deltaSeconds} is the elapsed frame time, used to keep camera smoothing
     * identical whether the game runs at 30 or 240 fps.
     */
    public GameplayInputFrame updateLook(
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout,
        GameplayInputFrame frame,
        float deltaSeconds
    ) {
        if (frame == null) {
            return null;
        }
        if (snapshot == null || config == null || layout == null) {
            frame.setLook(0.0D, 0.0D, 0.0F, 0.0F);
            return frame;
        }

        translateLook(snapshot, config, layout, frame, deltaSeconds);
        return frame;
    }

    public boolean isActionPressed(
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout,
        GameplayAction action
    ) {
        List<String> bindings = layout.actionBindings(action.configKey());
        if (bindings.isEmpty()) {
            return false;
        }

        for (int i = 0; i < bindings.size(); i++) {
            if (isBindingPressed(snapshot, config, action, bindings.get(i))) {
                return true;
            }
        }
        return false;
    }

    public void resetState() {
        previousActionStates.clear();
        smoothedLookX = 0.0F;
        smoothedLookY = 0.0F;
    }

    private void translateMovement(
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout,
        GameplayInputFrame frame
    ) {
        float rawX = readAxis(snapshot, layout.axisToken("move_x"));
        float rawY = readAxis(snapshot, layout.axisToken("move_y"));
        float magnitude = (float) Math.sqrt((rawX * rawX) + (rawY * rawY));
        float factor = radialDeadzoneFactor(magnitude, config.movementDeadzone);
        float moveX = rawX * factor;
        float moveY = rawY * factor;

        frame.setMovement(
            moveX < -0.05F,
            moveX > 0.05F,
            moveY < -0.05F,
            moveY > 0.05F,
            moveX,
            moveY
        );
    }

    private void translateLook(
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout,
        GameplayInputFrame frame,
        float deltaSeconds
    ) {
        float lookX = applyLookDeadzone(readAxis(snapshot, layout.axisToken("look_x")), config.lookDeadzone, config.lookAntiDeadzone);
        float lookY = applyLookDeadzone(readAxis(snapshot, layout.axisToken("look_y")), config.lookDeadzone, config.lookAntiDeadzone);

        lookX = applyResponseCurve(lookX, config.lookResponseCurve);
        lookY = applyResponseCurve(lookY, config.lookResponseCurve);
        if (config.invertLookY) {
            lookY = -lookY;
        }

        float centerStabilityThreshold = clamp(config.lookDeadzone * 0.16F, 0.003F, 0.035F);
        lookX = suppressMicroJitter(lookX, smoothedLookX, centerStabilityThreshold);
        lookY = suppressMicroJitter(lookY, smoothedLookY, centerStabilityThreshold);

        if (config.cameraSmoothing) {
            float smoothing = resolveAdaptiveSmoothing(config.cameraSmoothingStrength, lookX, lookY);
            smoothing = scaleSmoothingToFrame(smoothing, deltaSeconds);
            smoothedLookX += (lookX - smoothedLookX) * smoothing;
            smoothedLookY += (lookY - smoothedLookY) * smoothing;
        } else {
            smoothedLookX = lookX;
            smoothedLookY = lookY;
        }
        smoothedLookX = snapNearZero(smoothedLookX, 0.0008F);
        smoothedLookY = snapNearZero(smoothedLookY, 0.0008F);

        frame.setLook(
            smoothedLookX * config.lookSensitivityX,
            smoothedLookY * config.lookSensitivityY,
            smoothedLookX,
            smoothedLookY
        );
    }

    private void translateHolds(
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout,
        GameplayInputFrame frame
    ) {
        frame.setHolds(
            isActionPressed(snapshot, config, layout, GameplayAction.JUMP),
            isActionPressed(snapshot, config, layout, GameplayAction.SPRINT),
            isActionPressed(snapshot, config, layout, GameplayAction.SNEAK),
            isActionPressed(snapshot, config, layout, GameplayAction.ATTACK),
            isActionPressed(snapshot, config, layout, GameplayAction.USE),
            isActionPressed(snapshot, config, layout, GameplayAction.PLAYER_LIST)
        );
    }

    private void translateTaps(
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout,
        GameplayInputFrame frame
    ) {
        frame.setTaps(
            risingEdgeAction(snapshot, config, layout, GameplayAction.INVENTORY),
            risingEdgeAction(snapshot, config, layout, GameplayAction.SWAP_HANDS),
            risingEdgeAction(snapshot, config, layout, GameplayAction.DROP_ITEM),
            risingEdgeAction(snapshot, config, layout, GameplayAction.OPEN_CHAT),
            risingEdgeAction(snapshot, config, layout, GameplayAction.TOGGLE_PERSPECTIVE),
            risingEdgeAction(snapshot, config, layout, GameplayAction.PAUSE),
            risingEdgeAction(snapshot, config, layout, GameplayAction.PICK_BLOCK)
        );
    }

    private void translateHotbar(
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout,
        GameplayInputFrame frame
    ) {
        int step = (risingEdgeAction(snapshot, config, layout, GameplayAction.HOTBAR_NEXT) ? 1 : 0)
            + (risingEdgeAction(snapshot, config, layout, GameplayAction.HOTBAR_PREVIOUS) ? -1 : 0);

        int select = -1;
        for (int i = 0; i < HOTBAR_ACTIONS.length; i++) {
            if (risingEdgeAction(snapshot, config, layout, HOTBAR_ACTIONS[i])) {
                select = i;
                break;
            }
        }
        frame.setHotbar(step, select);
    }

    private boolean risingEdgeAction(
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout,
        GameplayAction action
    ) {
        boolean current = isActionPressed(snapshot, config, layout, action);
        boolean previous = Boolean.TRUE.equals(previousActionStates.get(action));
        previousActionStates.put(action, current);
        return current && !previous;
    }

    private static boolean isBindingPressed(
        ControllerSnapshot snapshot,
        ControllerConfig config,
        GameplayAction action,
        String bindingToken
    ) {
        if (bindingToken == null || bindingToken.isBlank()) {
            return false;
        }

        boolean invertAxis = bindingToken.startsWith("-");
        String normalizedToken = invertAxis ? bindingToken.substring(1) : bindingToken;

        ControllerButton buttonMatch = ControllerButton.fromTokenOrNull(normalizedToken);
        if (buttonMatch != null) {
            return snapshot.isPressed(buttonMatch);
        }

        ControllerAxis axisMatch = ControllerAxis.fromTokenOrNull(normalizedToken);
        if (axisMatch == null) {
            return false;
        }

        float value = snapshot.axis(axisMatch);
        if (invertAxis) {
            value = -value;
        }

        if (axisMatch.isTrigger()) {
            float normalized = (value + 1.0F) * 0.5F;
            return normalized >= config.triggerThreshold || value >= config.triggerThreshold;
        }

        float axisPressThreshold = isMenuAction(action) ? config.menuAxisThreshold : 0.55F;
        return value >= axisPressThreshold;
    }

    private static boolean isMenuAction(GameplayAction action) {
        return action != null && action.configKey().startsWith("menu_");
    }

    private static float readAxis(ControllerSnapshot snapshot, String axisToken) {
        if (axisToken == null || axisToken.isBlank()) {
            return 0.0F;
        }

        boolean invert = axisToken.startsWith("-");
        String normalizedToken = invert ? axisToken.substring(1) : axisToken;
        ControllerAxis axis = ControllerAxis.fromTokenOrNull(normalizedToken);
        if (axis == null) {
            return 0.0F;
        }

        float value = snapshot.axis(axis);
        return invert ? -value : value;
    }

    /**
     * Scale to apply to both movement axes for a circular deadzone.
     *
     * <p>Applying a deadzone to each axis on its own makes the dead area a square: a diagonal push
     * has to travel about 1.41x further to escape it, and rescaling each axis against its own
     * threshold bends the vector away from where the stick actually points. Working from the
     * vector's length fixes both - the direction is never touched, only its length.
     *
     * @return 0 inside the deadzone, otherwise the factor mapping the remaining travel onto 0..1.
     */
    static float radialDeadzoneFactor(float magnitude, float deadzone) {
        if (magnitude <= 0.0F || magnitude < deadzone) {
            return 0.0F;
        }
        if (deadzone >= 1.0F) {
            return 0.0F;
        }
        // Sticks report a square gate, so a corner reads about 1.41: clamp, or diagonals would
        // come out faster than cardinals.
        float scaled = Math.min((magnitude - deadzone) / (1.0F - deadzone), 1.0F);
        return scaled / magnitude;
    }

    private static float applyLookDeadzone(float value, float deadzone, float antiDeadzone) {
        float absolute = Math.abs(value);
        if (absolute < deadzone) {
            return 0.0F;
        }

        float scaled = (absolute - deadzone) / (1.0F - deadzone);
        float anti = clamp(antiDeadzone, 0.0F, 0.35F);
        float adjusted = anti + (scaled * (1.0F - anti));
        return Math.copySign(adjusted, value);
    }

    private static float applyResponseCurve(float value, String curveType) {
        String curve = curveType == null ? "linear" : curveType.toLowerCase(Locale.ROOT);
        float absolute = Math.abs(value);
        float curved = switch (curve) {
            case "exponential_light" -> (float) Math.pow(absolute, 1.35D);
            case "exponential_strong" -> (float) Math.pow(absolute, 1.75D);
            default -> absolute;
        };
        return Math.copySign(curved, value);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float resolveAdaptiveSmoothing(float baseStrength, float lookX, float lookY) {
        float base = clamp(baseStrength, 0.01F, 1.0F);
        float magnitude = Math.max(Math.abs(lookX), Math.abs(lookY));
        if (magnitude <= 0.50F) {
            return base;
        }
        float responsivenessScale = 1.0F - ((magnitude - 0.50F) / 0.50F) * 0.55F;
        return clamp(base * responsivenessScale, 0.08F, 1.0F);
    }

    /**
     * Converts a per-tick smoothing factor into the equivalent factor for a frame of
     * {@code deltaSeconds}, so a 144 fps client does not smooth seven times faster than a 20 Hz one.
     */
    private static float scaleSmoothingToFrame(float smoothing, float deltaSeconds) {
        if (smoothing >= 1.0F) {
            return 1.0F;
        }
        if (smoothing <= 0.0F || deltaSeconds <= 0.0F) {
            return 0.0F;
        }
        double frames = deltaSeconds / SMOOTHING_REFERENCE_SECONDS;
        return clamp((float) (1.0D - Math.pow(1.0D - smoothing, frames)), 0.0F, 1.0F);
    }

    private static float suppressMicroJitter(float value, float previousSmoothed, float threshold) {
        float absolute = Math.abs(value);
        if (absolute >= threshold) {
            return value;
        }
        if (Math.abs(previousSmoothed) >= threshold * 1.25F) {
            return value;
        }
        return 0.0F;
    }

    private static float snapNearZero(float value, float threshold) {
        if (Math.abs(value) < threshold) {
            return 0.0F;
        }
        return value;
    }
}
