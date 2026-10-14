package com.bettercontroller.client.input;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.config.ControllerConfigManager;
import com.bettercontroller.client.glyph.ControllerGlyphService;
import com.bettercontroller.client.gui.ContainerNavigator;
import com.bettercontroller.client.gui.GuiScreenNavigator;
import com.bettercontroller.client.gui.VirtualKeyboardOverlay;
import com.bettercontroller.client.polling.ControllerMappings;
import com.bettercontroller.client.polling.ControllerPoller;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.polling.ControllerType;
import com.bettercontroller.client.polling.GlfwBridge;
import com.bettercontroller.client.render.ControllerDebugOverlay;
import com.bettercontroller.client.translation.GameplayInputFrame;
import com.bettercontroller.client.translation.InputTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.MovementInput;

import java.nio.file.Path;
import java.util.List;

/**
 * Owns the controller loop: poll once per client tick, re-read the sticks once per rendered frame.
 *
 * <p>The split matters for the camera. A tick is 50ms and a frame is often 8; sampling the look
 * stick per tick makes the camera visibly step, which is the single thing that makes a pad feel
 * worse than a mouse. Reading the axes again per frame costs one GLFW call and no allocation.
 */
public final class ControllerRuntime {
    private static final long NANOS_PER_MS = 1_000_000L;
    private static final long LOOK_DELTA_MIN_NANOS = NANOS_PER_MS;
    private static final long LOOK_DELTA_MAX_NANOS = 75L * NANOS_PER_MS;
    private static final long LOOK_DELTA_DEFAULT_NANOS = 16L * NANOS_PER_MS;
    private static final double LOOK_REFERENCE_SECONDS = 1.0D / 30.0D;

    /**
     * Weight of the newest frame in the smoothed frame delta. A stick reports a rate, so the angle
     * turned is rate x elapsed time - and the only elapsed time available is the previous frame's.
     * Under uneven frame pacing that turns frame-time noise into camera-speed noise, which a mouse
     * never suffers because its hardware reports an already-integrated displacement. Averaging a
     * few frames removes the noise; being a mean, it leaves the average turn rate untouched.
     */
    private static final float FRAME_DELTA_SMOOTHING = 0.25F;

    private final GlfwBridge glfw;
    private final ControllerPoller controllerPoller;
    private final ControllerConfigManager configManager;
    private final InputTranslator inputTranslator = new InputTranslator();
    private final MinecraftInputApplier inputApplier = new MinecraftInputApplier();
    private final ControllerGlyphService glyphService = new ControllerGlyphService();
    private final GuiScreenNavigator screenNavigator = new GuiScreenNavigator();
    private final ContainerNavigator containerNavigator = new ContainerNavigator();
    private final VirtualKeyboardOverlay virtualKeyboard = new VirtualKeyboardOverlay();
    private final ControllerDebugOverlay debugOverlay = new ControllerDebugOverlay();
    private final ScreenExitGate screenExitGate = new ScreenExitGate();
    private final Path configDirectory;

    private ControllerSnapshot latestSnapshot;
    private ControllerConfig latestConfig;
    private ControllerConfig.ResolvedLayout activeLayout;
    private ControllerType activeControllerType = ControllerType.NONE;
    private GameplayInputFrame latestFrame;
    private long lastRenderLookUpdateNanos;
    private float smoothedFrameSeconds;
    private boolean mappingsApplied;
    private boolean wasControllerConnected;
    private boolean wasScreenOpen;
    private boolean buttonsDroveLastTick;

    public ControllerRuntime(GlfwBridge glfw, Path configDirectory) {
        this.glfw = glfw;
        this.configDirectory = configDirectory;
        this.controllerPoller = new ControllerPoller(glfw);
        this.configManager = new ControllerConfigManager(configDirectory);
        this.latestConfig = configManager.getConfig();
        this.latestFrame = inputTranslator.emptyFrame();
    }

    public ControllerSnapshot latestSnapshot() {
        return latestSnapshot;
    }

    public ControllerConfig latestConfig() {
        return latestConfig;
    }

    public GameplayInputFrame latestFrame() {
        return latestFrame;
    }

    public ControllerGlyphService glyphs() {
        return glyphService;
    }

    public ControllerType activeControllerType() {
        return activeControllerType;
    }

    /** Name and GUID of a connected joystick GLFW cannot drive, or null. */
    public String unmappedJoystick() {
        return controllerPoller.unmappedJoystick();
    }

    public void onClientTick(Minecraft client) {
        if (client == null) {
            return;
        }

        // Deferred to the first tick rather than done at mod init: this calls GLFW, and it should
        // happen once the game is actually up.
        if (!mappingsApplied) {
            mappingsApplied = true;
            ControllerMappings.applyUserMappings(glfw, configDirectory);
        }

        latestConfig = configManager.getConfig();
        latestSnapshot = controllerPoller.pollSnapshot(
            latestConfig.preferredControllerGuid,
            latestConfig.preferredJoystickIndex
        );

        if (latestSnapshot == null || !latestSnapshot.isConnected()) {
            handleNoController(client);
            return;
        }
        if (!latestConfig.autoActivateOnController) {
            handleNoController(client);
            return;
        }

        activeControllerType = latestSnapshot.controllerType();
        glyphService.updateControllerType(activeControllerType);
        activeLayout = latestConfig.resolveLayout();
        wasControllerConnected = true;

        if (client.currentScreen != null) {
            tickInScreen(client);
            return;
        }
        if (wasScreenOpen) {
            // Once on the way out, not every tick: this clears the selections and the repeat
            // timers so the next screen opens fresh rather than inheriting the last one's state.
            // All three, not just the button navigator - the other two were left holding a
            // reference to a dead screen, and through it its container, slots and item stacks.
            wasScreenOpen = false;
            onScreenClosed();
            screenExitGate.arm();
        }
        tickInWorld(client);
    }

    /** A screen is open: drop every gameplay hold and drive the menu instead. */
    private void tickInScreen(Minecraft client) {
        wasScreenOpen = true;
        latestFrame = inputTranslator.emptyFrame();
        inputApplier.releaseGameplayHolds(client);
        // Exactly one of these drives the screen, and each says whether it took the tick.
        // Otherwise a single stick push would move a key, a slot and a button at once.
        boolean handled = virtualKeyboard.onScreenTick(
            client, latestSnapshot, latestConfig, activeLayout, inputTranslator);
        if (!handled) {
            handled = containerNavigator.onScreenTick(
                client, latestSnapshot, latestConfig, activeLayout, inputTranslator);
        }
        if (handled) {
            buttonsDroveLastTick = false;
            return;
        }
        if (!buttonsDroveLastTick) {
            // Taking over from the keyboard or the container navigator, whose B or A may still be
            // held: the B that dismisses the keyboard on a sign must not then close the sign.
            buttonsDroveLastTick = true;
            screenNavigator.suppressNextFrame();
        }
        screenNavigator.onScreenTick(client, latestSnapshot, latestConfig, activeLayout, inputTranslator);
    }

    private void tickInWorld(Minecraft client) {
        if (client.player == null || client.world == null) {
            latestFrame = inputTranslator.emptyFrame();
            inputApplier.releaseGameplayHolds(client);
            return;
        }

        GameplayInputFrame frame = inputTranslator.translate(latestSnapshot, latestConfig, activeLayout);
        screenExitGate.filter(frame);
        if (frame.chatTap()) {
            // Opening chat from the controller is the request for a keyboard. Pressing T is not:
            // a player already at the keyboard does not want half the screen covered by a worse
            // one. The chat screen itself opens a tick or two later, which is why this is a
            // request rather than a direct open.
            virtualKeyboard.requestOpen();
        }
        inputApplier.applyGameplayFrame(client, frame);
        latestFrame = frame;
    }

    /**
     * Called from {@code InputUpdateEvent}, right after vanilla rebuilt the movement vector from
     * the key bindings. Does nothing unless a controller is actually driving the game, so vanilla
     * keyboard movement is untouched otherwise.
     */
    public void applyAnalogMovement(MovementInput input, boolean sneaking) {
        if (latestConfig == null || !latestConfig.autoActivateOnController) {
            return;
        }
        if (latestSnapshot == null || !latestSnapshot.isConnected() || latestFrame == null) {
            return;
        }
        inputApplier.applyAnalogMovement(input, latestFrame, sneaking);
    }

    public void onRenderFrame(Minecraft client) {
        if (!isLookActive(client)) {
            resetRenderLookClock();
            return;
        }

        float deltaSeconds = advanceRenderLookClock();
        latestSnapshot = controllerPoller.refreshAxes();
        inputTranslator.updateLook(latestSnapshot, latestConfig, activeLayout, latestFrame, deltaSeconds);

        double baseScale = deltaSeconds / LOOK_REFERENCE_SECONDS;
        double speedMultiplier = latestConfig.lookSpeedMultiplier;
        double stickMagnitude = Math.max(
            Math.abs(latestFrame.processedLookX()),
            Math.abs(latestFrame.processedLookY())
        );
        double turnBoost = 1.0D + Math.max(0.0D, stickMagnitude - 0.60D) * 1.3D;
        double frameScale = clamp(baseScale * speedMultiplier * turnBoost, 0.20D, 8.50D);
        inputApplier.applyLookDelta(
            client,
            latestFrame.lookDeltaX() * frameScale,
            latestFrame.lookDeltaY() * frameScale
        );
    }

    /** Forge fires this after a screen builds its buttons, and again whenever it rebuilds them. */
    public void onScreenInit(GuiScreen screen, List<GuiButton> buttons) {
        screenNavigator.onScreenInit(screen, buttons);
    }

    public void onScreenClosed() {
        buttonsDroveLastTick = false;
        screenNavigator.onScreenClosed();
        virtualKeyboard.reset();
        containerNavigator.close();
    }

    public void drawScreenSelection(GuiScreen screen) {
        screenNavigator.drawSelection(screen);
        containerNavigator.draw(Minecraft.getMinecraft(), screen);
        virtualKeyboard.draw(Minecraft.getMinecraft(), screen);
    }


    public void renderDebugOverlay(Minecraft client) {
        debugOverlay.render(client, this);
    }

    public void toggleDebugOverlay() {
        debugOverlay.toggleVisibility();
    }
    public void shutdown() {
        if (glfw != null) {
            glfw.shutdown();
        }
    }

    private void handleNoController(Minecraft client) {
        activeControllerType = ControllerType.NONE;
        glyphService.updateControllerType(ControllerType.NONE);
        activeLayout = null;
        latestFrame = inputTranslator.emptyFrame();
        if (wasControllerConnected) {
            // Only once, on the way down: otherwise this would fight the keyboard every tick for
            // players who have no controller at all.
            inputApplier.releaseGameplayHolds(client);
            wasControllerConnected = false;
        }
    }

    private boolean isLookActive(Minecraft client) {
        if (client == null || latestConfig == null || !latestConfig.autoActivateOnController) {
            return false;
        }
        if (latestSnapshot == null || !latestSnapshot.isConnected() || latestFrame == null) {
            return false;
        }
        if (activeLayout == null) {
            return false;
        }
        return client.currentScreen == null && client.player != null && client.world != null;
    }

    private void resetRenderLookClock() {
        lastRenderLookUpdateNanos = 0L;
        smoothedFrameSeconds = 0.0F;
    }

    private float advanceRenderLookClock() {
        long now = System.nanoTime();
        long deltaNanos = lastRenderLookUpdateNanos == 0L
            ? LOOK_DELTA_DEFAULT_NANOS
            : now - lastRenderLookUpdateNanos;
        deltaNanos = Math.max(LOOK_DELTA_MIN_NANOS, Math.min(deltaNanos, LOOK_DELTA_MAX_NANOS));
        lastRenderLookUpdateNanos = now;

        float rawSeconds = (float) (deltaNanos / 1_000_000_000.0D);
        if (smoothedFrameSeconds <= 0.0F) {
            smoothedFrameSeconds = rawSeconds;
        } else {
            smoothedFrameSeconds += (rawSeconds - smoothedFrameSeconds) * FRAME_DELTA_SMOOTHING;
        }
        return smoothedFrameSeconds;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
