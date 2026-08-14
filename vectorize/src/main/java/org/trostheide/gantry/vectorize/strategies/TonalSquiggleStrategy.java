package org.trostheide.gantry.vectorize.strategies;

import georegression.struct.point.Point2D_I32;
import org.trostheide.gantry.vectorize.PolylineGeometry;
import org.trostheide.gantry.vectorize.VectorGeometry;
import org.trostheide.gantry.vectorize.VectorizationStrategy;

import java.util.List;

/** Marker strategy for the whole-image tonal squiggle raster workflow. */
public class TonalSquiggleStrategy implements VectorizationStrategy {
    @Override
    public WorkflowType getWorkflowType() {
        return WorkflowType.TONAL_SQUIGGLE;
    }

    @Override
    public VectorGeometry processContour(List<Point2D_I32> rawPoints, double tolerance, double detailFactor,
                                         double minLength, double maxLength) {
        return new PolylineGeometry(List.of());
    }

    @Override
    public String getName() {
        return "squiggle";
    }
}
