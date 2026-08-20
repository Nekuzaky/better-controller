package com.bettercontroller.client.gui;

import com.bettercontroller.BetterControllerMod;
import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.translation.InputTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An on-screen keyboard for typing with the controller.
 *
 * <p>On later versions this is a Screen of its own that wraps the one underneath and writes into
 * its EditBox. That does not port: it leans on widgets, focus and a layout system 1.12.2 has none
 * of. Here it is an overlay drawn on top of whatever text screen is already open, and characters
 * are delivered through that screen's own {@code keyTyped} - the same door the physical keyboard
 * uses.
 *
 * <p>Going through keyTyped rather than writing into the text field directly is what makes this
 * work everywhere without knowing anything about the screen. Chat gets its command completion and
 * its history, a sign gets its line handling, an anvil renames the item. A field-level write would
 * bypass all of it.
 */
public final class VirtualKeyboardOverlay {

    // LWJGL 2 key codes. Named here rather than imported, so no part of the mod links against
    // LWJGL 2 either - the isolation in GlfwBridge cuts both ways.
    private static final int KEY_BACKSPACE = 14;
    private static final int KEY_RETURN = 28;

    private static final String[][] ROWS = {
        {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
        {"Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"},
        {"A", "S", "D", "F", "G", "H", "J", "K", "L"},
        {"Z", "X", "C", "V", "B", "N", "M"},
        {"_", "-", ".", ",", "/", ":", "!", "?", "@", "#"},
    };

    private static final int KEY_SIZE = 16;
    private static final int KEY_GAP = 2;
    private static final int PANEL_PADDING = 6;
    private static final int BOTTOM_MARGIN = 34;

    private static final int PANEL_BACKGROUND = 0xD0100C08;
    private static final int KEY_BACKGROUND = 0x60FFFFFF;
    private static final int KEY_SELECTED = 0xFFFFC66B;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_SELECTED = 0xFF201810;

    private final GuiInputRouter router = new GuiInputRouter();

    private GuiScreen boundScreen;
    private boolean uppercase;
    private int row;
    private int column;

    private static Method keyTyped;
    private static boolean reflectionWarned;

    public boolean isOpenFor(GuiScreen screen) {
        return screen != null && screen == boundScreen;
    }

    /**
     * Whether this screen is one worth typing into. Rather than listing screen classes - which
     * would miss every modded one - this looks for a focused text field, which is what actually
     * matters.
     */
    public static boolean isTextScreen(GuiScreen screen) {
        return findFocusedTextField(screen) != null;
    }

    public void openFor(GuiScreen screen) {
        boundScreen = screen;
        uppercase = false;
        row = 1;
        column = 0;
        router.reset();
    }

    public void close() {
        boundScreen = null;
        router.reset();
    }

    /**
     * @return true when the overlay consumed this tick's input, so menu navigation should not also
     *         act on it.
     */
    public boolean onScreenTick(
        Minecraft client,
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout,
        InputTranslator translator
    ) {
        if (client == null || client.currentScreen == null) {
            close();
            return false;
        }
        if (config == null || !config.virtualKeyboardEnabled) {
            close();
            return false;
        }

        if (boundScreen != client.currentScreen) {
            if (!isTextScreen(client.currentScreen)) {
                close();
                return false;
            }
            openFor(client.currentScreen);
        }

        GuiInputFrame frame = router.route(snapshot, config, layout, translator, true);
        if (!frame.anyNavigation()) {
            return true;
        }

        if (frame.up()) {
            moveRow(-1);
        } else if (frame.down()) {
            moveRow(1);
        } else if (frame.left()) {
            moveColumn(-1);
        } else if (frame.right()) {
            moveColumn(1);
        }

        if (frame.confirm()) {
            typeCharacter(client.currentScreen, currentKey());
        }
        // The shoulder buttons are the modifiers: one edits, one shifts. Confirm stays a single
        // meaning - typing - so the keyboard never needs a chorded press.
        if (frame.pagePrev()) {
            sendKey(client.currentScreen, '\b', KEY_BACKSPACE);
        }
        if (frame.pageNext()) {
            typeCharacter(client.currentScreen, " ");
        }
        if (frame.tabNext() || frame.tabPrev()) {
            uppercase = !uppercase;
        }
        if (frame.back()) {
            // Submit rather than dismiss: the player is in a text field, and abandoning what they
            // just typed is the more expensive mistake to make by accident.
            sendKey(client.currentScreen, '\r', KEY_RETURN);
            close();
        }
        return true;
    }

    /** Draws the keyboard. Called from DrawScreenEvent.Post, so it sits above the screen. */
    public void draw(Minecraft client, GuiScreen screen) {
        if (client == null || screen == null || screen != boundScreen) {
            return;
        }

        int panelWidth = (widestRow() * (KEY_SIZE + KEY_GAP)) - KEY_GAP + (PANEL_PADDING * 2);
        int panelHeight = (ROWS.length * (KEY_SIZE + KEY_GAP)) - KEY_GAP + (PANEL_PADDING * 2) + 12;
        int panelLeft = (screen.width - panelWidth) / 2;
        int panelTop = screen.height - panelHeight - BOTTOM_MARGIN;

        Gui.drawRect(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, PANEL_BACKGROUND);

        for (int r = 0; r < ROWS.length; r++) {
            int rowWidth = (ROWS[r].length * (KEY_SIZE + KEY_GAP)) - KEY_GAP;
            int rowLeft = panelLeft + ((panelWidth - rowWidth) / 2);
            int keyTop = panelTop + PANEL_PADDING + (r * (KEY_SIZE + KEY_GAP));

            for (int c = 0; c < ROWS[r].length; c++) {
                int keyLeft = rowLeft + (c * (KEY_SIZE + KEY_GAP));
                boolean isSelected = (r == row && c == column);
                Gui.drawRect(
                    keyLeft, keyTop, keyLeft + KEY_SIZE, keyTop + KEY_SIZE,
                    isSelected ? KEY_SELECTED : KEY_BACKGROUND
                );

                String label = applyCase(ROWS[r][c]);
                int labelWidth = client.fontRenderer.getStringWidth(label);
                client.fontRenderer.drawString(
                    label,
                    keyLeft + ((KEY_SIZE - labelWidth) / 2),
                    keyTop + ((KEY_SIZE - 8) / 2),
                    isSelected ? TEXT_SELECTED : TEXT_COLOR
                );
            }
        }

        String hint = "A type   LB back   RB space   LT/RT " + (uppercase ? "lower" : "UPPER") + "   B send";
        int hintWidth = client.fontRenderer.getStringWidth(hint);
        client.fontRenderer.drawString(
            hint,
            panelLeft + ((panelWidth - hintWidth) / 2),
            panelTop + panelHeight - PANEL_PADDING - 8,
            0xFFAAAAAA
        );
    }

    private String currentKey() {
        return applyCase(ROWS[row][column]);
    }

    private String applyCase(String key) {
        if (key.length() != 1 || !Character.isLetter(key.charAt(0))) {
            return key;
        }
        return uppercase ? key.toUpperCase(Locale.ROOT) : key.toLowerCase(Locale.ROOT);
    }

    /**
     * Moves between rows, keeping the cursor roughly under where it was. Rows are different
     * lengths, so clamping alone would drift the cursor leftwards every time it passed a short row.
     */
    private void moveRow(int delta) {
        int previousLength = ROWS[row].length;
        int nextRow = clamp(row + delta, 0, ROWS.length - 1);
        if (nextRow == row) {
            return;
        }
        float ratio = previousLength <= 1 ? 0.0F : (float) column / (previousLength - 1);
        row = nextRow;
        column = Math.round(ratio * (ROWS[row].length - 1));
    }

    private void moveColumn(int delta) {
        column = clamp(column + delta, 0, ROWS[row].length - 1);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int widestRow() {
        int widest = 0;
        for (String[] keys : ROWS) {
            widest = Math.max(widest, keys.length);
        }
        return widest;
    }

    private static void typeCharacter(GuiScreen screen, String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        // Key code 0 so nothing downstream mistakes this for a control key; GuiTextField falls
        // through to its "is this an allowed character" path and writes it.
        sendKey(screen, key.charAt(0), 0);
    }

    private static void sendKey(GuiScreen screen, char character, int keyCode) {
        Method method = keyTypedMethod();
        if (screen == null || method == null) {
            return;
        }
        try {
            method.invoke(screen, Character.valueOf(character), Integer.valueOf(keyCode));
        } catch (Exception failure) {
            warnOnce(failure);
        }
    }

    private static Method keyTypedMethod() {
        if (keyTyped == null) {
            try {
                keyTyped = GuiScreen.class.getDeclaredMethod("keyTyped", char.class, int.class);
                keyTyped.setAccessible(true);
            } catch (Exception failure) {
                warnOnce(failure);
            }
        }
        return keyTyped;
    }

    /**
     * Finds the focused text field on a screen, walking up the class hierarchy so a modded screen
     * that inherits its field is found too. Returns null when the screen has none, which is how
     * "should the keyboard appear" gets answered.
     */
    private static GuiTextField findFocusedTextField(GuiScreen screen) {
        if (screen == null) {
            return null;
        }
        for (Class<?> type = screen.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : declaredFields(type)) {
                if (!GuiTextField.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    GuiTextField candidate = (GuiTextField) field.get(screen);
                    if (candidate != null && candidate.getVisible() && candidate.isFocused()) {
                        return candidate;
                    }
                } catch (Exception ignored) {
                    // A field we cannot read is a field we cannot type into. Keep looking.
                }
            }
        }
        return null;
    }

    private static List<Field> declaredFields(Class<?> type) {
        try {
            List<Field> fields = new ArrayList<Field>();
            for (Field field : type.getDeclaredFields()) {
                fields.add(field);
            }
            return fields;
        } catch (Exception failure) {
            return new ArrayList<Field>();
        }
    }

    private static void warnOnce(Exception failure) {
        if (reflectionWarned) {
            return;
        }
        reflectionWarned = true;
        BetterControllerMod.logger().warn("BetterController: the virtual keyboard could not type.", failure);
    }
}
