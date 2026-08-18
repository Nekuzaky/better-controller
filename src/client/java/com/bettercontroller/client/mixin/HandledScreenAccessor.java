package com.bettercontroller.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int bettercontroller$getX();

    @Accessor("y")
    int bettercontroller$getY();

    @Accessor("backgroundWidth")
    int bettercontroller$getBackgroundWidth();

    @Accessor("backgroundHeight")
    int bettercontroller$getBackgroundHeight();
}
