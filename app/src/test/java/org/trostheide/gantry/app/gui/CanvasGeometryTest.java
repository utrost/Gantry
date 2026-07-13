package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanvasGeometryTest {
    @Test void projectsOntoSegmentAndClampsToEndpoints() {
        assertArrayEquals(new double[]{5, 0}, CanvasGeometry.closestOnSegment(5, 4, 0, 0, 10, 0), 1e-9);
        assertArrayEquals(new double[]{10, 0}, CanvasGeometry.closestOnSegment(20, 3, 0, 0, 10, 0), 1e-9);
        assertEquals(4, CanvasGeometry.distanceToSegment(5, 4, 0, 0, 10, 0), 1e-9);
    }
    @Test void degenerateSegmentIsAPoint() {
        assertArrayEquals(new double[]{2, 3}, CanvasGeometry.closestOnSegment(9, 9, 2, 3, 2, 3), 1e-9);
    }
}
