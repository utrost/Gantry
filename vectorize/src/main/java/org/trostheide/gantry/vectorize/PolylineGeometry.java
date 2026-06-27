package org.trostheide.gantry.vectorize;

import georegression.struct.point.Point2D_I32;
import java.util.List;

/**
 * A VectorGeometry implementation that holds a list of points
 * representing a polyline.
 */
public class PolylineGeometry implements VectorGeometry {

    public final List<Point2D_I32> points;

    public PolylineGeometry(List<Point2D_I32> points) {
        this.points = points;
    }
}