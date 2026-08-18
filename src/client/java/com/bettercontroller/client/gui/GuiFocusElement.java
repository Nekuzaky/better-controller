package com.bettercontroller.client.gui;

import net.minecraft.client.gui.components.AbstractWidget;

public record GuiFocusElement(AbstractWidget widget, int centerX, int centerY) {
    public static GuiFocusElement fromWidget(AbstractWidget widget) {
        int centerX = widget.getX() + (widget.getWidth() / 2);
        int centerY = widget.getY() + (widget.getHeight() / 2);
        return new GuiFocusElement(widget, centerX, centerY);
    }
}
