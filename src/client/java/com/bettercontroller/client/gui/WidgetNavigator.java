package com.bettercontroller.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;

import java.util.List;

/**
 * Picks the best widget index to navigate to in a given direction.
 * Horizontal navigation stays within the current row (perpendicular tolerance).
 * Vertical navigation first finds the nearest row in the requested direction, then
 * picks the widget in that row with the smallest column offset. Falls back to a
 * Euclidean score when no tight lane match is found.
 */
public final class WidgetNavigator {
    private static final int LANE_TOLERANCE_PX = 12;

    private WidgetNavigator() {
    }

    public static int findDirectionalIndex(List<GuiFocusElement> elements, int currentIndex, int dirX, int dirY) {
        if (elements == null || elements.isEmpty() || currentIndex < 0 || currentIndex >= elements.size()) {
            return currentIndex;
        }
        GuiFocusElement current = elements.get(currentIndex);

        int laneSelection = dirX != 0
            ? findHorizontalLaneIndex(elements, current, currentIndex, dirX)
            : findVerticalLaneIndex(elements, current, currentIndex, dirY);
        if (laneSelection != currentIndex) {
            return laneSelection;
        }
        return findEuclideanIndex(elements, current, currentIndex, dirX, dirY);
    }

    private static int findHorizontalLaneIndex(
        List<GuiFocusElement> elements,
        GuiFocusElement current,
        int currentIndex,
        int dirX
    ) {
        int bestIndex = currentIndex;
        int bestPrimary = Integer.MAX_VALUE;
        int bestSecondary = Integer.MAX_VALUE;

        for (int i = 0; i < elements.size(); i++) {
            if (i == currentIndex) continue;
            GuiFocusElement candidate = elements.get(i);
            int deltaX = candidate.centerX() - current.centerX();
            int deltaY = candidate.centerY() - current.centerY();
            if (dirX < 0 && deltaX >= 0) continue;
            if (dirX > 0 && deltaX <= 0) continue;
            if (Math.abs(deltaY) > LANE_TOLERANCE_PX) continue;

            int primary = Math.abs(deltaX);
            int secondary = Math.abs(deltaY);
            if (primary < bestPrimary || (primary == bestPrimary && secondary < bestSecondary)) {
                bestPrimary = primary;
                bestSecondary = secondary;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static int findVerticalLaneIndex(
        List<GuiFocusElement> elements,
        GuiFocusElement current,
        int currentIndex,
        int dirY
    ) {
        Integer targetRowY = resolveNearestRowY(elements, current, currentIndex, dirY);
        if (targetRowY == null) {
            return currentIndex;
        }

        int bestIndex = currentIndex;
        int bestPrimary = Integer.MAX_VALUE;
        int bestSecondary = Integer.MAX_VALUE;

        for (int i = 0; i < elements.size(); i++) {
            if (i == currentIndex) continue;
            GuiFocusElement candidate = elements.get(i);
            int deltaX = candidate.centerX() - current.centerX();
            int deltaY = candidate.centerY() - current.centerY();
            if (dirY < 0 && deltaY >= 0) continue;
            if (dirY > 0 && deltaY <= 0) continue;
            if (Math.abs(candidate.centerY() - targetRowY) > LANE_TOLERANCE_PX) continue;

            int primary = Math.abs(deltaX);
            int secondary = Math.abs(deltaY);
            if (primary < bestPrimary || (primary == bestPrimary && secondary < bestSecondary)) {
                bestPrimary = primary;
                bestSecondary = secondary;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static Integer resolveNearestRowY(
        List<GuiFocusElement> elements,
        GuiFocusElement current,
        int currentIndex,
        int dirY
    ) {
        Integer target = null;
        int nearestDistance = Integer.MAX_VALUE;

        for (int i = 0; i < elements.size(); i++) {
            if (i == currentIndex) continue;
            GuiFocusElement candidate = elements.get(i);
            int deltaY = candidate.centerY() - current.centerY();
            if (dirY < 0 && deltaY >= 0) continue;
            if (dirY > 0 && deltaY <= 0) continue;

            int distance = Math.abs(deltaY);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                target = candidate.centerY();
            }
        }
        return target;
    }

    private static int findEuclideanIndex(
        List<GuiFocusElement> elements,
        GuiFocusElement current,
        int currentIndex,
        int dirX,
        int dirY
    ) {
        int bestIndex = currentIndex;
        double bestScore = Double.MAX_VALUE;

        for (int i = 0; i < elements.size(); i++) {
            if (i == currentIndex) continue;
            GuiFocusElement candidate = elements.get(i);
            int deltaX = candidate.centerX() - current.centerX();
            int deltaY = candidate.centerY() - current.centerY();
            if (dirX < 0 && deltaX >= 0) continue;
            if (dirX > 0 && deltaX <= 0) continue;
            if (dirY < 0 && deltaY >= 0) continue;
            if (dirY > 0 && deltaY <= 0) continue;

            double distance = Math.sqrt((double) (deltaX * deltaX) + (deltaY * deltaY));
            double axisPenalty = dirX != 0 ? Math.abs(deltaY) * 0.75D : Math.abs(deltaX) * 0.75D;
            double score = distance + axisPenalty;
            if (score < bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    public static int ensureValidSelection(Screen screen, List<GuiFocusElement> elements, int selectedIndex) {
        if (elements == null || elements.isEmpty()) {
            return -1;
        }
        if (selectedIndex >= 0 && selectedIndex < elements.size()) {
            AbstractWidget widget = elements.get(selectedIndex).widget();
            if (widget.active && widget.visible) {
                return selectedIndex;
            }
        }
        return findInitialIndex(screen, elements);
    }

    public static int findInitialIndex(Screen screen, List<GuiFocusElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return -1;
        }
        var focused = screen == null ? null : screen.getFocused();
        if (focused instanceof AbstractWidget focusedWidget) {
            for (int i = 0; i < elements.size(); i++) {
                if (elements.get(i).widget() == focusedWidget) {
                    return i;
                }
            }
        }
        return preferNonSliderIndex(elements, 0);
    }

    public static int preferNonSliderIndex(List<GuiFocusElement> elements, int fallbackIndex) {
        if (elements == null || elements.isEmpty()) {
            return -1;
        }
        if (fallbackIndex >= 0
            && fallbackIndex < elements.size()
            && !(elements.get(fallbackIndex).widget() instanceof AbstractSliderButton)) {
            return fallbackIndex;
        }
        for (int i = 0; i < elements.size(); i++) {
            if (!(elements.get(i).widget() instanceof AbstractSliderButton)) {
                return i;
            }
        }
        return Math.max(0, Math.min(fallbackIndex, elements.size() - 1));
    }

    public static void applySelection(Screen screen, List<GuiFocusElement> elements, int selectedIndex) {
        for (int i = 0; i < elements.size(); i++) {
            AbstractWidget widget = elements.get(i).widget();
            boolean selected = i == selectedIndex;
            widget.setFocused(selected);
            if (selected && screen != null) {
                screen.setFocused(widget);
            }
        }
    }
}
