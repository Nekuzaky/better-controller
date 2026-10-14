package com.bettercontroller.client.mixin;

import com.bettercontroller.client.BetterControllerClientMod;
import com.bettercontroller.client.input.ControllerRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the controller camera at the point vanilla applies the mouse.
 *
 * <p>{@code MinecraftClient.render} calls {@code Mouse.tick()} and only then
 * {@code GameRenderer.render}, which is where the camera for this frame is built. Injecting at the
 * head of the latter puts controller look in the same frame slot as mouse look, instead of one
 * frame later - which is where it landed while this ran from a render event nested inside
 * {@code GameRenderer.render}, after {@code Camera.update}.
 *
 * <p>This also runs regardless of whether the HUD is visible, so hiding it with F1 cannot freeze
 * the camera.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At("HEAD"))
    private void bettercontroller$applyControllerLook(RenderTickCounter tickCounter, boolean tick, CallbackInfo callbackInfo) {
        ControllerRuntime runtime = BetterControllerClientMod.runtime();
        if (runtime != null) {
            runtime.onRenderFrame(MinecraftClient.getInstance());
        }
    }
}
