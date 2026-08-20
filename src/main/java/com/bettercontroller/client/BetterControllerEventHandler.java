package com.bettercontroller.client;

import com.bettercontroller.client.input.ControllerRuntime;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Where the mod meets the game.
 *
 * <p>The Fabric versions need mixins for all three of these hooks. Forge 1.12.2 already publishes
 * every one of them as an event, so this port has no mixins at all - which also means nothing here
 * can conflict with another mod's injection, a real concern on a version whose big packs run
 * hundreds of mods.
 */
@SideOnly(Side.CLIENT)
public final class BetterControllerEventHandler {

    private final ControllerRuntime runtime;

    public BetterControllerEventHandler(ControllerRuntime runtime) {
        this.runtime = runtime;
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
}
