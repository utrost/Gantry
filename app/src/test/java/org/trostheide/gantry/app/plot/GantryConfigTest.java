package org.trostheide.gantry.app.plot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GantryConfigTest {

    private static GantryConfig landscape(String origin) {
        GantryConfig c = new GantryConfig();
        c.gcode.machineWidth = 841.0;
        c.gcode.machineHeight = 594.0;
        c.orientation = "Landscape";
        c.machineOrigin = origin;
        return c;
    }

    @Test
    void extraInvertCancelsOriginDerivedInversion() {
        // A bottom origin gives a baseline inverted Y; Extra Invert Y must be able to cancel it
        // (the bug: with OR it could not, so a bottom-origin machine's reversed jog was unfixable).
        GantryConfig c = landscape("Bottom-Left");
        assertTrue(c.toPlotSettings().invertY, "bottom origin inverts Y by default");

        c.invertY = true;
        assertFalse(c.toPlotSettings().invertY, "Extra Invert Y cancels the bottom-origin inversion");
    }

    @Test
    void rightOriginInvertsXAndIsCancelable() {
        GantryConfig c = landscape("Top-Right");
        assertTrue(c.toPlotSettings().invertX, "right origin inverts X");

        c.invertX = true;
        assertFalse(c.toPlotSettings().invertX, "Extra Invert X cancels the right-origin inversion");
    }

    @Test
    void extraInvertAddsInversionWhenOriginDoesNot() {
        GantryConfig c = landscape("Top-Left"); // neither right nor bottom
        assertFalse(c.toPlotSettings().invertX);
        assertFalse(c.toPlotSettings().invertY);

        c.invertX = true;
        c.invertY = true;
        assertTrue(c.toPlotSettings().invertX, "Extra Invert X adds inversion a top-left origin lacks");
        assertTrue(c.toPlotSettings().invertY, "Extra Invert Y adds inversion a top-left origin lacks");
    }

    @Test
    void defaultExtraInvertOffMatchesOriginBaseline() {
        // Regression guard: with Extra Invert off, behaviour is unchanged from before (origin-only).
        assertFalse(landscape("Top-Left").toPlotSettings().invertX);
        assertFalse(landscape("Top-Left").toPlotSettings().invertY);
        assertTrue(landscape("Bottom-Right").toPlotSettings().invertX);
        assertTrue(landscape("Bottom-Right").toPlotSettings().invertY);
    }

    @Test
    void exposesBothPhysicalOriginComponentsToAlignmentPipeline() {
        PlotSettings topRight = landscape("Top-Right").toPlotSettings();
        assertTrue(topRight.originRight);
        assertFalse(topRight.originBottom);

        PlotSettings bottomLeft = landscape("Bottom-Left").toPlotSettings();
        assertFalse(bottomLeft.originRight);
        assertTrue(bottomLeft.originBottom);
    }

    @Test
    void portraitKeepsUserFacingAlignmentLabel() {
        GantryConfig c = landscape("Top-Right");
        c.orientation = "Portrait";
        c.canvasAlignment = "Bottom Left";

        assertEquals("Bottom Left", c.toPlotSettings().canvasAlign);
    }
}
