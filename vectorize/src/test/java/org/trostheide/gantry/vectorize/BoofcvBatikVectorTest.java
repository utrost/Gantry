package org.trostheide.gantry.vectorize;

import georegression.struct.point.Point2D_I32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoofcvBatikVectorTest {

    // --- Helper ---
    private static List<Point2D_I32> pts(int... coords) {
        List<Point2D_I32> list = new ArrayList<>();
        for (int i = 0; i < coords.length; i += 2) {
            list.add(new Point2D_I32(coords[i], coords[i + 1]));
        }
        return list;
    }

    @Nested
    class PolylineToCubicBezierPathTest {

        @Test
        void emptyOrSinglePoint_returnsEmpty() {
            assertEquals("", BoofcvBatikVector.polylineToCubicBezierPath(new ArrayList<>()));
            assertEquals("", BoofcvBatikVector.polylineToCubicBezierPath(
                    List.of(new Point2D_I32(0, 0))));
        }

        @Test
        void twoPoints_returnsLineTo() {
            String result = BoofcvBatikVector.polylineToCubicBezierPath(pts(10, 20, 30, 40));
            assertTrue(result.startsWith("M 10 20"));
            assertTrue(result.contains("L 30 40"));
            assertFalse(result.contains("C")); // no Bezier for 2 points
        }

        @Test
        void threePoints_returnsCubicBezier() {
            String result = BoofcvBatikVector.polylineToCubicBezierPath(pts(0, 0, 50, 50, 100, 0));
            assertTrue(result.startsWith("M 0 0"));
            assertTrue(result.contains("C")); // should have cubic Bezier commands
            // Should end with the last point
            assertTrue(result.contains("100 0"));
        }

        @Test
        void manyPoints_producesCorrectNumberOfSegments() {
            List<Point2D_I32> points = pts(0, 0, 10, 10, 20, 0, 30, 10, 40, 0);
            String result = BoofcvBatikVector.polylineToCubicBezierPath(points);

            // 5 points = 4 segments = 4 "C" commands
            int cCount = 0;
            int idx = 0;
            while ((idx = result.indexOf(" C ", idx)) != -1) {
                cCount++;
                idx++;
            }
            assertEquals(4, cCount);
        }

        @Test
        void curvesPassThroughOriginalPoints() {
            // The Catmull-Rom → Bezier conversion should produce curves that
            // pass through all original points (they appear as endpoints of C commands)
            List<Point2D_I32> points = pts(0, 0, 50, 100, 100, 0);
            String result = BoofcvBatikVector.polylineToCubicBezierPath(points);

            assertTrue(result.contains("M 0 0"));
            assertTrue(result.contains("50 100")); // intermediate point
            assertTrue(result.contains("100 0"));  // end point
        }

        @Test
        void closedShape_curvesPassThroughAllPoints() {
            // A closed shape where first ≈ last point (square-like)
            List<Point2D_I32> closedSquare = pts(0, 0, 100, 0, 100, 100, 0, 100, 0, 0);
            String result = BoofcvBatikVector.polylineToCubicBezierPath(closedSquare);

            assertFalse(result.isEmpty(), "Closed shape should produce a non-empty path");
            assertTrue(result.contains("M 0 0"), "Path should start with first point");
            // All intermediate points should appear in the path
            assertTrue(result.contains("100 0"), "Path should contain point (100,0)");
            assertTrue(result.contains("100 100"), "Path should contain point (100,100)");
            assertTrue(result.contains("0 100"), "Path should contain point (0,100)");
        }
    }

    @Nested
    class PathGeometryTest {

        @Test
        void constructorRejectsNull() {
            assertThrows(IllegalArgumentException.class, () -> new PathGeometry(null));
        }

        @Test
        void constructorStoresPathData() {
            PathGeometry pg = new PathGeometry("M 0 0 L 10 10");
            assertEquals("M 0 0 L 10 10", pg.pathData);
        }
    }

    @Nested
    class PolylineGeometryTest {

        @Test
        void constructorStoresPoints() {
            List<Point2D_I32> points = pts(1, 2, 3, 4);
            PolylineGeometry pg = new PolylineGeometry(points);
            assertEquals(2, pg.points.size());
            assertEquals(1, pg.points.get(0).x);
            assertEquals(2, pg.points.get(0).y);
        }

        @Test
        void implementsVectorGeometry() {
            PolylineGeometry pg = new PolylineGeometry(new ArrayList<>());
            assertInstanceOf(VectorGeometry.class, pg);
        }
    }
}
