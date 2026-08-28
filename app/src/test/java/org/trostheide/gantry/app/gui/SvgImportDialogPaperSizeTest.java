package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.pipeline.svgimport.PaperFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SvgImportDialogPaperSizeTest {
    @Test
    void pickerListsEveryIsoPresetWithItsActualDimensions() {
        assertArrayEquals(new String[] {
                "A6 — 105 × 148 mm",
                "A5 — 148 × 210 mm",
                "A4 — 210 × 297 mm",
                "A3 — 297 × 420 mm",
                "A2 — 420 × 594 mm",
                "A1 — 594 × 841 mm",
                "XL — 430 × 600 mm"
        }, SvgImportDialog.paperSizeOptions());
    }

    @Test
    void labelledA4AndA1ResolveToTheirDisplayedDimensions() {
        PaperFormat a4 = SvgImportDialog.resolvePaperPreset("A4 — 210 × 297 mm");
        assertEquals(210, a4.width());
        assertEquals(297, a4.height());

        PaperFormat a1 = SvgImportDialog.resolvePaperPreset("A1 — 594 × 841 mm");
        assertEquals(594, a1.width());
        assertEquals(841, a1.height());
    }

    @Test
    void declaredSvgSizeIsTheDefaultAndMachineBedIsTheFallback() {
        assertEquals("SVG document size",
                SvgImportDialog.initialFitSelection(new PaperFormat(210, 297)));
        assertEquals("Machine bed", SvgImportDialog.initialFitSelection(null));
    }
}
