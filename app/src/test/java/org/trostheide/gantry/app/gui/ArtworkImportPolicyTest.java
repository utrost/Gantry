package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.pipeline.svgimport.PaperFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtworkImportPolicyTest {
    @Test
    void usesConfiguredMachineBed() {
        PaperFormat bed = ArtworkImportPolicy.machineBed(420, 297);

        assertEquals(420, bed.width());
        assertEquals(297, bed.height());
        assertTrue(ArtworkImportPolicy.summary(bed, 10).contains("420 × 297 mm"));
    }

    @Test
    void invalidMachineDimensionsFallBackToSafeKnownBed() {
        PaperFormat bed = ArtworkImportPolicy.machineBed(Double.NaN, -1);

        assertEquals(300, bed.width());
        assertEquals(200, bed.height());
    }
}
