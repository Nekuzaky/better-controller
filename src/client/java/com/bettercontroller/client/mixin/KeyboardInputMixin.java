package com.bettercontroller.client.mixin;

import com.bettercontroller.client.BetterControllerClientMod;
import com.bettercontroller.client.input.ControllerRuntime;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the digital movement values with the analog ones, after vanilla has computed them.
 *
 * <p>The mod drives movement through the vanilla key bindings so that rising-edge behaviour like
 * creative double-tap fly keeps working, and then wants to overwrite the resulting magnitude with
 * the real stick deflection. Doing that from the client tick does not survive:
 * {@code KeyboardInput.tick} runs later in the same tick and recomputes both fields from those very
 * key bindings, so the stick's magnitude was always thrown away and every deflection past the
 * deadzone moved the player at full speed.
 *
 * <p>Injecting at the tail of that method is the first point where the values stick until
 * {@code ClientPlayerEntity.tickMovement} reads them. Vanilla applies its sneak slow-down at the
 * very end of the same method, so this has to re-apply it rather than inherit it.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    @Inject(method = "tick(ZF)V", at = @At("TAIL"))
    private void bettercontroller$applyAnalogMovement(boolean slowDown, float slowDownFactor, CallbackInfo callbackInfo) {
        ControllerRuntime runtime = BetterControllerClientMod.runtime();
        if (runtime != null) {
            runtime.applyAnalogMovement((Input) (Object) this, slowDown, slowDownFactor);
        }
    }
}
