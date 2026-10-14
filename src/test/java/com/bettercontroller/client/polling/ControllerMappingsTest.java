package com.bettercontroller.client.polling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Only the parsing is testable without GLFW; applying the mappings needs a live window.
 */
class ControllerMappingsTest {
    @Test
    void countsOnlyRealMappingLines() {
        String contents = """
            # SDL_GameControllerDB
            # Windows
            03000000790000000600000000000000,G-Shark GS-GP702,a:b2,b:b1,x:b3,y:b0,platform:Windows,

            030000005e0400008e02000000000000,Xbox 360 Controller,a:b0,b:b1,platform:Windows,
            """;

        assertEquals(2, ControllerMappings.countMappingLines(contents));
    }

    @Test
    void ignoresBlankAndCommentOnlyFiles() {
        assertEquals(0, ControllerMappings.countMappingLines(""));
        assertEquals(0, ControllerMappings.countMappingLines("\n\n   \n"));
        assertEquals(0, ControllerMappings.countMappingLines("# nothing but a comment\n"));
        assertEquals(0, ControllerMappings.countMappingLines(null));
    }

    @Test
    void toleratesWindowsLineEndingsAndIndentation() {
        String contents = "  # comment\r\n  030000005e0400008e02000000000000,Pad,a:b0,\r\n\r\n";

        assertEquals(1, ControllerMappings.countMappingLines(contents));
    }
}
