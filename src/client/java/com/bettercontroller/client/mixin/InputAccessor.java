package com.bettercontroller.client.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientInput.class)
public interface InputAccessor {
    @Accessor("movementVector")
    void bettercontroller$setMovementVector(Vec2 movementVector);
}
