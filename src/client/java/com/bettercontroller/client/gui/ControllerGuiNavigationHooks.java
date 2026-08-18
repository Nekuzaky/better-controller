package com.bettercontroller.client.gui;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerAxis;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.translation.InputTranslator;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

public final class ControllerGuiNavigationHooks implements GuiNavigationHooks {
    private final ControllerInventorySelectionState inventorySelectionState;
    private final GuiNavigationController navigationController;
    private final GuiInputRouter inputRouter = new GuiInputRouter();
    private final InputTranslator inputTranslator;
    private final Consumer<ControllerConfig> virtualKeyboardRequest;

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
        Minecraft client,
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout
    ) {
        if (client == null || client.screen == null || snapshot == null || config == null || layout == null) {
            return;
        }

        boolean allowConfirmRepeat = client.screen instanceof ControllerVirtualKeyboardScreen;
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

    public void reset() {
        inputRouter.reset();
        navigationController.reset();
    }
}
