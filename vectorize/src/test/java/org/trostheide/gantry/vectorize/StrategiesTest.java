package org.trostheide.gantry.vectorize;

import georegression.struct.point.Point2D_I32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.trostheide.gantry.vectorize.strategies.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StrategiesTest {

    // --- Helper ---
    private static List<Point2D_I32> pts(int... coords) {
        List<Point2D_I32> list = new ArrayList<>();
        for (int i = 0; i < coords.length; i += 2) {
            list.add(new Point2D_I32(coords[i], coords[i + 1]));
        }
        return list;
    }

    private static List<Point2D_I32> makeSquare(int size) {
        return pts(0, 0, size, 0, size, size, 0, size, 0, 0);
    }

    @Nested
    class DouglasPeuckerStrategyTest {

        private final DouglasPeuckerStrategy strategy = new DouglasPeuckerStrategy();

        @Test
        void name() {
            assertEquals("DOUGLAS_PEUCKER", strategy.getName());
        }

        @Test
        void nullInput_returnsEmptyPolyline() {
            VectorGeometry result = strategy.processContour(null, 2.0);
            assertInstanceOf(PolylineGeometry.class, result);
            assertTrue(((PolylineGeometry) result).points.isEmpty());
        }

        @Test
        void singlePoint_returnsEmptyPolyline() {
            VectorGeometry result = strategy.processContour(pts(5, 5), 2.0);
            assertInstanceOf(PolylineGeometry.class, result);
            assertTrue(((PolylineGeometry) result).points.isEmpty());
        }

        @Test
        void straightLine_simplifiesToTwoPoints() {
            // A perfectly straight line should simplify to just start and end
            List<Point2D_I32> line = pts(0, 0, 5, 5, 10, 10, 15, 15, 20, 20);
            VectorGeometry result = strategy.processContour(line, 1.0);

            assertInstanceOf(PolylineGeometry.class, result);
            List<Point2D_I32> simplified = ((PolylineGeometry) result).points;
            assertEquals(2, simplified.size());
            assertEquals(0, simplified.get(0).x);
            assertEquals(20, simplified.get(1).x);
        }

        @Test
        void squareShape_preservesCorners() {
            List<Point2D_I32> square = makeSquare(100);
            VectorGeometry result = strategy.processContour(square, 1.0);

            assertInstanceOf(PolylineGeometry.class, result);
            List<Point2D_I32> simplified = ((PolylineGeometry) result).points;
            // Square has 4 corners + closing point = at least 4 points should remain
            assertTrue(simplified.size() >= 4, "Square should preserve at least 4 corners");
        }

        @Test
        void higherTolerance_fewerPoints() {
            // Create a noisy curve
            List<Point2D_I32> noisy = pts(
                    0, 0, 5, 3, 10, 1, 15, 4, 20, 2, 25, 5, 30, 1, 35, 3, 40, 0);

            PolylineGeometry lowTol = (PolylineGeometry) strategy.processContour(noisy, 0.5, 1.0, 0, 0);
            PolylineGeometry highTol = (PolylineGeometry) strategy.processContour(noisy, 5.0, 1.0, 0, 0);

            assertTrue(highTol.points.size() <= lowTol.points.size(),
                    "Higher tolerance should produce same or fewer points");
        }

        @Test
        void detailFactor_affectsSimplification() {
            List<Point2D_I32> curve = pts(
                    0, 0, 10, 5, 20, 2, 30, 8, 40, 3, 50, 7, 60, 0);

            // detailFactor 0.0 = coarse (doubles tolerance)
            PolylineGeometry coarse = (PolylineGeometry) strategy.processContour(curve, 2.0, 0.0, 0, 0);
            // detailFactor 1.0 = full detail (uses base tolerance)
            PolylineGeometry fine = (PolylineGeometry) strategy.processContour(curve, 2.0, 1.0, 0, 0);

            assertTrue(coarse.points.size() <= fine.points.size(),
                    "Coarse detail factor should produce same or fewer points");
        }

        @Test
        void effectiveTolerance_calculation() {
            // detailFactor 1.0 → tolerance * 1.0
            assertEquals(2.0, strategy.computeEffectiveTolerance(2.0, 1.0), 0.001);
            // detailFactor 0.0 → tolerance * 2.0
            assertEquals(4.0, strategy.computeEffectiveTolerance(2.0, 0.0), 0.001);
            // detailFactor 0.5 → tolerance * 1.5
            assertEquals(3.0, strategy.computeEffectiveTolerance(2.0, 0.5), 0.001);
        }
    }

    @Nested
    class RawContourStrategyTest {

        private final RawContourStrategy strategy = new RawContourStrategy();

        @Test
        void name() {
            assertEquals("RAW", strategy.getName());
        }

        @Test
        void nullInput_returnsEmptyPolyline() {
            VectorGeometry result = strategy.processContour(null, 2.0);
            assertInstanceOf(PolylineGeometry.class, result);
            assertTrue(((PolylineGeometry) result).points.isEmpty());
        }

        @Test
        void returnsAllPoints_unchanged() {
            List<Point2D_I32> original = pts(0, 0, 10, 10, 20, 20);
            VectorGeometry result = strategy.processContour(original, 100.0);

            assertInstanceOf(PolylineGeometry.class, result);
            List<Point2D_I32> points = ((PolylineGeometry) result).points;
            assertEquals(3, points.size());
            // Should be a copy, not the same reference
            assertNotSame(original, points);
        }
    }

    @Nested
    class StraightLineStrategyTest {

        private final StraightLineStrategy strategy = new StraightLineStrategy();

        @Test
        void name() {
            assertEquals("line", strategy.getName());
        }

        @Test
        void nullInput_returnsEmptyPolyline() {
            VectorGeometry result = strategy.processContour(null, 2.0);
            assertInstanceOf(PolylineGeometry.class, result);
            assertTrue(((PolylineGeometry) result).points.isEmpty());
        }

        @Test
        void alwaysReturnsTwoPoints() {
            List<Point2D_I32> points = pts(0, 0, 5, 3, 10, 1, 15, 5, 20, 2);
            VectorGeometry result = strategy.processContour(points, 2.0, 1.0, 0.0, 0.0);

            assertInstanceOf(PolylineGeometry.class, result);
            List<Point2D_I32> simplified = ((PolylineGeometry) result).points;
            assertEquals(2, simplified.size(), "Straight line strategy always produces exactly 2 endpoints");
        }

        @Test
        void horizontalLine_fitsCorrectly() {
            List<Point2D_I32> horizontal = pts(0, 50, 10, 50, 20, 50, 30, 50, 40, 50);
            VectorGeometry result = strategy.processContour(horizontal, 2.0, 1.0, 0.0, 0.0);

            List<Point2D_I32> simplified = ((PolylineGeometry) result).points;
            assertEquals(2, simplified.size());
            // Both points should be at y=50
            assertEquals(50, simplified.get(0).y);
            assertEquals(50, simplified.get(1).y);
        }

        @Test
        void minLength_filtersShortLines() {
            List<Point2D_I32> shortLine = pts(0, 0, 3, 0);
            VectorGeometry result = strategy.processContour(shortLine, 2.0, 1.0, 10.0, 0.0);

            List<Point2D_I32> simplified = ((PolylineGeometry) result).points;
            assertTrue(simplified.isEmpty(), "Line shorter than minLength should be filtered out");
        }
    }

    @Nested
    class ConvexHullStrategyTest {

        private final ConvexHullStrategy strategy = new ConvexHullStrategy();

        @Test
        void name() {
            assertEquals("convexhull", strategy.getName());
        }

        @Test
        void nullInput_returnsEmptyPolyline() {
            VectorGeometry result = strategy.processContour(null, 2.0);
            assertInstanceOf(PolylineGeometry.class, result);
            assertTrue(((PolylineGeometry) result).points.isEmpty());
        }

        @Test
        void squareInput_producesConvexHull() {
            // Interior points should be excluded from hull
            List<Point2D_I32> pointsWithInterior = pts(
                    0, 0, 100, 0, 100, 100, 0, 100,  // corners
                    50, 50, 25, 75, 60, 30);           // interior points

            VectorGeometry result = strategy.processContour(pointsWithInterior, 2.0);

            assertInstanceOf(PolylineGeometry.class, result);
            List<Point2D_I32> hull = ((PolylineGeometry) result).points;
            // Hull of a square with interior points should have 4 corners + closing point = 5
            assertTrue(hull.size() <= 6, "Hull should have ~5 points for a square (4 corners + close)");
            assertTrue(hull.size() >= 4, "Hull should have at least 4 points for a square");
        }

        @Test
        void tooFewPoints_returnsEmpty() {
            VectorGeometry result = strategy.processContour(pts(10, 20, 30, 40), 2.0);
            assertInstanceOf(PolylineGeometry.class, result);
            assertTrue(((PolylineGeometry) result).points.isEmpty());
        }
    }

    @Nested
    class BezierStrategyTest {

        @Test
        void name() {
            assertEquals("bezier", new BezierStrategy().getName());
        }

        @Test
        void processContour_throwsUnsupported() {
            assertThrows(UnsupportedOperationException.class,
                    () -> new BezierStrategy().processContour(pts(0, 0, 10, 10), 2.0, 1.0, 0, 0));
        }
    }

    @Nested
    class ImageTracerStrategyTest {

        @Test
        void name() {
            assertEquals("bezier2", new ImageTracerStrategy().getName());
        }

        @Test
        void processContour_throwsUnsupported() {
            assertThrows(UnsupportedOperationException.class,
                    () -> new ImageTracerStrategy().processContour(pts(0, 0, 10, 10), 2.0, 1.0, 0, 0));
        }
    }

    @Nested
    class SkeletonStrategyTest {

        @Test
        void name() {
            assertEquals("centerline", new SkeletonStrategy().getName());
        }

        @Test
        void processContour_throwsUnsupported() {
            assertThrows(UnsupportedOperationException.class,
                    () -> new SkeletonStrategy().processContour(pts(0, 0, 10, 10), 2.0, 1.0, 0, 0));
        }
    }

    @Nested
    class WorkflowTypeTest {

        @Test
        void cannyStrategies_returnCannyContour() {
            assertEquals(VectorizationStrategy.WorkflowType.CANNY_CONTOUR, new DouglasPeuckerStrategy().getWorkflowType());
            assertEquals(VectorizationStrategy.WorkflowType.CANNY_CONTOUR, new StraightLineStrategy().getWorkflowType());
            assertEquals(VectorizationStrategy.WorkflowType.CANNY_CONTOUR, new RawContourStrategy().getWorkflowType());
            assertEquals(VectorizationStrategy.WorkflowType.CANNY_CONTOUR, new ConvexHullStrategy().getWorkflowType());
        }

        @Test
        void wholeImageStrategies_returnCorrectType() {
            assertEquals(VectorizationStrategy.WorkflowType.WHOLE_IMAGE_BEZIER, new BezierStrategy().getWorkflowType());
            assertEquals(VectorizationStrategy.WorkflowType.WHOLE_IMAGE_IMAGETRACER, new ImageTracerStrategy().getWorkflowType());
        }

        @Test
        void skeletonStrategy_returnsSkeleton() {
            assertEquals(VectorizationStrategy.WorkflowType.SKELETON, new SkeletonStrategy().getWorkflowType());
        }

        @Test
        void pbnStrategy_returnsPaintByNumbers() {
            assertEquals(VectorizationStrategy.WorkflowType.PAINT_BY_NUMBERS, new PaintByNumbersStrategy().getWorkflowType());
        }
    }
}
