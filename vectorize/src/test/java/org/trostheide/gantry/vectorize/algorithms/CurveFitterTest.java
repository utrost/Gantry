package org.trostheide.gantry.vectorize.algorithms;

import georegression.struct.point.Point2D_I32;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class CurveFitterTest {

    /**
     * Regression: {@code fitBezier} (used by the centerline and Bézier strategies) formatted its
     * cubic control points with the default locale, so in a comma-decimal locale (e.g. de_DE) it
     * emitted "560,83" — which SVG reads as the two coordinates 560 and 83, corrupting the path
     * and tripping the importer/preview with "Unexpected character ('C' ...)". The path must use
     * '.' decimals regardless of the default locale.
     */
    @Test
    void bezierPathIsLocaleIndependent() {
        List<Point2D_I32> points = List.of(
                new Point2D_I32(0, 0), new Point2D_I32(10, 20), new Point2D_I32(30, 25),
                new Point2D_I32(50, 10), new Point2D_I32(70, 0));

        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            String us = CurveFitter.fitBezier(points);
            Locale.setDefault(Locale.GERMANY);
            String de = CurveFitter.fitBezier(points);

            assertEquals(us, de, "path data must not depend on the locale's decimal separator");
            assertTrue(de.contains("C"), "five points should fit at least one cubic Bézier");
            assertTrue(de.contains("."), "fractional control points use a dot decimal");
        } finally {
            Locale.setDefault(original);
        }
    }
}
