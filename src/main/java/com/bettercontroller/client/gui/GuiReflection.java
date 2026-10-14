package com.bettercontroller.client.gui;

import com.bettercontroller.BetterControllerMod;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * The protected members of vanilla GUI classes that 1.12.2 leaves no public way to reach.
 *
 * <p>Every lookup names the member twice. The MCP name is what the development workspace runs
 * with; the SRG name is what every real installation runs with, because Forge deobfuscates the
 * game to SRG at runtime and reobfuscation only rewrites bytecode references, never the strings
 * handed to reflection. Looking up the MCP name alone works in {@code runClient} and fails on
 * every player's machine - which is exactly what this code used to do.
 *
 * <p>A member that cannot be found costs that one feature and one log line, never the game.
 */
final class GuiReflection {

    private static boolean invokeWarned;

    private static final Method ACTION_PERFORMED = findMethod(
        GuiScreen.class, "actionPerformed", "func_146284_a", GuiButton.class);
    private static final Method KEY_TYPED = findMethod(
        GuiScreen.class, "keyTyped", "func_73869_a", char.class, int.class);
    private static final Field BUTTON_LIST = findField(
        GuiScreen.class, "buttonList", "field_146292_n");
    private static final Field OPTION_SLIDER_VALUE = findField(
        GuiOptionSlider.class, "sliderValue", "field_146134_p");

    private GuiReflection() {
    }

    /** Runs the screen's handler for a button, as a mouse click on it would. */
    static boolean actionPerformed(GuiScreen screen, GuiButton button) {
        return invoke(ACTION_PERFORMED, screen, button);
    }

    /** Delivers a key through the screen's own keyboard path. */
    static boolean keyTyped(GuiScreen screen, char character, int keyCode) {
        return invoke(KEY_TYPED, screen, Character.valueOf(character), Integer.valueOf(keyCode));
    }

    /** The screen's live button list, or an empty one when it cannot be read. */
    @SuppressWarnings("unchecked")
    static List<GuiButton> buttonList(GuiScreen screen) {
        if (BUTTON_LIST == null || screen == null) {
            return new ArrayList<GuiButton>();
        }
        try {
            List<GuiButton> buttons = (List<GuiButton>) BUTTON_LIST.get(screen);
            return buttons == null ? new ArrayList<GuiButton>() : buttons;
        } catch (Exception failure) {
            warnInvokeOnce("read a screen's button list", failure);
            return new ArrayList<GuiButton>();
        }
    }

    /** A vanilla option slider's position, 0 to 1, or {@code fallback} when it cannot be read. */
    static float optionSliderValue(GuiOptionSlider slider, float fallback) {
        if (OPTION_SLIDER_VALUE == null || slider == null) {
            return fallback;
        }
        try {
            return OPTION_SLIDER_VALUE.getFloat(slider);
        } catch (Exception failure) {
            warnInvokeOnce("read a slider's value", failure);
            return fallback;
        }
    }

    private static boolean invoke(Method method, GuiScreen screen, Object... arguments) {
        if (method == null || screen == null) {
            return false;
        }
        try {
            method.invoke(screen, arguments);
            return true;
        } catch (Exception failure) {
            warnInvokeOnce("call GuiScreen." + method.getName(), failure);
            return false;
        }
    }

    private static Method findMethod(Class<?> owner, String mcpName, String srgName, Class<?>... parameters) {
        try {
            return ReflectionHelper.findMethod(owner, mcpName, srgName, parameters);
        } catch (Exception failure) {
            warnLookup(owner.getSimpleName() + "." + mcpName, failure);
            return null;
        }
    }

    private static Field findField(Class<?> owner, String mcpName, String srgName) {
        try {
            return ReflectionHelper.findField(owner, mcpName, srgName);
        } catch (Exception failure) {
            warnLookup(owner.getSimpleName() + "." + mcpName, failure);
            return null;
        }
    }

    /** Once per member, at class load: each one names a different feature that is now off. */
    private static void warnLookup(String member, Exception failure) {
        BetterControllerMod.logger().warn(
            "BetterController: could not find " + member + "; the controller feature using it is off.", failure);
    }

    /** Once in total: a failing call would otherwise log on every press. */
    private static void warnInvokeOnce(String what, Exception failure) {
        if (invokeWarned) {
            return;
        }
        invokeWarned = true;
        BetterControllerMod.logger().warn("BetterController: could not " + what + ".", failure);
    }
}
