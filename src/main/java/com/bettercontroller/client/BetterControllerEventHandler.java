package com.bettercontroller.client;

import com.bettercontroller.client.input.ControllerRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Where the mod meets the game.
 *
 * <p>The Fabric versions need mixins for every hook below. Forge 1.12.2 already publishes them all
 * as events, so this port injects into nothing - which also means nothing here can conflict with
 * another mod's injection, a real concern on a version whose big packs run hundreds of mods.
 */
@SideOnly(Side.CLIENT)
public final class BetterControllerEventHandler {

    private final ControllerRuntime runtime;
    private final KeyBinding debugOverlayKey;

    public BetterControllerEventHandler(ControllerRuntime runtime, KeyBinding debugOverlayKey) {
        this.runtime = runtime;
        this.debugOverlayKey = debugOverlayKey;
    }

    /** Buttons, triggers and hotbar, once per tick. */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        runtime.onClientTick(Minecraft.getMinecraft());
    }

    /**
     * The camera, once per rendered frame rather than once per tick.
     *
     * <p>START, not END: the look delta has to land before the frame is drawn, or the camera shows
     * a position one frame stale and the whole point of sampling per frame is lost.
     */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        runtime.onRenderFrame(Minecraft.getMinecraft());
    }

    /**
     * The analog movement magnitude.
     *
     * <p>Forge fires this immediately after {@code MovementInputFromOptions.updatePlayerMoveState},
     * which is the only point where the value survives to be read - vanilla rebuilds the vector
     * from the key bindings, so anything written earlier in the tick is discarded. That is exactly
     * the bug that made analog movement behave digitally on the other versions.
     */
    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        Minecraft client = Minecraft.getMinecraft();
        if (client.player == null || event.getEntityPlayer() != client.player) {
            return;
        }
        runtime.applyAnalogMovement(event.getMovementInput(), event.getMovementInput().sneak);
    }

    /**
     * Menu navigation binds here rather than reading {@code buttonList} itself: Forge hands over
     * the live list, and fires again whenever a screen rebuilds its buttons - on resize, or when a
     * settings page swaps its contents. Rebinding then is what stops the selection pointing at a
     * control that no longer exists.
     */
    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        runtime.onScreenInit(event.getGui(), event.getButtonList());
    }

    /** Draws the selection border and the virtual keyboard, above whatever the screen drew. */
    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        runtime.drawScreenSelection(event.getGui());
    }

    /**
     * The connection banner and button prompts, then the F8 diagnostic panel above them.
     *
     * <p>Keyed on TEXT rather than ALL so it draws once per frame: ALL fires for the overlay pass
     * as a whole and again for each element, which would stack the panel on itself a dozen times.
     */
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.TEXT) {
            return;
        }
        Minecraft client = Minecraft.getMinecraft();
        runtime.renderHud(client, event.getResolution().getScaledWidth(), event.getResolution().getScaledHeight());
        runtime.renderDebugOverlay(client);
    }

    /** Toggles the panel. A real key binding, so it shows up in Controls and can be rebound. */
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (debugOverlayKey != null && debugOverlayKey.isPressed()) {
            runtime.toggleDebugOverlay();
        }
    }
}
