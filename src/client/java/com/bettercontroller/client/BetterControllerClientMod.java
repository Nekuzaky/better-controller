package com.bettercontroller.client;

import com.bettercontroller.BetterControllerMod;
import com.bettercontroller.client.gui.BetterControllerSettingsScreen;
import com.bettercontroller.client.input.ControllerRuntime;
import com.bettercontroller.client.render.ControllerDebugOverlayRenderer;
import com.bettercontroller.client.render.ControllerHUDRenderer;
import com.bettercontroller.client.render.ControllerInventoryHighlightRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class BetterControllerClientMod implements ClientModInitializer {
    private static final Identifier BETTERCONTROLLER_HUD_LAYER_ID = Identifier.of("bettercontroller", "runtime_hud");
    private static final int BUTTON_HEIGHT = 20;
    private static final int WIDGET_GAP = 4;
    private static final int SCREEN_MARGIN = 4;
    private static final int MIN_BUTTON_WIDTH = 120;
    private static final int CORNER_BUTTON_WIDTH = 146;
    /** Reached from GameRendererMixin, which runs on the vanilla per-frame input path. */
    private static ControllerRuntime activeRuntime;

    private final ControllerRuntime controllerRuntime = new ControllerRuntime();
    private final ControllerHUDRenderer controllerHUDRenderer = new ControllerHUDRenderer();
    private final ControllerDebugOverlayRenderer debugOverlayRenderer = new ControllerDebugOverlayRenderer();
    private final ControllerInventoryHighlightRenderer inventoryHighlightRenderer = new ControllerInventoryHighlightRenderer();
    private boolean debugToggleLatch;

    public static ControllerRuntime runtime() {
        return activeRuntime;
    }

    @Override
    public void onInitializeClient() {
        activeRuntime = controllerRuntime;
        ClientTickEvents.START_CLIENT_TICK.register(controllerRuntime::tick);
        ClientTickEvents.END_CLIENT_TICK.register(this::handleDebugToggle);
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof OptionsScreen || screen instanceof ControlsOptionsScreen || screen instanceof GameMenuScreen) {
                addSettingsButton(client, screen, scaledWidth);
            }
            ScreenEvents.afterRender(screen).register((renderedScreen, drawContext, mouseX, mouseY, tickDelta) -> {
                MinecraftClient currentClient = MinecraftClient.getInstance();
                inventoryHighlightRenderer.render(currentClient, drawContext, controllerRuntime);
            });
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES, BETTERCONTROLLER_HUD_LAYER_ID, (drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            controllerHUDRenderer.render(client, drawContext, controllerRuntime);
            debugOverlayRenderer.render(client, drawContext, controllerRuntime);
        });
        BetterControllerMod.LOGGER.info("Controller config path: {}", controllerRuntime.configPath());
        BetterControllerMod.LOGGER.info("BetterController client initialized.");
    }

    private void handleDebugToggle(MinecraftClient client) {
        if (client == null || client.getWindow() == null) {
            return;
        }

        boolean pressed = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_F8) == GLFW.GLFW_PRESS;
        if (pressed && !debugToggleLatch) {
            debugOverlayRenderer.toggleVisibility();
        }
        debugToggleLatch = pressed;
    }

    private void addSettingsButton(MinecraftClient client, Screen screen, int scaledWidth) {
        if (client == null || screen == null) {
            return;
        }

        Text buttonText = Text.translatable("bettercontroller.ui.open_settings");
        String buttonLabel = buttonText.getString();
        List<ClickableWidget> widgets = Screens.getButtons(screen);
        boolean alreadyPresent = widgets.stream()
            .anyMatch(widget -> widget instanceof ButtonWidget button
                && buttonLabel.equals(button.getMessage().getString()));
        if (alreadyPresent) {
            return;
        }

        Placement placement = resolvePlacement(screen, widgets, scaledWidth);
        ButtonWidget button = ButtonWidget.builder(
            buttonText,
            widget -> client.setScreen(new BetterControllerSettingsScreen(screen, controllerRuntime))
        ).dimensions(placement.x(), placement.y(), placement.width(), BUTTON_HEIGHT).build();
        widgets.add(button);
    }

    /**
     * Positions the button relative to the screen's own bottom-most widget - normally Done or
     * Back to Game - so it reads as another row of the vanilla layout instead of floating in a
     * corner, at any window size or GUI scale. Candidates are tried in order and the first one
     * that stays on screen without overlapping an existing widget wins.
     */
    private static Placement resolvePlacement(Screen screen, List<ClickableWidget> widgets, int scaledWidth) {
        ClickableWidget anchor = bottomMostWidget(widgets);
        if (anchor != null) {
            Placement above = new Placement(
                anchor.getX(),
                anchor.getY() - BUTTON_HEIGHT - WIDGET_GAP,
                anchor.getWidth()
            );
            if (fits(above, screen, widgets, scaledWidth)) {
                return above;
            }

            int leftWidth = Math.min(anchor.getWidth(), anchor.getX() - SCREEN_MARGIN - WIDGET_GAP);
            Placement left = new Placement(anchor.getX() - WIDGET_GAP - leftWidth, anchor.getY(), leftWidth);
            if (fits(left, screen, widgets, scaledWidth)) {
                return left;
            }

            int anchorRight = anchor.getX() + anchor.getWidth();
            int rightWidth = Math.min(anchor.getWidth(), scaledWidth - SCREEN_MARGIN - WIDGET_GAP - anchorRight);
            Placement right = new Placement(anchorRight + WIDGET_GAP, anchor.getY(), rightWidth);
            if (fits(right, screen, widgets, scaledWidth)) {
                return right;
            }
        }
        return new Placement(scaledWidth - CORNER_BUTTON_WIDTH - 6, 6, CORNER_BUTTON_WIDTH);
    }

    private static ClickableWidget bottomMostWidget(List<ClickableWidget> widgets) {
        ClickableWidget bottomMost = null;
        for (ClickableWidget widget : widgets) {
            if (!widget.visible) {
                continue;
            }
            if (bottomMost == null || widget.getY() + widget.getHeight() > bottomMost.getY() + bottomMost.getHeight()) {
                bottomMost = widget;
            }
        }
        return bottomMost;
    }

    private static boolean fits(Placement placement, Screen screen, List<ClickableWidget> widgets, int scaledWidth) {
        if (placement.width() < MIN_BUTTON_WIDTH) {
            return false;
        }
        if (placement.x() < SCREEN_MARGIN || placement.x() + placement.width() > scaledWidth - SCREEN_MARGIN) {
            return false;
        }
        if (placement.y() < SCREEN_MARGIN || placement.y() + BUTTON_HEIGHT > screen.height - SCREEN_MARGIN) {
            return false;
        }
        for (ClickableWidget widget : widgets) {
            if (widget.visible && overlaps(placement, widget)) {
                return false;
            }
        }
        return true;
    }

    private static boolean overlaps(Placement placement, ClickableWidget widget) {
        return placement.x() < widget.getX() + widget.getWidth()
            && widget.getX() < placement.x() + placement.width()
            && placement.y() < widget.getY() + widget.getHeight()
            && widget.getY() < placement.y() + BUTTON_HEIGHT;
    }

    private record Placement(int x, int y, int width) {
    }
}
