package com.bettercontroller.client.glyph;

import com.bettercontroller.client.polling.ControllerAxis;
import com.bettercontroller.client.polling.ControllerButton;
import com.bettercontroller.client.polling.ControllerType;

import java.util.Locale;

/**
 * Resolves player-facing glyph labels from logical bindings and the current controller family.
 *
 * <p>The four label sets are tables rather than the switch expressions the other versions use -
 * Java 8 has no switch expressions, and four fifteen-case switch statements would be worse to read
 * than one table anyway. Rows line up with {@link ControllerButton}'s declaration order, so the
 * families can be compared down the column: the same physical button, four names.
 */
public final class ControllerGlyphService {

    private static final String[] XBOX_LABELS = {
        "A", "B", "X", "Y",
        "LB", "RB",
        "View", "Menu", "Guide",
        "L3", "R3",
        "D-Up", "D-Right", "D-Down", "D-Left",
    };

    private static final String[] PLAYSTATION_LABELS = {
        "Cross", "Circle", "Square", "Triangle",
        "L1", "R1",
        "Share", "Options", "PS",
        "L3", "R3",
        "D-Up", "D-Right", "D-Down", "D-Left",
    };

    // The Switch pad's south button is B and its east is A - the physical positions are swapped
    // relative to every other family, which is exactly why these are logical names internally.
    private static final String[] SWITCH_LABELS = {
        "B", "A", "Y", "X",
        "L", "R",
        "-", "+", "Home",
        "L3", "R3",
        "D-Up", "D-Right", "D-Down", "D-Left",
    };

    private static final String[] GENERIC_LABELS = {
        "South", "East", "West", "North",
        "LB", "RB",
        "Back", "Start", "Guide",
        "L3", "R3",
        "D-Up", "D-Right", "D-Down", "D-Left",
    };

    static {
        // A row that drifts out of step with the enum would mislabel every button after it, and
        // nothing else would notice. Fail at class-load instead.
        int expected = ControllerButton.values().length;
        if (XBOX_LABELS.length != expected
            || PLAYSTATION_LABELS.length != expected
            || SWITCH_LABELS.length != expected
            || GENERIC_LABELS.length != expected) {
            throw new IllegalStateException(
                "Glyph tables must have one entry per ControllerButton (" + expected + ").");
        }
    }

    private ControllerType activeControllerType = ControllerType.NONE;

    public void updateControllerType(ControllerType controllerType) {
        activeControllerType = controllerType == null ? ControllerType.NONE : controllerType;
    }

    public ControllerType activeControllerType() {
        return activeControllerType;
    }

    public ControllerGlyphSet activeGlyphSet() {
        return glyphSetFor(activeControllerType);
    }

    public String activeGlyphSetName() {
        return activeGlyphSet().displayName();
    }

    public String glyphForBinding(String bindingToken) {
        if (bindingToken == null || bindingToken.trim().isEmpty()) {
            return "?";
        }

        String normalized = bindingToken.trim().toUpperCase(Locale.ROOT);
        boolean inverted = normalized.startsWith("-");
        if (inverted) {
            normalized = normalized.substring(1);
        }

        ControllerButton button = ControllerButton.fromTokenOrNull(normalized);
        if (button != null) {
            return glyphForButton(button);
        }

        ControllerAxis axis = ControllerAxis.fromTokenOrNull(normalized);
        if (axis != null) {
            return glyphForAxis(axis, inverted);
        }

        return formatUnknown(normalized);
    }

    public String glyphForButton(ControllerButton button) {
        if (button == null) {
            return "?";
        }
        return labelsFor(activeGlyphSet())[button.ordinal()];
    }

    private static String[] labelsFor(ControllerGlyphSet glyphSet) {
        switch (glyphSet) {
            case XBOX:
                return XBOX_LABELS;
            case PLAYSTATION:
                return PLAYSTATION_LABELS;
            case SWITCH:
                return SWITCH_LABELS;
            default:
                return GENERIC_LABELS;
        }
    }

    private String glyphForAxis(ControllerAxis axis, boolean inverted) {
        String axisLabel = axisLabel(axis);
        if (!inverted) {
            return axisLabel;
        }
        switch (axis) {
            case LEFT_X:
            case RIGHT_X:
                return axisLabel + "-";
            case LEFT_Y:
            case RIGHT_Y:
                return axisLabel + " Up";
            default:
                return axisLabel;
        }
    }

    private String axisLabel(ControllerAxis axis) {
        switch (axis) {
            case LEFT_TRIGGER:
                return triggerLabel("L2", "ZL", "LT");
            case RIGHT_TRIGGER:
                return triggerLabel("R2", "ZR", "RT");
            case LEFT_X:
                return "LS X";
            case LEFT_Y:
                return "LS Y";
            case RIGHT_X:
                return "RS X";
            case RIGHT_Y:
                return "RS Y";
            default:
                return "?";
        }
    }

    private String triggerLabel(String playStation, String nintendo, String other) {
        switch (activeGlyphSet()) {
            case PLAYSTATION:
                return playStation;
            case SWITCH:
                return nintendo;
            default:
                return other;
        }
    }

    public static ControllerGlyphSet glyphSetFor(ControllerType type) {
        if (type == null) {
            return ControllerGlyphSet.GENERIC;
        }
        switch (type) {
            case XBOX:
                return ControllerGlyphSet.XBOX;
            case PLAYSTATION:
                return ControllerGlyphSet.PLAYSTATION;
            case SWITCH:
                return ControllerGlyphSet.SWITCH;
            default:
                return ControllerGlyphSet.GENERIC;
        }
    }

    private static String formatUnknown(String token) {
        return token
            .replace("SWITCH_", "")
            .replace("NINTENDO_", "")
            .replace('_', ' ');
    }
}
