package com.bettercontroller.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiListWorldSelection;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiResourcePackList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.ServerSelectionList;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * A scrolling list on a screen - worlds, servers, resource packs, languages - as one stop for the
 * button navigator.
 *
 * <p>1.12.2's lists are {@link GuiSlot}s, which live outside {@code buttonList} and know nothing
 * of a keyboard. Each list is stood in for by a {@link GuiButton} covering it, so the navigator's
 * geometry gets there and away again unchanged; once there, up and down step through the entries
 * and A opens the selected one.
 *
 * <p>"Open" means something different in every list, and the extended lists ignore
 * {@code elementClicked} entirely - their entries handle the mouse themselves, by where in the
 * entry it lands. So each kind vanilla ships is driven through its own public API, the same calls
 * its double click makes. A plain GuiSlot gets a double click through {@code elementClicked}. Any
 * other extended list - the key bindings, the video settings rows - holds controls of its own
 * that a synthesised click would hit by position, and is left alone rather than guessed at.
 */
final class SlotListTarget {

    private enum Kind { WORLDS, SERVERS, RESOURCE_PACKS, PLAIN }

    private static final int HIGHLIGHT_COLOR = 0xFFFFC66B;

    /** Entry inside the pack icon: "move to the other list" on either resource pack list. */
    private static final int PACK_ICON_X = 8;
    private static final int PACK_ICON_Y = 16;

    private final GuiSlot list;
    private final Kind kind;
    private final GuiButton target = new GuiButton(-1, 0, 0, 0, 0, "");
    private int index;
    /** Entry count as of the last input; drawing reads this rather than asking every frame. */
    private int size;

    private SlotListTarget(GuiSlot list, Kind kind) {
        this.list = list;
        this.kind = kind;
        if (list instanceof ServerSelectionList) {
            index = Math.max(0, ((ServerSelectionList) list).getSelected());
        }
        syncTarget();
    }

    /**
     * The navigable lists on a screen, found by type rather than name, which needs no obfuscated
     * names and also finds a modded screen's plain lists. Lists stacked on the same rectangle are
     * dropped: the statistics screen keeps four and shows one, and there is no telling which.
     */
    static List<SlotListTarget> find(GuiScreen screen) {
        List<SlotListTarget> result = new ArrayList<SlotListTarget>();
        if (screen == null) {
            return result;
        }
        List<GuiSlot> lists = new ArrayList<GuiSlot>();
        for (Class<?> type = screen.getClass(); type != null && type != GuiScreen.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                GuiSlot list = readList(screen, field);
                if (list != null && !lists.contains(list)) {
                    lists.add(list);
                }
            }
        }
        for (GuiSlot list : lists) {
            Kind kind = kindOf(list);
            if (kind != null && !overlapsAnother(list, lists)) {
                result.add(new SlotListTarget(list, kind));
            }
        }
        return result;
    }

    GuiButton target() {
        return target;
    }

    /**
     * Lists move on resize without being recreated, and gain entries as servers are discovered,
     * so the stand-in and the entry count are refreshed whenever there is input to act on.
     */
    void syncTarget() {
        size = GuiReflection.slotCount(list);
        int listWidth = list.getListWidth();
        target.x = list.left + (list.width / 2) - (listWidth / 2);
        target.y = list.top;
        target.width = listWidth;
        target.height = Math.max(0, list.bottom - list.top);
    }

    /**
     * Steps the selection one entry. Returns false at either end, so the caller can move the
     * selection off the list instead - otherwise the buttons under a list could never be reached.
     */
    boolean move(GuiScreen screen, int delta) {
        size = GuiReflection.slotCount(list);
        int next = clampIndex(size) + delta;
        if (size == 0 || next < 0 || next >= size) {
            return false;
        }
        index = next;
        select(screen);
        scrollIntoView();
        return true;
    }

    /**
     * Arriving on the list from elsewhere: brings the remembered entry into view and makes it the
     * screen's own selection too, so Play or Join works straight away.
     */
    void enter(GuiScreen screen) {
        size = GuiReflection.slotCount(list);
        if (size == 0) {
            return;
        }
        index = clampIndex(size);
        select(screen);
        scrollIntoView();
    }

    /** What a double click on the selected entry does. */
    void activate(Minecraft client, GuiScreen screen) {
        size = GuiReflection.slotCount(list);
        if (size == 0) {
            return;
        }
        index = clampIndex(size);
        target.playPressSound(client.getSoundHandler());
        switch (kind) {
            case WORLDS:
                ((GuiListWorldSelection) list).selectWorld(index);
                ((GuiListWorldSelection) list).getListEntry(index).joinWorld();
                break;
            case SERVERS:
                if (screen instanceof GuiMultiplayer) {
                    ((GuiMultiplayer) screen).selectServer(index);
                    ((GuiMultiplayer) screen).connectToSelected();
                }
                break;
            case RESOURCE_PACKS:
                ((GuiResourcePackList) list).getListEntry(index).mousePressed(
                    index, entryCenterX(), entryTop(index) + PACK_ICON_Y, 0, PACK_ICON_X, PACK_ICON_Y);
                break;
            default:
                GuiReflection.elementClicked(list, index, true, entryCenterX(), entryTop(index));
                break;
        }
    }

    /** Scrolls by mouse-wheel notches, as the wheel would; negative is up. */
    void scrollNotches(int notches) {
        list.scrollBy(notches * Math.max(1, list.slotHeight / 2));
    }

    /** A border round the selected entry, or round the whole list while it is scrolled away. */
    void drawSelection() {
        int top = entryTop(clampIndex(size));
        int bottom = top + list.slotHeight - 4;
        if (size == 0 || top < list.top || bottom > list.bottom) {
            drawBorder(target.x, target.y, target.x + target.width, target.y + target.height);
            return;
        }
        drawBorder(target.x, top - 2, target.x + target.width, bottom + 2);
    }

    /** Keeps the vanilla selection in step, so the screen's own buttons - Play, Join - light up. */
    private void select(GuiScreen screen) {
        if (kind == Kind.WORLDS) {
            ((GuiListWorldSelection) list).selectWorld(index);
        } else if (kind == Kind.SERVERS && screen instanceof GuiMultiplayer) {
            ((GuiMultiplayer) screen).selectServer(index);
        }
    }

    private void scrollIntoView() {
        int top = entryTop(index);
        int bottom = top + list.slotHeight - 4;
        if (top < list.top) {
            list.scrollBy(top - list.top - 2);
        } else if (bottom > list.bottom) {
            list.scrollBy(bottom - list.bottom + 2);
        }
    }

    /** Mirrors GuiSlot.drawScreen and drawSelectionBox, which is where these numbers come from. */
    private int entryTop(int entry) {
        return list.top + 4 - list.getAmountScrolled() + (entry * list.slotHeight) + list.headerPadding;
    }

    private int entryCenterX() {
        return list.left + (list.width / 2);
    }

    private int clampIndex(int size) {
        return Math.max(0, Math.min(index, size - 1));
    }

    private static Kind kindOf(GuiSlot list) {
        if (list instanceof GuiListWorldSelection) {
            return Kind.WORLDS;
        }
        if (list instanceof ServerSelectionList) {
            return Kind.SERVERS;
        }
        if (list instanceof GuiResourcePackList) {
            return Kind.RESOURCE_PACKS;
        }
        return list instanceof GuiListExtended ? null : Kind.PLAIN;
    }

    private static boolean overlapsAnother(GuiSlot list, List<GuiSlot> lists) {
        for (GuiSlot other : lists) {
            if (other != list && other.left == list.left && other.top == list.top
                && other.width == list.width && other.bottom == list.bottom) {
                return true;
            }
        }
        return false;
    }

    private static GuiSlot readList(GuiScreen screen, Field field) {
        if (!GuiSlot.class.isAssignableFrom(field.getType())) {
            return null;
        }
        try {
            field.setAccessible(true);
            return (GuiSlot) field.get(screen);
        } catch (Exception unreadable) {
            return null;
        }
    }

    private static void drawBorder(int left, int top, int right, int bottom) {
        Gui.drawRect(left, top, right, top + 1, HIGHLIGHT_COLOR);
        Gui.drawRect(left, bottom - 1, right, bottom, HIGHLIGHT_COLOR);
        Gui.drawRect(left, top, left + 1, bottom, HIGHLIGHT_COLOR);
        Gui.drawRect(right - 1, top, right, bottom, HIGHLIGHT_COLOR);
    }
}
