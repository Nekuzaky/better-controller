package com.bettercontroller.client.gui;

import com.bettercontroller.client.mixin.CreativeInventoryScreenAccessor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

import java.util.List;

/**
 * Cycles between visible creative inventory tabs without reflection - uses an accessor mixin.
 */
public final class CreativeTabNavigator {

    private CreativeTabNavigator() {
    }

    public static boolean cycle(AbstractContainerScreen<?> screen, int step) {
        if (!(screen instanceof CreativeModeInventoryScreen creativeScreen) || step == 0) {
            return false;
        }
        List<CreativeModeTab> groups = CreativeModeTabs.getGroupsToDisplay();
        if (groups == null || groups.isEmpty()) {
            return false;
        }
        CreativeModeTab current = CreativeInventoryScreenAccessor.bettercontroller$getSelectedTab();
        int currentIndex = groups.indexOf(current);
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        int nextIndex = Math.floorMod(currentIndex + (step > 0 ? 1 : -1), groups.size());
        CreativeModeTab target = groups.get(nextIndex);
        if (target == null || target == current) {
            return false;
        }
        ((CreativeInventoryScreenAccessor) creativeScreen).bettercontroller$invokeSetSelectedTab(target);
        return true;
    }
}
