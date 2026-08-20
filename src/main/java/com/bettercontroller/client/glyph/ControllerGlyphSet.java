package com.bettercontroller.client.glyph;

public enum ControllerGlyphSet {
    XBOX("Xbox"),
    PLAYSTATION("PlayStation"),
    SWITCH("Switch"),
    GENERIC("Generic");

    private final String displayName;

    ControllerGlyphSet(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
