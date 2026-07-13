package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import java.awt.Color;
import static org.junit.jupiter.api.Assertions.*;

class CanvasPaletteTest {
    @Test void parsesLongAndShortHex() {
        assertEquals(new Color(0xaa, 0xbb, 0xcc), CanvasPalette.parseHex("#abc"));
        assertEquals(new Color(1, 2, 3), CanvasPalette.parseHex("010203"));
        assertNull(CanvasPalette.parseHex("not-a-colour"));
    }
    @Test void darkGreyUsesReadableFallbackAndGhostMovesTowardBackground() {
        assertNotEquals(Color.BLACK, CanvasPalette.displayColor("#000000", 0));
        Color ghost = CanvasPalette.ghost(Color.WHITE);
        assertTrue(ghost.getRed() < Color.WHITE.getRed());
        assertTrue(ghost.getRed() > CanvasPalette.BACKGROUND.getRed());
    }
}
