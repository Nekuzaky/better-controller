package com.bettercontroller.client.gui;

/**
 * One frame of menu navigation intent.
 *
 * <p>A record on every other version. Java 8 has none, so the accessors are written out - the
 * shape and the call sites are unchanged.
 */
public final class GuiInputFrame {
    private final boolean up;
    private final boolean down;
    private final boolean left;
    private final boolean right;
    private final boolean confirm;
    private final boolean back;
    private final boolean pageNext;
    private final boolean pagePrev;
    private final boolean tabNext;
    private final boolean tabPrev;

    public GuiInputFrame(
        boolean up,
        boolean down,
        boolean left,
        boolean right,
        boolean confirm,
        boolean back,
        boolean pageNext,
        boolean pagePrev,
        boolean tabNext,
        boolean tabPrev
    ) {
        this.up = up;
        this.down = down;
        this.left = left;
        this.right = right;
        this.confirm = confirm;
        this.back = back;
        this.pageNext = pageNext;
        this.pagePrev = pagePrev;
        this.tabNext = tabNext;
        this.tabPrev = tabPrev;
    }

    public boolean up() {
        return up;
    }

    public boolean down() {
        return down;
    }

    public boolean left() {
        return left;
    }

    public boolean right() {
        return right;
    }

    public boolean confirm() {
        return confirm;
    }

    public boolean back() {
        return back;
    }

    public boolean pageNext() {
        return pageNext;
    }

    public boolean pagePrev() {
        return pagePrev;
    }

    public boolean tabNext() {
        return tabNext;
    }

    public boolean tabPrev() {
        return tabPrev;
    }

    public boolean anyNavigation() {
        return up || down || left || right || confirm || back || pageNext || pagePrev || tabNext || tabPrev;
    }
}
