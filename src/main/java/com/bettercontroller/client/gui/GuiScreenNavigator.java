package com.bettercontroller.client.gui;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerAxis;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.translation.InputTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlider;
import net.minecraft.client.gui.GuiOptionSlider;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives Minecraft 1.12.2's menus from the controller.
 *
 * <p>This is the one part of the mod that could not be ported, only rebuilt. Later versions give a
 * screen a focus system, a widget tree and a keyboard navigation model to borrow; 1.12.2 gives a
 * {@code List<GuiButton>} and nothing else. There is no notion of a selected control, so the
 * selection, the highlight that shows it, and the activation all have to be supplied here.
 *
 * <p>Directional intent - thresholds, hysteresis, repeat timing - comes from {@link GuiInputRouter},
 * which is shared with every other version. Only the part that touches GuiScreen lives here.
 */
public final class GuiScreenNavigator {

    /** Selection border, matching the mod's accent on the other versions. */
    private static final int HIGHLIGHT_COLOR = 0xFFFFC66B;
    private static final int HIGHLIGHT_THICKNESS = 1;

    /** Right-stick list scrolling: the same deadzone and pacing as the Fabric versions. */
    private static final float SCROLL_STICK_DEADZONE = 0.20F;
    private static final long SCROLL_BASE_INTERVAL_MS = 110L;

    private final GuiInputRouter router = new GuiInputRouter();
    private final VirtualKeyboardOverlay keyboard;

    private GuiScreen boundScreen;
    private List<GuiButton> boundButtons = new ArrayList<GuiButton>();
    /** Stand-ins for the screen's text fields; see {@link TextFieldTargets#targetsFor}. */
    private List<GuiButton> textTargets = new ArrayList<GuiButton>();
    /** The screen's scrolling lists; see {@link SlotListTarget}. */
    private List<SlotListTarget> lists = new ArrayList<SlotListTarget>();
    private GuiButton selected;
    private boolean justOpened;
    private long lastScrollMs;

    public GuiScreenNavigator(VirtualKeyboardOverlay keyboard) {
        this.keyboard = keyboard;
    }

    /**
     * Rebinds to a screen. Forge fires this after {@code initGui}, which is also when a screen
     * rebuilds its buttons on resize - so the selection is re-resolved by identity rather than
     * kept by index, which would silently point at a different control.
     */
    public void onScreenInit(GuiScreen screen, List<GuiButton> buttons) {
        boolean sameScreen = screen == boundScreen;
        boundScreen = screen;
        boundButtons = buttons == null ? new ArrayList<GuiButton>() : buttons;
        // Rebuilt on every init, not only for a new screen: initGui creates its text fields anew.
        textTargets = TextFieldTargets.targetsFor(screen);
        lists = SlotListTarget.find(screen);
        if (!sameScreen) {
            selected = null;
            suppressNextFrame();
        } else if (selected != null && !boundButtons.contains(selected)
            && !textTargets.contains(selected) && listFor(selected) == null) {
            selected = null;
        }
    }

    public void onScreenClosed() {
        boundScreen = null;
        boundButtons = new ArrayList<GuiButton>();
        textTargets = new ArrayList<GuiButton>();
        lists = new ArrayList<SlotListTarget>();
        selected = null;
        justOpened = false;
        lastScrollMs = 0L;
        router.reset();
    }

    /**
     * Makes the next tick record what is held without acting on it.
     *
     * <p>Needed whenever this navigator takes over input it has not been watching: a new screen,
     * or the same screen after the virtual keyboard or the container navigator had it. The
     * router's state is stale then, and pulse() reads a held button with no previous state as a
     * fresh press - so the A that opened a screen clicked that screen's first button, and a B
     * held a moment too long backed out through several menus.
     */
    public void suppressNextFrame() {
        router.reset();
        justOpened = true;
    }

    public void onScreenTick(
        Minecraft client,
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout,
        InputTranslator translator
    ) {
        if (client == null || client.currentScreen == null || snapshot == null || config == null || layout == null) {
            return;
        }
        if (client.currentScreen != boundScreen) {
            // A screen opened without us seeing its init - fall back to whatever it has now.
            onScreenInit(client.currentScreen, GuiReflection.buttonList(client.currentScreen));
        }

        GuiInputFrame frame = router.route(snapshot, config, layout, translator, false);
        if (justOpened) {
            // Routing was still needed: it records what is held right now. The frame is dropped.
            justOpened = false;
            return;
        }
        scrollWithStick(snapshot, config, layout);
        if (!frame.anyNavigation()) {
            return;
        }

        List<GuiButton> candidates = selectableButtons();
        if (candidates.isEmpty()) {
            if (frame.back()) {
                pressEscape(client.currentScreen);
            }
            return;
        }
        if (selected == null || !candidates.contains(selected)) {
            selected = initialSelection(candidates);
        }
        handleFrame(client, frame, candidates);
    }

    /**
     * A screen with somewhere to type is mostly about typing, and one with a list - worlds,
     * servers - is mostly about the list, so that is where each starts.
     */
    private GuiButton initialSelection(List<GuiButton> candidates) {
        if (!textTargets.isEmpty()) {
            return textTargets.get(0);
        }
        return lists.isEmpty() ? candidates.get(0) : lists.get(0).target();
    }

    private void handleFrame(Minecraft client, GuiInputFrame frame, List<GuiButton> candidates) {
        // Sliders take left/right for their value, so those never move the selection while one is
        // highlighted - otherwise a slider could never be adjusted, only skipped past.
        boolean onSlider = isSlider(selected);
        if (onSlider && (frame.left() || frame.right())) {
            nudgeSlider(client, selected, frame.right());
            return;
        }

        // On a list, up and down step through its entries, and only leave it past either end.
        SlotListTarget list = listFor(selected);
        boolean steppedInList = list != null && (frame.up() || frame.down())
            && list.move(client.currentScreen, frame.down() ? 1 : -1);
        if (!steppedInList) {
            moveSelection(frame, candidates);
            SlotListTarget entered = listFor(selected);
            if (entered != null && entered != list) {
                entered.enter(client.currentScreen);
            }
        }

        list = listFor(selected);
        if (frame.confirm() && list != null) {
            list.activate(client, client.currentScreen);
            return;
        }
        if (frame.confirm() && textTargets.contains(selected)) {
            summonKeyboard(client, client.currentScreen, selected);
            return;
        }
        if (frame.confirm() && !onSlider) {
            // Sliders are excluded deliberately. Confirming one would activate it as a button, and
            // on the other versions that snapped the value to wherever the synthetic click landed.
            // Nothing else runs after it: the button may well have replaced the screen, and a B in
            // the same frame would then back out of the new one.
            activate(client, client.currentScreen, selected);
            return;
        }
        if (frame.back()) {
            pressEscape(client.currentScreen);
        }
    }

    private void moveSelection(GuiInputFrame frame, List<GuiButton> candidates) {
        if (frame.up()) {
            selected = nearestInDirection(candidates, selected, 0, -1);
        } else if (frame.down()) {
            selected = nearestInDirection(candidates, selected, 0, 1);
        } else if (frame.left()) {
            selected = nearestInDirection(candidates, selected, -1, 0);
        } else if (frame.right()) {
            selected = nearestInDirection(candidates, selected, 1, 0);
        }
    }

    /** Draws the selection border. Called from DrawScreenEvent.Post so it sits above the button. */
    public void drawSelection(GuiScreen screen) {
        if (screen == null || screen != boundScreen || selected == null || !selected.visible) {
            return;
        }
        SlotListTarget list = listFor(selected);
        if (list != null) {
            list.drawSelection();
            return;
        }
        int left = selected.x - HIGHLIGHT_THICKNESS;
        int top = selected.y - HIGHLIGHT_THICKNESS;
        int right = selected.x + selected.width + HIGHLIGHT_THICKNESS;
        int bottom = selected.y + selected.height + HIGHLIGHT_THICKNESS;

        Gui.drawRect(left, top, right, top + HIGHLIGHT_THICKNESS, HIGHLIGHT_COLOR);
        Gui.drawRect(left, bottom - HIGHLIGHT_THICKNESS, right, bottom, HIGHLIGHT_COLOR);
        Gui.drawRect(left, top, left + HIGHLIGHT_THICKNESS, bottom, HIGHLIGHT_COLOR);
        Gui.drawRect(right - HIGHLIGHT_THICKNESS, top, right, bottom, HIGHLIGHT_COLOR);
    }

    private List<GuiButton> selectableButtons() {
        List<GuiButton> result = new ArrayList<GuiButton>(textTargets);
        for (int i = 0; i < lists.size(); i++) {
            lists.get(i).syncTarget();
            result.add(lists.get(i).target());
        }
        for (GuiButton button : boundButtons) {
            if (button != null && button.visible && button.enabled) {
                result.add(button);
            }
        }
        return result;
    }

    /** The list a stand-in belongs to, or null. Indexed, not for-each: this runs every tick. */
    private SlotListTarget listFor(GuiButton button) {
        for (int i = 0; i < lists.size(); i++) {
            if (lists.get(i).target() == button) {
                return lists.get(i);
            }
        }
        return null;
    }

    /**
     * Scrolls a list with the right stick, a mouse-wheel notch at a time, faster the further it is
     * pushed. The selected list if there is one, otherwise the screen's first.
     */
    private void scrollWithStick(ControllerSnapshot snapshot, ControllerConfig config, ControllerConfig.ResolvedLayout layout) {
        SlotListTarget list = listFor(selected);
        if (list == null && !lists.isEmpty()) {
            list = lists.get(0);
        }
        float value = list == null ? 0.0F : readScrollAxis(snapshot, config, layout);
        float magnitude = Math.abs(value);
        if (magnitude < SCROLL_STICK_DEADZONE) {
            lastScrollMs = 0L;
            return;
        }
        long now = System.currentTimeMillis();
        long interval = (long) (SCROLL_BASE_INTERVAL_MS / Math.max(0.25F, magnitude));
        if (now - lastScrollMs < interval) {
            return;
        }
        lastScrollMs = now;
        // Stick up reads negative, and a negative scroll moves the content back towards the top.
        list.scrollNotches(value < 0.0F ? -1 : 1);
    }

    /** The look stick's vertical axis, honouring its binding and the invert setting as Fabric does. */
    private static float readScrollAxis(ControllerSnapshot snapshot, ControllerConfig config, ControllerConfig.ResolvedLayout layout) {
        String token = layout.axisToken("look_y");
        if (token == null || token.trim().isEmpty()) {
            return 0.0F;
        }
        boolean invert = token.startsWith("-");
        ControllerAxis axis = ControllerAxis.fromTokenOrNull(invert ? token.substring(1) : token);
        if (axis == null) {
            return 0.0F;
        }
        float value = snapshot.axis(axis);
        if (invert) {
            value = -value;
        }
        return config.invertLookY ? -value : value;
    }

    /**
     * A on a text field: focus it, as a click would, and ask for the keyboard. The keyboard opens
     * on the next tick and discards its first frame, so the A that summoned it types nothing.
     */
    private void summonKeyboard(Minecraft client, GuiScreen screen, GuiButton target) {
        target.playPressSound(client.getSoundHandler());
        TextFieldTargets.focus(screen, target);
        keyboard.requestOpen();
    }

    /**
     * Picks the next control in a direction.
     *
     * <p>Scores on distance along the axis of travel plus a penalty for drifting off it, so a
     * two-column layout steps down its own column rather than hopping across. Nothing in the
     * direction means the selection stays put - menus here are small and wrapping tends to feel
     * like a misfire rather than a convenience.
     */
    private static GuiButton nearestInDirection(List<GuiButton> buttons, GuiButton from, int dx, int dy) {
        int fromX = from.x + (from.width / 2);
        int fromY = from.y + (from.height / 2);

        GuiButton best = null;
        int bestScore = Integer.MAX_VALUE;
        for (GuiButton candidate : buttons) {
            if (candidate == from) {
                continue;
            }
            int cx = candidate.x + (candidate.width / 2);
            int cy = candidate.y + (candidate.height / 2);
            int along = ((cx - fromX) * dx) + ((cy - fromY) * dy);
            if (along <= 0) {
                continue;
            }
            int across = Math.abs(((cx - fromX) * dy) + ((cy - fromY) * dx));
            int score = along + (across * 3);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best == null ? from : best;
    }

    private static boolean isSlider(GuiButton button) {
        return button instanceof GuiSlider || button instanceof GuiOptionSlider;
    }

    /** One press of left or right moves a slider this much of its range. */
    private static final float SLIDER_STEP = 0.05F;

    /**
     * Moves a slider by one step.
     *
     * <p>1.12.2 sliders have no keyboard handling whatsoever, so the only way in is the mouse path.
     * Both slider classes derive their value in {@code mousePressed} as
     * {@code (mouseX - (x + 4)) / (width - 8)} and apply the setting there, so this reads the
     * current value, adds a step, and synthesises the click that would have produced it. Going
     * through {@code mousePressed} rather than writing the field is what makes the setting actually
     * take effect and the label update.
     */
    private static void nudgeSlider(Minecraft client, GuiButton slider, boolean increase) {
        float current = sliderValue(slider);
        float target = clamp01(current + (increase ? SLIDER_STEP : -SLIDER_STEP));
        if (target == current) {
            return;
        }

        int track = slider.width - 8;
        int targetX = slider.x + 4 + Math.round(target * track);
        int centreY = slider.y + (slider.height / 2);
        slider.mousePressed(client, targetX, centreY);
        slider.mouseReleased(targetX, centreY);
    }

    /**
     * The slider's current position, 0 to 1. GuiSlider exposes it; GuiOptionSlider keeps it
     * private, and there is no other way to ask.
     */
    private static float sliderValue(GuiButton slider) {
        if (slider instanceof GuiSlider) {
            return ((GuiSlider) slider).getSliderPosition();
        }
        return GuiReflection.optionSliderValue((GuiOptionSlider) slider, 0.5F);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static void activate(Minecraft client, GuiScreen screen, GuiButton button) {
        if (screen == null || button == null) {
            return;
        }
        button.playPressSound(client.getSoundHandler());
        GuiReflection.actionPerformed(screen, button);
    }

    /**
     * Closes the current screen the same way the keyboard does.
     *
     * <p>Calling {@code displayGuiScreen(null)} instead would skip whatever the screen does on
     * escape - a nested settings screen would close the whole stack rather than going back one.
     * 1 is Keyboard.KEY_ESCAPE. LWJGL 2's Keyboard is not referenced by name anywhere in this
     * mod, so the constant is written out.
     */
    private static void pressEscape(GuiScreen screen) {
        GuiReflection.keyTyped(screen, '\0', 1);
    }
}
