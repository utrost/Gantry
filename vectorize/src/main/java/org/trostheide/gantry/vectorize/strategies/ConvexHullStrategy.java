package org.trostheide.gantry.vectorize.strategies;

// --- Imports from GeoRegression (for the F64 algorithm) ---
import georegression.fitting.polygon.FitPolygon2D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.shapes.Polygon2D_F64;

// --- Imports from BoofCV (for the I32 types) ---
import georegression.struct.point.Point2D_I32;
import org.trostheide.gantry.vectorize.PolylineGeometry;
import org.trostheide.gantry.vectorize.VectorGeometry;
import org.trostheide.gantry.vectorize.VectorizationStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * A vectorization strategy that computes the convex hull of a contour.
 *
 * This implementation converts the integer points to double-precision (F64)
 * to use the convex hull algorithm in GeoRegression, then converts the
 * resulting hull points back to integers (I32).
 */
public class ConvexHullStrategy implements VectorizationStrategy {

    // Pre-allocate objects to re-use in the processContour method.
    // This avoids creating new objects for every contour, which is more efficient.
    // NOTE: This assumes single-threaded execution (which is true for a CLI app).
    private final List<Point2D_F64> pointsF64 = new ArrayList<>();
    private final Polygon2D_F64 hullF64 = new Polygon2D_F64();

    public ConvexHullStrategy() {
        // Constructor is empty, state is pre-initialized.
    }

    @Override
    public VectorGeometry processContour(List<Point2D_I32> rawPoints,
                                         double tolerance,    // Unused
                                         double detailFactor, // Unused
                                         double minLength,    // Unused
                                         double maxLength) {  // Unused

        // A convex hull requires at least 3 points.
        if (rawPoints == null || rawPoints.size() < 3) {
            return new PolylineGeometry(new ArrayList<>());
        }

        // --- 1. Convert I32 points to F64 points ---
        pointsF64.clear();
        for (Point2D_I32 p : rawPoints) {
            pointsF64.add(new Point2D_F64(p.x, p.y));
        }

        // --- 2. Calculate the convex hull ---
        FitPolygon2D_F64.convexHull(pointsF64, hullF64);

        // --- 3. Convert F64 hull vertices back to I32 ---
        List<Point2D_I32> simplified = new ArrayList<>();
        for (int i = 0; i < hullF64.size(); i++) {
            Point2D_F64 v = hullF64.get(i);
            // Round the double coordinate and cast to int
            simplified.add(new Point2D_I32((int) (v.x + 0.5), (int) (v.y + 0.5)));
        }

        // Close the loop for polyline rendering
        if (simplified.size() > 1) {
            simplified.add(simplified.get(0)); // Add the first point again to the end
        }

        // Wrap the final list in an SvgPolyline
        return new PolylineGeometry(simplified);
    }

    @Override
    public String getName() {
        return "convexhull";
    }
}