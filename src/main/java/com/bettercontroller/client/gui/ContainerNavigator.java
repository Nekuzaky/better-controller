package com.bettercontroller.client.gui;

import com.bettercontroller.BetterControllerMod;
import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.translation.InputTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives inventories, chests and every other container screen.
 *
 * <p>Separate from {@link GuiScreenNavigator} because slots are not buttons - not even distantly.
 * A container's contents live in {@code Container.inventorySlots} as {@link Slot} objects with
 * their own coordinates, and are clicked through the player controller rather than by pressing a
 * widget. Running the button navigator on these screens was worse than running nothing: it found
 * no candidates, so it selected nothing and highlighted nothing, while still consuming the input.
 */
public final class ContainerNavigator {

    private static final int HIGHLIGHT_COLOR = 0xFFFFC66B;
    private static final int SLOT_SIZE = 16;

    private final GuiInputRouter router = new GuiInputRouter();

    private GuiContainer boundScreen;
    private int selectedSlotNumber = -1;
    private boolean justOpened;

    private static Field guiLeftField;
    private static Field guiTopField;
    private static boolean reflectionWarned;

    public static boolean isContainerScreen(GuiScreen screen) {
        return screen instanceof GuiContainer;
    }

    public void close() {
        boundScreen = null;
        selectedSlotNumber = -1;
        justOpened = false;
        router.reset();
    }

    /**
     * @return true when this navigator handled the tick, so the button navigator should stand
     *         aside.
     */
    public boolean onScreenTick(
        Minecraft client,
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout,
        InputTranslator translator
    ) {
        if (client == null || !isContainerScreen(client.currentScreen)) {
            close();
            return false;
        }

        GuiContainer screen = (GuiContainer) client.currentScreen;
        if (screen != boundScreen) {
            boundScreen = screen;
            selectedSlotNumber = -1;
            // The button that opened the container is very likely still held, and pulse() reads a
            // press with no previous state as a fresh one. Without swallowing a frame, opening a
            // chest would immediately pick up whatever slot happened to be selected.
            justOpened = true;
            router.reset();
        }

        List<Slot> slots = usableSlots(screen);
        if (slots.isEmpty()) {
            return false;
        }
        if (findSlot(slots, selectedSlotNumber) == null) {
            selectedSlotNumber = slots.get(0).slotNumber;
        }

        GuiInputFrame frame = router.route(snapshot, config, layout, translator, false);
        if (justOpened) {
            justOpened = false;
            return true;
        }
        if (!frame.anyNavigation()) {
            return true;
        }

        Slot selected = findSlot(slots, selectedSlotNumber);
        if (frame.up()) {
            selected = nearestInDirection(slots, selected, 0, -1);
        } else if (frame.down()) {
            selected = nearestInDirection(slots, selected, 0, 1);
        } else if (frame.left()) {
            selected = nearestInDirection(slots, selected, -1, 0);
        } else if (frame.right()) {
            selected = nearestInDirection(slots, selected, 1, 0);
        }
        selectedSlotNumber = selected.slotNumber;

        if (frame.confirm()) {
            click(client, screen, selected, 0, ClickType.PICKUP);
        }
        if (frame.tabNext()) {
            // Shift-click. The single most used action in a chest, so it gets a button of its own
            // rather than a modifier the player has to hold.
            click(client, screen, selected, 0, ClickType.QUICK_MOVE);
        }
        if (frame.pageNext()) {
            // Right click: take half a stack, or place one item.
            click(client, screen, selected, 1, ClickType.PICKUP);
        }
        if (frame.back() && client.player != null) {
            // Through the player rather than displayGuiScreen(null): the server has to be told the
            // container was closed, or it keeps the window open and the next one desynchronises.
            client.player.closeScreen();
            close();
        }
        return true;
    }

    /**
     * Draws the selection as a border rather than a wash.
     *
     * <p>This runs on DrawScreenEvent.Post, which is after the screen has drawn its items - so a
     * filled rectangle sits on top of the icon it is meant to point at. A border leaves the item
     * untouched.
     */
    public void draw(Minecraft client, GuiScreen screen) {
        if (client == null || boundScreen == null || screen != boundScreen) {
            return;
        }
        Slot selected = selectedSlot();
        if (selected == null) {
            return;
        }

        int left = guiLeft(boundScreen) + selected.xPos - 1;
        int top = guiTop(boundScreen) + selected.yPos - 1;
        int right = left + SLOT_SIZE + 2;
        int bottom = top + SLOT_SIZE + 2;

        Gui.drawRect(left, top, right, top + 1, HIGHLIGHT_COLOR);
        Gui.drawRect(left, bottom - 1, right, bottom, HIGHLIGHT_COLOR);
        Gui.drawRect(left, top, left + 1, bottom, HIGHLIGHT_COLOR);
        Gui.drawRect(right - 1, top, right, bottom, HIGHLIGHT_COLOR);
    }

    /**
     * The selected slot, read straight off the container.
     *
     * <p>Deliberately not routed through {@link #usableSlots}: this runs every frame, and building
     * a list to find one element would allocate for nothing sixty times a second.
     */
    private Slot selectedSlot() {
        if (boundScreen.inventorySlots == null || boundScreen.inventorySlots.inventorySlots == null) {
            return null;
        }
        for (Slot slot : boundScreen.inventorySlots.inventorySlots) {
            if (slot != null && slot.isEnabled() && slot.slotNumber == selectedSlotNumber) {
                return slot;
            }
        }
        return null;
    }

    private static List<Slot> usableSlots(GuiContainer screen) {
        List<Slot> result = new ArrayList<Slot>();
        if (screen == null || screen.inventorySlots == null || screen.inventorySlots.inventorySlots == null) {
            return result;
        }
        for (Slot slot : screen.inventorySlots.inventorySlots) {
            if (slot != null && slot.isEnabled()) {
                result.add(slot);
            }
        }
        return result;
    }

    private static Slot findSlot(List<Slot> slots, int slotNumber) {
        for (Slot slot : slots) {
            if (slot.slotNumber == slotNumber) {
                return slot;
            }
        }
        return null;
    }

    /**
     * Picks the next slot in a direction.
     *
     * <p>Scores distance along the axis of travel plus a penalty for drifting off it, so a grid
     * steps down its own column. The penalty matters more here than it does for buttons: a
     * container is a dense grid, and without it "down" from the top-left of a chest would find the
     * slot to its right just as readily as the one beneath.
     */
    private static Slot nearestInDirection(List<Slot> slots, Slot from, int dx, int dy) {
        if (from == null) {
            return slots.get(0);
        }
        Slot best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Slot candidate : slots) {
            if (candidate == from) {
                continue;
            }
            int along = ((candidate.xPos - from.xPos) * dx) + ((candidate.yPos - from.yPos) * dy);
            if (along <= 0) {
                continue;
            }
            int across = Math.abs(((candidate.xPos - from.xPos) * dy) + ((candidate.yPos - from.yPos) * dx));
            int score = along + (across * 4);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best == null ? from : best;
    }

    /**
     * Clicks a slot the way the mouse would.
     *
     * <p>Through the player controller rather than the screen's own handleMouseClick, so the server
     * is told about it - the container's contents live on the server, and a purely client-side
     * change would be reverted on the next sync.
     */
    private static void click(Minecraft client, GuiContainer screen, Slot slot, int button, ClickType type) {
        if (client.playerController == null || client.player == null || slot == null) {
            return;
        }
        client.playerController.windowClick(
            screen.inventorySlots.windowId, slot.slotNumber, button, type, client.player);
    }

    private static int guiLeft(GuiContainer screen) {
        return readInt(screen, guiLeftField(), 0);
    }

    private static int guiTop(GuiContainer screen) {
        return readInt(screen, guiTopField(), 0);
    }

    private static Field guiLeftField() {
        if (guiLeftField == null) {
            guiLeftField = findField("guiLeft");
        }
        return guiLeftField;
    }

    private static Field guiTopField() {
        if (guiTopField == null) {
            guiTopField = findField("guiTop");
        }
        return guiTopField;
    }

    /** guiLeft and guiTop are protected, and slot coordinates are relative to them. */
    private static Field findField(String name) {
        try {
            Field field = GuiContainer.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Exception failure) {
            if (!reflectionWarned) {
                reflectionWarned = true;
                BetterControllerMod.logger().warn(
                    "BetterController: could not read GuiContainer." + name
                        + "; the container highlight will be misplaced.", failure);
            }
            return null;
        }
    }

    private static int readInt(GuiContainer screen, Field field, int fallback) {
        if (field == null) {
            return fallback;
        }
        try {
            return field.getInt(screen);
        } catch (Exception failure) {
            return fallback;
        }
    }
}
