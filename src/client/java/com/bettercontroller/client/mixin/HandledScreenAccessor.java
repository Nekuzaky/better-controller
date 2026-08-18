package com.bettercontroller.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface HandledScreenAccessor {
    @Accessor("leftPos")
    int bettercontroller$getX();

    @Accessor("topPos")
    int bettercontroller$getY();

    @Accessor("imageWidth")
    int bettercontroller$getBackgroundWidth();

    @Accessor("imageHeight")
    int bettercontroller$getBackgroundHeight();
}
