package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Zoom-to-cursor math for the Live View viewport (Phase 10 Tier 3 prerequisite). The paint
 * transform is {@code finalPixel = content*(fitScale*viewZoom) + (viewZoom*fitTx + viewPan)}; a
 * wheel zoom must re-solve {@code viewPan} so the content under the cursor stays put, or the
 * inverse hit-test (which reuses the same cached transform) would drift.
 */
class VisualizationViewTest {

    /** The content coordinate currently under screen pixel {@code m}, per the composed transform. */
    private static double contentUnderCursor(double fitScale, double fitTx,
                                             double zoom, double pan, double m) {
        double scale = fitScale * zoom;
        double tx = zoom * fitTx + pan;
        return (m - tx) / scale;
    }

    @Test
    void zoomInKeepsContentUnderCursorFixed() {
        double fitScale = 2.0, fitTx = 30.0;
        double z0 = 1.5, pan0 = 10.0, cursor = 200.0;

        double before = contentUnderCursor(fitScale, fitTx, z0, pan0, cursor);

        double z1 = 3.0;
        double pan1 = VisualizationPanel.zoomToCursorPan(pan0, z0, z1, cursor);
        double after = contentUnderCursor(fitScale, fitTx, z1, pan1, cursor);

        assertEquals(before, after, 1e-9, "content under the cursor must not move when zooming in");
    }

    @Test
    void zoomOutKeepsContentUnderCursorFixed() {
        double fitScale = 1.25, fitTx = -12.0;
        double z0 = 4.0, pan0 = -75.0, cursor = 410.0;

        double before = contentUnderCursor(fitScale, fitTx, z0, pan0, cursor);

        double z1 = 1.0;
        double pan1 = VisualizationPanel.zoomToCursorPan(pan0, z0, z1, cursor);
        double after = contentUnderCursor(fitScale, fitTx, z1, pan1, cursor);

        assertEquals(before, after, 1e-9, "content under the cursor must not move when zooming out");
    }

    @Test
    void noZoomChangeLeavesPanUntouched() {
        assertEquals(42.0, VisualizationPanel.zoomToCursorPan(42.0, 2.0, 2.0, 300.0), 1e-12);
    }
}
