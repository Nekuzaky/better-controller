package com.bettercontroller.client.gui;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerAxis;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.translation.InputTranslator;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

public final class ControllerGuiNavigationHooks implements GuiNavigationHooks {
    private final ControllerInventorySelectionState inventorySelectionState;
    private final GuiNavigationController navigationController;
    private final GuiInputRouter inputRouter = new GuiInputRouter();
    private final InputTranslator inputTranslator;
    private final Consumer<ControllerConfig> virtualKeyboardRequest;
    /** Whether the last controller tick had a screen open; see {@link #onWorldTick}. */
    private boolean inScreen;

    public ControllerGuiNavigationHooks(
        InputTranslator inputTranslator,
        Consumer<ControllerConfig> virtualKeyboardRequest,
        ControllerInventorySelectionState inventorySelectionState
    ) {
        this.inputTranslator = inputTranslator;
        this.virtualKeyboardRequest = virtualKeyboardRequest;
        this.inventorySelectionState = inventorySelectionState == null
            ? new ControllerInventorySelectionState()
            : inventorySelectionState;
        this.navigationController = new GuiNavigationController(this.inventorySelectionState);
    }

    @Override
    public void onScreenTick(
        MinecraftClient client,
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout
    ) {
        if (client == null || client.currentScreen == null || snapshot == null || config == null || layout == null) {
            return;
        }

        inScreen = true;
        boolean allowConfirmRepeat = client.currentScreen instanceof ControllerVirtualKeyboardScreen;
        GuiInputFrame frame = inputRouter.route(snapshot, config, layout, inputTranslator, allowConfirmRepeat);

        navigationController.onScreenTick(client, frame, request -> {
            if (request && virtualKeyboardRequest != null) {
                virtualKeyboardRequest.accept(config);
            }
        }, config.debugLogging);

        navigationController.handleAnalogScroll(client, readRightStickY(snapshot, layout, config));
    }

    private static float readRightStickY(
        ControllerSnapshot snapshot,
        ControllerConfig.ResolvedLayout layout,
        ControllerConfig config
    ) {
        String token = layout.axisToken("look_y");
        if (token == null || token.isBlank()) {
            return 0.0F;
        }
        boolean invert = token.startsWith("-");
        String normalized = invert ? token.substring(1) : token;
        ControllerAxis axis = ControllerAxis.fromTokenOrNull(normalized);
        if (axis == null) {
            return 0.0F;
        }
        float value = snapshot.axis(axis);
        if (invert) {
            value = -value;
        }
        if (config.invertLookY) {
            value = -value;
        }
        return value;
    }

    public ControllerInventorySelectionState inventorySelectionState() {
        return inventorySelectionState;
    }

    /**
     * Called on every tick spent in the world. On the first one after a screen closes, lets go of
     * that screen's state so its widgets and slots are not kept alive for the rest of the session.
     * The router's memory of pressed buttons is deliberately kept: cleared, a button still held
     * when the next screen opens would read there as a fresh press.
     */
    public void onWorldTick() {
        if (!inScreen) {
            return;
        }
        inScreen = false;
        navigationController.forgetScreen();
    }

    public void reset() {
        inScreen = false;
        inputRouter.reset();
        navigationController.reset();
    }
}
