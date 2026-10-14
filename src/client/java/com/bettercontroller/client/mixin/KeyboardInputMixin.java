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
 * Replaces the digital movement vector with the analog one, after vanilla has computed it.
 *
 * <p>The mod drives movement through the vanilla key bindings so that rising-edge behaviour like
 * creative double-tap fly keeps working, then wants to overwrite the resulting magnitude with the
 * real stick deflection. Doing that from the client tick does not survive: {@code
 * KeyboardInput.tick} runs later in the same tick, rebuilds the vector from those very bindings and
 * normalises it, so the stick's magnitude was always thrown away and every deflection past the
 * deadzone moved the player at full speed.
 *
 * <p>The tail of that method is the first point where the value sticks until
 * {@code ClientPlayerEntity.tickMovement} reads it - and since tickMovement applies the sneak
 * slow-down afterwards, that scaling still lands on the analog value without this having to redo it.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    @Inject(method = "tick()V", at = @At("TAIL"))
    private void bettercontroller$applyAnalogMovement(CallbackInfo callbackInfo) {
        ControllerRuntime runtime = BetterControllerClientMod.runtime();
        if (runtime != null) {
            runtime.applyAnalogMovement((Input) (Object) this);
        }
    }
}
