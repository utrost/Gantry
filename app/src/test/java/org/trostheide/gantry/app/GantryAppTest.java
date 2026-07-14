package org.trostheide.gantry.app;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GantryAppTest {
    @Test
    void initialWindowFitsMinimumScreenUsableArea() {
        Rectangle usable = new Rectangle(0, 25, 1024, 775);

        assertEquals(usable, GantryApp.initialBounds(usable));
    }

    @Test
    void initialWindowKeepsPreferredSizeAndCentersOnLargerDisplays() {
        Rectangle usable = new Rectangle(40, 30, 1920, 1050);

        assertEquals(new Rectangle(360, 145, 1280, 820), GantryApp.initialBounds(usable));
    }
}
