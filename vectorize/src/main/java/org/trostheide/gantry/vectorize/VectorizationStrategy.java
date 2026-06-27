package org.trostheide.gantry.vectorize;

import georegression.struct.point.Point2D_I32;
import java.util.List;

/**
 * Interface for all vectorization algorithms.
 *
 * Implementations will process raw contours and return a VectorGeometry object.
 */
public interface VectorizationStrategy {

    enum WorkflowType {
        CANNY_CONTOUR,
        WHOLE_IMAGE_BEZIER,
        WHOLE_IMAGE_IMAGETRACER,
        SKELETON,
        PAINT_BY_NUMBERS
    }

    default WorkflowType getWorkflowType() {
        return WorkflowType.CANNY_CONTOUR;
    }

    /**
     * Processes a raw contour into a vector geometry object (e.g., polyline or path).
     *
     * @param rawPoints    The raw pixel points from contour tracing.
     * @param tolerance    Simplification tolerance (max error distance in pixels).
     * @param detailFactor 0.0 = coarse (fewer points), 1.0 = full detail.
     * @param minLength    Minimum length (in pixels) for a contour to be kept.
     * @param maxLength    Maximum length (in pixels) for a contour to be kept.
     * @return A VectorGeometry object (e.g., PolylineGeometry).
     */
    VectorGeometry processContour(List<Point2D_I32> rawPoints,
                                  double tolerance,
                                  double detailFactor,
                                  double minLength,
                                  double maxLength);

    /**
     * Overloaded method for backward compatibility / simpler calls.
     * Defaults detailFactor to 1.0 and min/max lengths to 0.
     *
     * @param rawPoints The raw pixel points from contour tracing.
     * @param tolerance Simplification tolerance (max error distance in pixels).
     * @return A VectorGeometry object (e.g., PolylineGeometry).
     */
    default VectorGeometry processContour(List<Point2D_I32> rawPoints, double tolerance) {
        return processContour(rawPoints, tolerance, 1.0, 0.0, 0.0);
    }

    // ... (computeEffectiveTolerance and getName are unchanged) ...

    /**
     * Computes an "effective tolerance" for this strategy, optionally factoring in detail.
     * Default implementation just returns the base tolerance.
     *
     * @param baseTolerance The base tolerance from CLI or defaults.
     * @param detailFactor  Detail factor 0.0 → coarse, 1.0 → full.
     * @return The effective tolerance to use for simplification.
     */
    default double computeEffectiveTolerance(double baseTolerance, double detailFactor) {
        return baseTolerance;
    }

    /**
     * Gets the name of the strategy, used for CLI identification.
     */
    String getName();
}