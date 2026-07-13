package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.ProcessorOutput;

import static org.junit.jupiter.api.Assertions.*;

class PracticeArtworkTest {
    @Test
    void bundledPracticeDrawingIsSmallSinglePenArtwork() {
        ProcessorOutput output = PracticeArtwork.create();

        assertEquals("Gantry practice drawing", output.metadata().source());
        assertEquals(1, output.layers().size());
        assertFalse(output.layers().get(0).commands().isEmpty());
        assertEquals(80, output.metadata().bounds().maxX() - output.metadata().bounds().minX());
        assertEquals(60, output.metadata().bounds().maxY() - output.metadata().bounds().minY());
    }
}
