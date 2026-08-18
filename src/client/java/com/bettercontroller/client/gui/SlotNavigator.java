package com.bettercontroller.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * Picks the best slot to navigate to in a given direction. Uses a row/column lane heuristic
 * with a generic Euclidean fallback so dense layouts (creative tabs, chests) stay predictable.
 */
public final class SlotNavigator {
    private static final int SLOT_ROW_TOLERANCE = 10;
    private static final int SLOT_LANE_TOLERANCE = 10;
    private static final int SLOT_CENTER_OFFSET = 8;

    private SlotNavigator() {
    }

    public static int findDirectionalSlot(List<Slot> slots, int currentId, int dirX, int dirY) {
        Slot current = findSlotById(slots, currentId);
        if (current == null) {
            return slots.isEmpty() ? -1 : slots.get(0).id;
        }

        Slot laneCandidate = findLaneAlignedSlot(slots, current, dirX, dirY);
        if (laneCandidate != null) {
            return laneCandidate.id;
        }
        Slot rowColumnCandidate = findRowColumnNeighborSlot(slots, current, dirX, dirY);
        if (rowColumnCandidate != null) {
            return rowColumnCandidate.id;
        }
        Slot generic = findGenericNearest(slots, current, dirX, dirY);
        return generic == null ? current.id : generic.id;
    }

    public static Slot findSlotById(List<Slot> slots, int slotId) {
        if (slots == null) {
            return null;
        }
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot != null && slot.index == slotId) {
                return slot;
            }
        }
        return null;
    }

    public static Slot resolveInitialSlot(Minecraft client, List<Slot> slots) {
        if (slots == null || slots.isEmpty()) {
            return null;
        }
        if (client == null || client.player == null) {
            return slots.get(0);
        }
        int selectedHotbar = client.player.getInventory().getSelectedSlot();
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot != null
                && slot.inventory == client.player.getInventory()
                && slot.getIndex() == selectedHotbar) {
                return slot;
            }
        }
        return slots.get(0);
    }

    private static Slot findRowColumnNeighborSlot(List<Slot> slots, Slot current, int dirX, int dirY) {
        if (dirX == 0 && dirY == 0) {
            return null;
        }
        int cx = current.x + SLOT_CENTER_OFFSET;
        int cy = current.y + SLOT_CENTER_OFFSET;
        return dirX != 0
            ? findHorizontalNeighbor(slots, current.id, cx, cy, dirX)
            : findVerticalNeighbor(slots, current.id, cx, cy, dirY);
    }

    private static Slot findHorizontalNeighbor(List<Slot> slots, int currentId, int cx, int cy, int dirX) {
        Slot best = null;
        int bestColumn = Integer.MAX_VALUE;
        int bestRow = Integer.MAX_VALUE;

        for (int i = 0; i < slots.size(); i++) {
            Slot candidate = slots.get(i);
            if (candidate == null || candidate.id == currentId) continue;
            int deltaX = (candidate.x + SLOT_CENTER_OFFSET) - cx;
            int deltaY = (candidate.y + SLOT_CENTER_OFFSET) - cy;
            if (dirX < 0 && deltaX >= 0) continue;
            if (dirX > 0 && deltaX <= 0) continue;
            if (Math.abs(deltaY) > SLOT_ROW_TOLERANCE) continue;
            int columnDistance = Math.abs(deltaX);
            int rowDistance = Math.abs(deltaY);
            if (columnDistance < bestColumn || (columnDistance == bestColumn && rowDistance < bestRow)) {
                bestColumn = columnDistance;
                bestRow = rowDistance;
                best = candidate;
            }
        }
        return best;
    }

    private static Slot findVerticalNeighbor(List<Slot> slots, int currentId, int cx, int cy, int dirY) {
        Slot best = null;
        int bestRow = Integer.MAX_VALUE;
        int bestColumn = Integer.MAX_VALUE;

        for (int i = 0; i < slots.size(); i++) {
            Slot candidate = slots.get(i);
            if (candidate == null || candidate.id == currentId) continue;
            int deltaY = (candidate.y + SLOT_CENTER_OFFSET) - cy;
            if (dirY < 0 && deltaY >= 0) continue;
            if (dirY > 0 && deltaY <= 0) continue;
            int rowDist = Math.abs(deltaY);
            int colDist = Math.abs((candidate.x + SLOT_CENTER_OFFSET) - cx);
            if (rowDist < bestRow || (rowDist == bestRow && colDist < bestColumn)) {
                bestRow = rowDist;
                bestColumn = colDist;
                best = candidate;
            }
        }
        return best;
    }

    private static Slot findLaneAlignedSlot(List<Slot> slots, Slot current, int dirX, int dirY) {
        if (dirX == 0 && dirY == 0) {
            return null;
        }
        int cx = current.x + SLOT_CENTER_OFFSET;
        int cy = current.y + SLOT_CENTER_OFFSET;
        Slot best = null;
        int bestPrimary = Integer.MAX_VALUE;
        int bestSecondary = Integer.MAX_VALUE;

        for (int i = 0; i < slots.size(); i++) {
            Slot candidate = slots.get(i);
            if (candidate == null || candidate.id == current.id) continue;
            int deltaX = (candidate.x + SLOT_CENTER_OFFSET) - cx;
            int deltaY = (candidate.y + SLOT_CENTER_OFFSET) - cy;
            if (dirX < 0 && deltaX >= 0) continue;
            if (dirX > 0 && deltaX <= 0) continue;
            if (dirY < 0 && deltaY >= 0) continue;
            if (dirY > 0 && deltaY <= 0) continue;

            int primary;
            int secondary;
            if (dirX != 0) {
                secondary = Math.abs(deltaY);
                if (secondary > SLOT_LANE_TOLERANCE) continue;
                primary = Math.abs(deltaX);
            } else {
                secondary = Math.abs(deltaX);
                if (secondary > SLOT_LANE_TOLERANCE) continue;
                primary = Math.abs(deltaY);
            }
            if (primary < bestPrimary || (primary == bestPrimary && secondary < bestSecondary)) {
                bestPrimary = primary;
                bestSecondary = secondary;
                best = candidate;
            }
        }
        return best;
    }

    private static Slot findGenericNearest(List<Slot> slots, Slot current, int dirX, int dirY) {
        int cx = current.x + SLOT_CENTER_OFFSET;
        int cy = current.y + SLOT_CENTER_OFFSET;
        Slot best = null;
        double bestScore = Double.MAX_VALUE;

        for (int i = 0; i < slots.size(); i++) {
            Slot candidate = slots.get(i);
            if (candidate == null || candidate.id == current.id) continue;
            int deltaX = (candidate.x + SLOT_CENTER_OFFSET) - cx;
            int deltaY = (candidate.y + SLOT_CENTER_OFFSET) - cy;
            if (dirX < 0 && deltaX >= 0) continue;
            if (dirX > 0 && deltaX <= 0) continue;
            if (dirY < 0 && deltaY >= 0) continue;
            if (dirY > 0 && deltaY <= 0) continue;
            double distance = Math.sqrt((double) (deltaX * deltaX) + (deltaY * deltaY));
            double axisPenalty = dirX != 0 ? Math.abs(deltaY) * 0.55D : Math.abs(deltaX) * 0.55D;
            double score = distance + axisPenalty;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }
}
