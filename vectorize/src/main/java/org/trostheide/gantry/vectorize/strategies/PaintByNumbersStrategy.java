package org.trostheide.gantry.vectorize.strategies;

import georegression.struct.point.Point2D_I32;
import org.trostheide.gantry.vectorize.VectorGeometry;
import org.trostheide.gantry.vectorize.VectorizationStrategy;

import java.util.List;

public class PaintByNumbersStrategy implements VectorizationStrategy {

    @Override
    public VectorGeometry processContour(List<Point2D_I32> rawPoints,
                                         double tolerance,
                                         double detailFactor,
                                         double minLength,
                                         double maxLength) {
        throw new UnsupportedOperationException(
                "PaintByNumbersStrategy cannot be run on a single contour. " +
                        "It must be run on the entire image."
        );
    }

    @Override
    public WorkflowType getWorkflowType() {
        return WorkflowType.PAINT_BY_NUMBERS;
    }

    @Override
    public String getName() {
        return "pbn";
    }
}
