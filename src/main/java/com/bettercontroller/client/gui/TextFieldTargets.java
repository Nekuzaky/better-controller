package com.bettercontroller.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiEditSign;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the places on a screen the player can type into, so the navigators can select them.
 *
 * <p>1.12.2 keeps text fields in private fields of each screen, outside {@code buttonList}, with
 * no registry of any kind. They are found by type rather than by name - which also finds them on
 * modded screens, and needs no obfuscated names, since a field's type survives reobfuscation.
 *
 * <p>A sign has no text field at all: it edits its lines in {@code keyTyped} directly. It gets a
 * stand-in target over the sign itself.
 */
final class TextFieldTargets {

    /** Where GuiEditSign draws the sign: centred, under the title, above "Done". */
    private static final int SIGN_TARGET_WIDTH = 100;
    private static final int SIGN_TARGET_TOP = 40;
    private static final int SIGN_TARGET_HEIGHT = 50;

    private TextFieldTargets() {
    }

    /** Whether the screen takes typed text: a sign, or a screen with a focused text field. */
    static boolean isTextScreen(GuiScreen screen) {
        return screen instanceof GuiEditSign || focusedField(screen) != null;
    }

    static GuiTextField focusedField(GuiScreen screen) {
        List<GuiTextField> fields = visibleFields(screen);
        for (GuiTextField field : fields) {
            if (field.isFocused()) {
                return field;
            }
        }
        return null;
    }

    /**
     * Every visible text field on the screen, walking up the class hierarchy so a field a modded
     * screen inherits is found too.
     */
    static List<GuiTextField> visibleFields(GuiScreen screen) {
        List<GuiTextField> result = new ArrayList<GuiTextField>();
        if (screen == null) {
            return result;
        }
        for (Class<?> type = screen.getClass(); type != null && type != GuiScreen.class; type = type.getSuperclass()) {
            for (Field field : declaredFields(type)) {
                GuiTextField candidate = readField(screen, field);
                if (candidate != null && candidate.getVisible() && !result.contains(candidate)) {
                    result.add(candidate);
                }
            }
        }
        return result;
    }

    /**
     * One selectable stand-in per place to type. These are never added to the screen or drawn by
     * it; they exist so the button navigator's geometry, selection and highlight work on text
     * fields unchanged. Fields sharing one rectangle get one target - GuiCreateWorld stacks its
     * name and seed fields on the same spot and shows one or the other.
     */
    static List<GuiButton> targetsFor(GuiScreen screen) {
        List<GuiButton> targets = new ArrayList<GuiButton>();
        if (screen instanceof GuiEditSign) {
            targets.add(new GuiButton(-1, (screen.width - SIGN_TARGET_WIDTH) / 2, SIGN_TARGET_TOP,
                SIGN_TARGET_WIDTH, SIGN_TARGET_HEIGHT, ""));
            return targets;
        }
        for (GuiTextField field : visibleFields(screen)) {
            if (findTarget(targets, field) == null) {
                targets.add(new GuiButton(-1, field.x, field.y, field.width, field.height, ""));
            }
        }
        return targets;
    }

    /**
     * Focuses whatever sits under a target and unfocuses every other field, as a mouse click on
     * it would. Without the unfocusing, a screen like Add Server - which hands each key to both of
     * its fields and lets focus decide - would type into two fields at once.
     */
    static void focus(GuiScreen screen, GuiButton target) {
        for (GuiTextField field : visibleFields(screen)) {
            field.setFocused(sameRectangle(target, field));
        }
    }

    private static GuiButton findTarget(List<GuiButton> targets, GuiTextField field) {
        for (GuiButton target : targets) {
            if (sameRectangle(target, field)) {
                return target;
            }
        }
        return null;
    }

    private static boolean sameRectangle(GuiButton target, GuiTextField field) {
        return target.x == field.x && target.y == field.y
            && target.width == field.width && target.height == field.height;
    }

    private static GuiTextField readField(GuiScreen screen, Field field) {
        if (!GuiTextField.class.isAssignableFrom(field.getType())) {
            return null;
        }
        try {
            field.setAccessible(true);
            return (GuiTextField) field.get(screen);
        } catch (Exception unreadable) {
            // A field we cannot read is a field we cannot type into. Keep looking.
            return null;
        }
    }

    private static Field[] declaredFields(Class<?> type) {
        try {
            return type.getDeclaredFields();
        } catch (Exception failure) {
            return new Field[0];
        }
    }
}
