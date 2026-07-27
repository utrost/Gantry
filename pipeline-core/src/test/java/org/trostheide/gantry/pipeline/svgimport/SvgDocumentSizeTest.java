package org.trostheide.gantry.pipeline.svgimport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SvgDocumentSizeTest {
    @Test
    void readsCommonPhysicalUnitsAsMillimetres() {
        assertEquals(210.0, SvgDocumentSize.toMillimetres("210mm"), 1e-9);
        assertEquals(210.0, SvgDocumentSize.toMillimetres("21cm"), 1e-9);
        assertEquals(25.4, SvgDocumentSize.toMillimetres("1in"), 1e-9);
        assertEquals(25.4, SvgDocumentSize.toMillimetres("72pt"), 1e-9);
        assertEquals(25.4, SvgDocumentSize.toMillimetres("6pc"), 1e-9);
        assertEquals(25.4, SvgDocumentSize.toMillimetres("96px"), 1e-9);
        assertEquals(25.4, SvgDocumentSize.toMillimetres("96"), 1e-9);
    }

    @Test
    void rejectsPercentagesAndMissingValues() {
        assertNull(SvgDocumentSize.toMillimetres("100%"));
        assertNull(SvgDocumentSize.toMillimetres(""));
        assertNull(SvgDocumentSize.toMillimetres(null));
    }
}
