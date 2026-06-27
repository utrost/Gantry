package org.trostheide.gantry.vectorize.strategies;

import georegression.struct.point.Point2D_I32;
import org.trostheide.gantry.vectorize.VectorGeometry;
import org.trostheide.gantry.vectorize.VectorizationStrategy;

import java.util.List;

/**
 * A strategy for vectorizing using a potrace-like algorithm (DrPTrace).
 *
 * This strategy is special: it does not operate on a list of contours.
 * Instead, it must be called from the main application loop with the
 * full BufferedImage.
 */
public class BezierStrategy implements VectorizationStrategy {

    @Override
    public VectorGeometry processContour(List<Point2D_I32> rawPoints,
                                         double tolerance,
                                         double detailFactor,
                                         double minLength,
                                         double maxLength) {
        // This method should never be called.
        // The main() method is expected to catch this strategy and
        // call a different, dedicated processing method.
        throw new UnsupportedOperationException(
                "BezierStrategy cannot be run on a single contour. " +
                        "It must be run on the entire image."
        );
    }

    @Override
    public WorkflowType getWorkflowType() {
        return WorkflowType.WHOLE_IMAGE_BEZIER;
    }

    @Override
    public String getName() {
        return "bezier";
    }
}