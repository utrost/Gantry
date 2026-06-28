package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;

import java.awt.geom.Path2D;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Flood-fill enclosure detection from multiple separate strokes (Phase 10 Tier 3, multi-stroke). */
class EnclosedRegionTest {

    /** Four separate edges forming a square boundary [0,100]² (each a distinct stroke). */
    private static List<double[][]> squareAsFourStrokes() {
        return List.of(
                new double[][]{{0, 0}, {100, 0}},     // bottom
                new double[][]{{100, 0}, {100, 100}},  // right
                new double[][]{{100, 100}, {0, 100}},  // top
                new double[][]{{0, 100}, {0, 0}});     // left
    }

    @Test
    void fillsAreaEnclosedBySeparateStrokes() {
        Path2D region = EnclosedRegion.fromSeed(squareAsFourStrokes(), 50, 50);
        assertNotNull(region, "a seed inside four edges that meet should be enclosed");
        assertTrue(region.contains(50, 50), "the traced region contains the seed");
        var b = region.getBounds2D();
        // The traced boundary hugs the inside of the walls; it should roughly span the square.
        assertTrue(b.getWidth() > 80 && b.getHeight() > 80, "region spans most of the square: " + b);
        assertTrue(b.getX() >= -5 && b.getMaxX() <= 105, "region stays within the square: " + b);
    }

    @Test
    void seedOutsideTheEnclosureIsNotEnclosed() {
        // A point well outside the square: the fill reaches the raster border ⇒ null.
        assertNull(EnclosedRegion.fromSeed(squareAsFourStrokes(), 150, 50));
    }

    @Test
    void leakyBoundaryIsNotEnclosed() {
        // Same square but with the right edge missing: the fill escapes through the gap.
        List<double[][]> leaky = List.of(
                new double[][]{{0, 0}, {100, 0}},
                new double[][]{{100, 100}, {0, 100}},
                new double[][]{{0, 100}, {0, 0}});
        assertNull(EnclosedRegion.fromSeed(leaky, 50, 50), "an open boundary doesn't enclose the seed");
    }

    @Test
    void emptyInputYieldsNull() {
        assertNull(EnclosedRegion.fromSeed(List.of(), 0, 0));
    }
}
