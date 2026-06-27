package org.trostheide.gantry.vectorize.strategies;

import georegression.struct.point.Point2D_I32;
import org.trostheide.gantry.vectorize.PolylineGeometry;
import org.trostheide.gantry.vectorize.VectorGeometry;
import org.trostheide.gantry.vectorize.VectorizationStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * Strategy that returns raw contours without simplification.
 */
public class RawContourStrategy implements VectorizationStrategy {

    @Override
    public VectorGeometry processContour(List<Point2D_I32> rawPoints,
                                         double tolerance,    // Unused
                                         double detailFactor, // Unused
                                         double minLength,    // Unused
                                         double maxLength) {  // Unused
        if (rawPoints == null) {
            return new PolylineGeometry(new ArrayList<>());
        }
        // Clone the list to avoid modifying the original and wrap it
        return new PolylineGeometry(new ArrayList<>(rawPoints));
    }

    @Override
    public String getName() {
        return "RAW";
    }

    /**
     * Return effective tolerance for rendering/debugging.
     * For raw contours, tolerance is ignored.
     */
    @Override
    public double computeEffectiveTolerance(double baseTolerance, double detailFactor) {
        return baseTolerance; // no simplification
    }
}
