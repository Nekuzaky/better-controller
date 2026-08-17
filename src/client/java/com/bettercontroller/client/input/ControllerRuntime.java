package com.bettercontroller.client.input;

import com.bettercontroller.client.chat.VirtualKeyboardLauncher;
import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.config.ControllerConfigManager;
import com.bettercontroller.client.glyph.ControllerGlyphService;
import com.bettercontroller.client.gui.ControllerGuiNavigationHooks;
import com.bettercontroller.client.gui.ControllerInventorySelectionState;
import com.bettercontroller.client.polling.ControllerPoller;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.polling.ControllerType;
import com.bettercontroller.client.translation.GameplayInputFrame;
import com.bettercontroller.client.translation.InputTranslator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.function.Consumer;

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
    private static final long STATUS_CONNECTED_MS = 3000L;
    private static final long STATUS_DISCONNECTED_MS = 3200L;
    private static final long STATUS_PROFILE_SWITCH_MS = 2400L;

    private final ControllerPoller controllerPoller = new ControllerPoller();
    private final ControllerConfigManager configManager = new ControllerConfigManager();
    private final InputTranslator inputTranslator = new InputTranslator();
    private final MinecraftInputApplier inputApplier = new MinecraftInputApplier();
    private final ControllerInventorySelectionState inventorySelectionState = new ControllerInventorySelectionState();
    private final VirtualKeyboardLauncher virtualKeyboardLauncher = new VirtualKeyboardLauncher();
    private final ControllerGuiNavigationHooks guiNavigationHooks = new ControllerGuiNavigationHooks(
        inputTranslator,
        virtualKeyboardLauncher::tryOpen,
        inventorySelectionState
    );
    private final ControllerGlyphService glyphService = new ControllerGlyphService();

    private ControllerSnapshot latestSnapshot;
    private ControllerConfig latestConfig;
    private ControllerConfig.ResolvedLayout activeLayout;
    private ControllerType activeControllerType = ControllerType.NONE;
    private GameplayInputFrame latestFrame;
    private long lastRenderLookUpdateNanos;
    private float smoothedFrameSeconds;
    private boolean wasControllerConnected;
    private int previousJoystickId = -1;
    private ControllerType previousControllerType = ControllerType.NONE;
    private String runtimeStatusMessage = "";
    private long runtimeStatusMessageUntilMs;

    public ControllerRuntime() {
        this.configManager.load();
        this.latestConfig = configManager.getConfig();
        this.latestFrame = inputTranslator.emptyFrame();
    }

    public Path configPath() {
        return configManager.configPath();
    }

    public ControllerSnapshot latestSnapshot() { return latestSnapshot; }
    public ControllerConfig latestConfig() { return latestConfig; }
    public ControllerConfig.ResolvedLayout activeLayout() { return activeLayout; }
    public ControllerType activeControllerType() { return activeControllerType; }
    public GameplayInputFrame latestFrame() { return latestFrame; }
    public ControllerGlyphService glyphs() { return glyphService; }
    public ControllerInventorySelectionState inventorySelectionState() { return inventorySelectionState; }

    public boolean isHudHintsEnabled() {
        return latestConfig != null && latestConfig.hudHintsEnabled;
    }

    public void setHudHintsEnabled(boolean enabled) {
        updateConfig(config -> config.hudHintsEnabled = enabled);
    }

    public void setDebugOverlayEnabled(boolean enabled) {
        updateConfig(config -> config.debugOverlayEnabled = enabled);
    }

    public String runtimeStatusMessage() {
        if (runtimeStatusMessage.isBlank()) {
            return "";
        }
        if (System.currentTimeMillis() > runtimeStatusMessageUntilMs) {
            runtimeStatusMessage = "";
            runtimeStatusMessageUntilMs = 0L;
            return "";
        }
        return runtimeStatusMessage;
    }

    public void resetToDefaults() {
        latestConfig = configManager.save(ControllerConfig.createDefault());
    }

    public void updateConfig(Consumer<ControllerConfig> updater) {
        if (updater == null) {
            return;
        }
        ControllerConfig config = mutableConfig();
        updater.accept(config);
        latestConfig = configManager.save(config);
    }

    public void previewConfig(Consumer<ControllerConfig> updater) {
        if (updater == null) {
            return;
        }
        ControllerConfig config = mutableConfig();
        updater.accept(config);
        latestConfig = config;
    }

    public void tick(MinecraftClient client) {
        if (client == null) {
            return;
        }

        latestConfig = configManager.getConfig();
        latestSnapshot = controllerPoller.pollSnapshot(
            latestConfig.preferredControllerGuid,
            latestConfig.preferredJoystickIndex
        );

        if (latestSnapshot == null || !latestSnapshot.isConnected()) {
            tickWhileDisconnected(client);
            return;
        }

        updateActiveControllerProfile();
        if (!latestConfig.autoActivateOnController) {
            tickWhileSuspended(client);
            return;
        }
        if (client.currentScreen != null) {
            tickInScreen(client);
            return;
        }
        if (client.player == null || client.world == null) {
            tickWhileInactive(client);
            return;
        }
        tickInWorld(client);
    }

    private void tickWhileDisconnected(MinecraftClient client) {
        activeControllerType = ControllerType.NONE;
        activeLayout = null;
        latestFrame = inputTranslator.emptyFrame();
        glyphService.updateControllerType(ControllerType.NONE);
        if (wasControllerConnected) {
            pushRuntimeStatus(Text.translatable("bettercontroller.status.disconnected").getString(), STATUS_DISCONNECTED_MS);
        }
        wasControllerConnected = false;
        previousJoystickId = -1;
        previousControllerType = ControllerType.NONE;
        inputApplier.releaseGameplayHolds(client);
        inputTranslator.resetState();
        guiNavigationHooks.reset();
        resetRenderLookClock();
    }

    private void updateActiveControllerProfile() {
        activeControllerType = latestSnapshot.controllerType();
        glyphService.updateControllerType(activeControllerType);
        activeLayout = latestConfig.resolveLayout();

        if (!wasControllerConnected || previousJoystickId != latestSnapshot.joystickId()) {
            pushRuntimeStatus(
                Text.translatable(
                    "bettercontroller.status.connected",
                    latestSnapshot.joystickName(),
                    activeControllerType
                ).getString(),
                STATUS_CONNECTED_MS
            );
        } else if (previousControllerType != activeControllerType) {
            pushRuntimeStatus(
                Text.translatable("bettercontroller.status.profile_switched", activeControllerType).getString(),
                STATUS_PROFILE_SWITCH_MS
            );
        }
        wasControllerConnected = true;
        previousJoystickId = latestSnapshot.joystickId();
        previousControllerType = activeControllerType;
    }

    private void tickWhileSuspended(MinecraftClient client) {
        latestFrame = inputTranslator.emptyFrame();
        inputApplier.releaseGameplayHolds(client);
        inputTranslator.resetState();
        guiNavigationHooks.reset();
        resetRenderLookClock();
    }

    private void tickInScreen(MinecraftClient client) {
        latestFrame = inputTranslator.emptyFrame();
        inputApplier.releaseGameplayHolds(client);
        inputTranslator.resetState();
        virtualKeyboardLauncher.processPending(latestConfig);
        guiNavigationHooks.onScreenTick(client, latestSnapshot, latestConfig, activeLayout);
        resetRenderLookClock();
    }

    private void tickWhileInactive(MinecraftClient client) {
        latestFrame = inputTranslator.emptyFrame();
        inputApplier.releaseGameplayHolds(client);
        inputTranslator.resetState();
        guiNavigationHooks.reset();
        resetRenderLookClock();
    }

    private void tickInWorld(MinecraftClient client) {
        GameplayInputFrame frame = inputTranslator.translate(latestSnapshot, latestConfig, activeLayout);
        if (frame.chatTap()) {
            virtualKeyboardLauncher.requestOpenOnNextTextScreen(latestConfig);
        }
        inputApplier.applyGameplayFrame(client, frame, latestConfig.debugLogging);
        latestFrame = frame;
    }

    /**
     * Camera path, run once per rendered frame. The look axes are re-sampled here rather than in
     * {@link #tick} so the stick is read at frame rate instead of at the 20 Hz client tick.
     */
    public void onRenderFrame(MinecraftClient client) {
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

    private boolean isLookActive(MinecraftClient client) {
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

    private void resetRenderLookClock() {
        lastRenderLookUpdateNanos = 0L;
        smoothedFrameSeconds = 0.0F;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private ControllerConfig mutableConfig() {
        ControllerConfig config = latestConfig != null ? latestConfig : configManager.getConfig();
        if (config == null) {
            config = ControllerConfig.createDefault();
        }
        return config;
    }

    private void pushRuntimeStatus(String message, long durationMs) {
        if (message == null || message.isBlank() || durationMs <= 0L) {
            return;
        }
        runtimeStatusMessage = message;
        runtimeStatusMessageUntilMs = System.currentTimeMillis() + durationMs;
    }
}
