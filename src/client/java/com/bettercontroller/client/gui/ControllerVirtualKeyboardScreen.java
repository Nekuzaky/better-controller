package com.bettercontroller.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller-first virtual keyboard with cursor-aware editing.
 */
public final class ControllerVirtualKeyboardScreen extends Screen {
    private static final int KEY_HEIGHT = 20;
    private static final int KEY_GAP = 4;
    private static final int MAX_TEXT_LENGTH = 256;
    private static final int PREVIEW_MAX_CHARS = 64;

    private final Screen parentScreen;
    private final EditBox originalTargetField;
    private final List<CharacterKey> characterKeys = new ArrayList<>();
    private String workingText;
    private int cursorIndex;
    private boolean upperCase = true;
    private boolean applyOnClose;
    private boolean sendOnClose;

    private Button caseToggleButton;
    private AbstractWidget lastFocusedWidget;
    private long focusChangedMs;

    public ControllerVirtualKeyboardScreen(Screen parentScreen, EditBox targetField) {
        super(Component.translatable("bettercontroller.screen.virtual_keyboard.title"));
        this.parentScreen = parentScreen;
        this.originalTargetField = targetField;
        this.workingText = targetField == null ? "" : targetField.getText();
        this.cursorIndex = this.workingText.length();
    }

    @Override
    protected void init() {
        characterKeys.clear();
        lastFocusedWidget = null;
        focusChangedMs = System.currentTimeMillis();

        int top = 58;
        top = addCharacterRow(new String[] {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"}, top);
        top = addCharacterRow(new String[] {"Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"}, top);
        top = addCharacterRow(new String[] {"A", "S", "D", "F", "G", "H", "J", "K", "L"}, top);
        top = addCharacterRow(new String[] {"Z", "X", "C", "V", "B", "N", "M"}, top);

        int actionY = top + 4;
        int actionWidth = 86;
        int actionGap = 6;
        int actionX = (this.width - ((actionWidth * 4) + (actionGap * 3))) / 2;

        caseToggleButton = addDrawableChild(Button.builder(
            Component.literal(caseLabel()),
            button -> {
                upperCase = !upperCase;
                refreshCharacterLabels();
            }
        ).bounds(actionX, actionY, actionWidth, KEY_HEIGHT).build());

        addDrawableChild(Button.builder(
            Component.translatable("bettercontroller.screen.virtual_keyboard.backspace"),
            button -> backspace()
        ).bounds(actionX + (actionWidth + actionGap), actionY, actionWidth, KEY_HEIGHT).build());

        addDrawableChild(Button.builder(
            Component.translatable("bettercontroller.screen.virtual_keyboard.delete"),
            button -> deleteForward()
        ).bounds(actionX + ((actionWidth + actionGap) * 2), actionY, actionWidth, KEY_HEIGHT).build());

        addDrawableChild(Button.builder(
            Component.translatable("bettercontroller.screen.virtual_keyboard.space"),
            button -> insertText(" ")
        ).bounds(actionX + ((actionWidth + actionGap) * 3), actionY, actionWidth, KEY_HEIGHT).build());

        int bottomY = actionY + KEY_HEIGHT + 8;
        int bottomWidth = 96;
        int bottomGap = 8;
        int bottomX = (this.width - ((bottomWidth * 4) + (bottomGap * 3))) / 2;

        addDrawableChild(Button.builder(
            Component.translatable("bettercontroller.screen.virtual_keyboard.cursor_left"),
            button -> moveCursor(-1)
        ).bounds(bottomX, bottomY, bottomWidth, KEY_HEIGHT).build());

        addDrawableChild(Button.builder(
            Component.translatable("bettercontroller.screen.virtual_keyboard.cursor_right"),
            button -> moveCursor(1)
        ).bounds(bottomX + (bottomWidth + bottomGap), bottomY, bottomWidth, KEY_HEIGHT).build());

        addDrawableChild(Button.builder(
            Component.translatable("bettercontroller.screen.virtual_keyboard.cancel"),
            button -> {
                applyOnClose = false;
                sendOnClose = false;
                close();
            }
        ).bounds(bottomX + ((bottomWidth + bottomGap) * 2), bottomY, bottomWidth, KEY_HEIGHT).build());

        addDrawableChild(Button.builder(
            Component.translatable("bettercontroller.screen.virtual_keyboard.enter"),
            button -> {
                applyOnClose = true;
                sendOnClose = true;
                close();
            }
        ).bounds(bottomX + ((bottomWidth + bottomGap) * 3), bottomY, bottomWidth, KEY_HEIGHT).build());

        if (!characterKeys.isEmpty()) {
            Button first = characterKeys.get(0).button();
            setFocused(first);
            first.setFocused(true);
            lastFocusedWidget = first;
        }
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xD010131A);
        context.fill(0, 0, this.width, 30, 0xAA1D2633);
        renderKeyboardPanels(context);
        super.render(context, mouseX, mouseY, delta);

        trackFocusChange();

        int centerX = this.width / 2;
        context.drawCenteredTextWithShadow(this.font, this.title, centerX, 10, 0xFFFFFFFF);

        String preview = buildPreviewWithCursor();
        context.fill(centerX - 230, 30, centerX + 230, 44, 0xB5000000);
        context.drawCenteredTextWithShadow(this.font, preview, centerX, 33, 0xFFE8EEF7);
        context.drawCenteredTextWithShadow(
            this.font,
            Component.translatable("bettercontroller.screen.virtual_keyboard.cursor_pos", cursorIndex, workingText.length()),
            centerX,
            46,
            0xFF9FB3C9
        );

        renderFocusedKeyHighlight(context);
        renderFocusedKeyDetails(context, centerX);

        context.drawCenteredTextWithShadow(
            this.font,
            Component.translatable("bettercontroller.screen.virtual_keyboard.help"),
            centerX,
            this.height - 18,
            0xFFB6C2D2
        );
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            applyOnClose = false;
            sendOnClose = false;
            close();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
            backspace();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_DELETE) {
            deleteForward();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_LEFT) {
            moveCursor(-1);
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_RIGHT) {
            moveCursor(1);
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            applyOnClose = true;
            sendOnClose = true;
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        if (this.client == null) {
            return;
        }

        this.client.setScreen(parentScreen);
        if (!applyOnClose || parentScreen == null) {
            return;
        }

        EditBox target = resolveTargetField(parentScreen);
        if (target == null) {
            return;
        }
        target.setText(workingText);
        target.setFocused(true);
        if (sendOnClose && parentScreen instanceof ChatScreen) {
            submitChatMessage();
        }
    }

    private int addCharacterRow(String[] row, int y) {
        int keyWidth = 24;
        int totalWidth = (row.length * keyWidth) + ((row.length - 1) * KEY_GAP);
        int x = (this.width - totalWidth) / 2;
        for (String key : row) {
            Button button = addDrawableChild(Button.builder(
                Component.literal(displayKey(key)),
                widget -> insertText(displayKey(key))
            ).bounds(x, y, keyWidth, KEY_HEIGHT).build());
            characterKeys.add(new CharacterKey(key, button));
            x += keyWidth + KEY_GAP;
        }
        return y + KEY_HEIGHT + KEY_GAP;
    }

    private void insertText(String token) {
        if (token == null || token.isEmpty() || workingText.length() >= MAX_TEXT_LENGTH) {
            return;
        }
        String left = workingText.substring(0, cursorIndex);
        String right = workingText.substring(cursorIndex);
        workingText = left + token + right;
        cursorIndex = Math.min(workingText.length(), cursorIndex + token.length());
    }

    private void backspace() {
        if (workingText.isEmpty() || cursorIndex <= 0) {
            return;
        }
        String left = workingText.substring(0, cursorIndex - 1);
        String right = workingText.substring(cursorIndex);
        workingText = left + right;
        cursorIndex = Math.max(0, cursorIndex - 1);
    }

    private void deleteForward() {
        if (workingText.isEmpty() || cursorIndex >= workingText.length()) {
            return;
        }
        String left = workingText.substring(0, cursorIndex);
        String right = workingText.substring(cursorIndex + 1);
        workingText = left + right;
    }

    private void moveCursor(int delta) {
        if (delta == 0) {
            return;
        }
        cursorIndex = Math.max(0, Math.min(workingText.length(), cursorIndex + delta));
    }

    private void refreshCharacterLabels() {
        for (CharacterKey key : characterKeys) {
            key.button().setMessage(Component.literal(displayKey(key.baseToken())));
        }
        if (caseToggleButton != null) {
            caseToggleButton.setMessage(Component.literal(caseLabel()));
        }
    }

    private String displayKey(String key) {
        if (key == null || key.length() != 1 || !Character.isLetter(key.charAt(0))) {
            return key;
        }
        return upperCase ? key.toUpperCase() : key.toLowerCase();
    }

    private String caseLabel() {
        return Component.translatable(
            upperCase
                ? "bettercontroller.screen.virtual_keyboard.lowercase"
                : "bettercontroller.screen.virtual_keyboard.uppercase"
        ).getString();
    }

    private String buildPreviewWithCursor() {
        String withCursor = workingText.substring(0, cursorIndex) + "|" + workingText.substring(cursorIndex);
        if (withCursor.length() <= PREVIEW_MAX_CHARS) {
            return withCursor.isBlank() ? "|" : withCursor;
        }

        int windowHalf = PREVIEW_MAX_CHARS / 2;
        int start = Math.max(0, cursorIndex - windowHalf);
        int end = Math.min(withCursor.length(), start + PREVIEW_MAX_CHARS);
        start = Math.max(0, end - PREVIEW_MAX_CHARS);
        String clipped = withCursor.substring(start, end);
        if (start > 0) {
            clipped = "." + clipped.substring(1);
        }
        if (end < withCursor.length()) {
            clipped = clipped.substring(0, clipped.length() - 1) + ".";
        }
        return clipped;
    }

    private void renderFocusedKeyHighlight(GuiGraphicsExtractor context) {
        AbstractWidget focusedWidget = focusedWidget();
        if (focusedWidget == null) {
            return;
        }

        int left = focusedWidget.getX() - 2;
        int top = focusedWidget.getY() - 2;
        int width = focusedWidget.getWidth() + 4;
        int height = focusedWidget.getHeight() + 4;
        long now = System.currentTimeMillis();
        double pulse = 0.5D + (0.5D * Math.sin(now * 0.016D));
        double settle = Math.min(1.0D, Math.max(0.0D, (now - focusChangedMs) / 180.0D));
        int grow = (int) Math.round((1.0D - settle) * 1.4D);
        int alpha = (int) (158 + (pulse * 82));
        int glowAlpha = (int) (50 + (pulse * 25));

        context.fill(left - 2, top - 2, left + width + 2, top + height + 2, (glowAlpha << 24) | 0x6FCFFF);
        context.drawStrokedRectangle(
            left - grow,
            top - grow,
            width + (grow * 2),
            height + (grow * 2),
            (alpha << 24) | 0xE7F8FF
        );
    }

    private void renderKeyboardPanels(GuiGraphicsExtractor context) {
        int centerX = this.width / 2;
        context.fill(centerX - 248, 52, centerX + 248, 156, 0x3A0E1622);
        context.fill(centerX - 248, 52, centerX + 248, 53, 0x66587A9B);
        context.fill(centerX - 248, 159, centerX + 248, 183, 0x34131C29);
        context.fill(centerX - 248, 187, centerX + 248, 214, 0x30131C29);
    }

    private void renderFocusedKeyDetails(GuiGraphicsExtractor context, int centerX) {
        AbstractWidget focusedWidget = focusedWidget();
        if (focusedWidget == null) {
            return;
        }
        String focusedLabel = focusedWidget.getMessage().getString();
        if (focusedLabel == null || focusedLabel.isBlank()) {
            return;
        }
        context.drawCenteredTextWithShadow(
            this.font,
            Component.translatable("bettercontroller.screen.virtual_keyboard.selected_key", focusedLabel),
            centerX,
            this.height - 30,
            0xFFD6E4F4
        );
    }

    private void trackFocusChange() {
        AbstractWidget currentFocused = focusedWidget();
        if (currentFocused != lastFocusedWidget) {
            lastFocusedWidget = currentFocused;
            focusChangedMs = System.currentTimeMillis();
        }
    }

    private AbstractWidget focusedWidget() {
        return this.getFocused() instanceof AbstractWidget widget ? widget : null;
    }

    private EditBox resolveTargetField(Screen parent) {
        if (parent == null) {
            return null;
        }
        if (parent.getFocused() instanceof EditBox focusedField) {
            return focusedField;
        }

        EditBox firstField = null;
        for (GuiEventListener element : parent.children()) {
            if (element instanceof EditBox textField) {
                if (firstField == null) {
                    firstField = textField;
                }
                if (isSameGeometry(textField, originalTargetField)) {
                    return textField;
                }
            }
        }
        return firstField;
    }

    private static boolean isSameGeometry(EditBox a, EditBox b) {
        if (a == null || b == null) {
            return false;
        }
        return a.getX() == b.getX()
            && a.getY() == b.getY()
            && a.getWidth() == b.getWidth()
            && a.getHeight() == b.getHeight();
    }

    private void submitChatMessage() {
        if (client == null || client.player == null || client.player.connection == null) {
            return;
        }

        String message = workingText == null ? "" : workingText.trim();
        if (message.isEmpty()) {
            client.setScreen(null);
            return;
        }

        if (message.startsWith("/")) {
            String command = message.substring(1).trim();
            if (!command.isEmpty()) {
                client.player.connection.sendChatCommand(command);
            }
        } else {
            client.player.connection.sendChatMessage(message);
        }
        client.setScreen(null);
    }

    private record CharacterKey(String baseToken, Button button) {
    }
}
