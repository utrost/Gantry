package org.trostheide.gantry.vectorize.strategies;

import georegression.struct.point.Point2D_I32;
import org.trostheide.gantry.vectorize.PolylineGeometry;
import org.trostheide.gantry.vectorize.VectorGeometry;
import org.trostheide.gantry.vectorize.VectorizationStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * Douglas-Peucker simplification strategy with consistent detailFactor handling.
 */
public class DouglasPeuckerStrategy implements VectorizationStrategy {


    @Override
    public VectorGeometry processContour(List<Point2D_I32> rawPoints,
                                         double tolerance,
                                         double detailFactor,
                                         double minLength,  // Unused
                                         double maxLength) { // Unused
        if (rawPoints == null || rawPoints.size() < 2) {
            // Return an empty, renderable polyline
            return new PolylineGeometry(new ArrayList<>());
        }

        // Clamp detailFactor to [0.0, 1.0]
        detailFactor = Math.max(0.0, Math.min(1.0, detailFactor));

        // Adjust tolerance: smaller detailFactor → coarser simplification
        double adjustedTolerance = computeEffectiveTolerance(tolerance, detailFactor);

        List<Point2D_I32> simplified = simplify(rawPoints, adjustedTolerance);

        // Wrap the result in an SvgPolyline object
        return new PolylineGeometry(simplified);
    }

    @Override
    public String getName() {
        return "DOUGLAS_PEUCKER";
    }

    @Override
    public double computeEffectiveTolerance(double baseTolerance, double detailFactor) {
        // Smaller detailFactor → coarser simplification
        detailFactor = Math.max(0.0, Math.min(1.0, detailFactor));
        return baseTolerance * (1.0 + (1.0 - detailFactor));
    }

    private List<Point2D_I32> simplify(List<Point2D_I32> points, double tol) {
        List<Point2D_I32> result = new ArrayList<>();
        simplifyRecursive(points, 0, points.size() - 1, tol, result);
        return result;
    }

    private void simplifyRecursive(List<Point2D_I32> points, int start, int end, double tol, List<Point2D_I32> result) {
        if (start >= end) return;

        double maxDist = -1;
        int index = -1;
        Point2D_I32 p1 = points.get(start);
        Point2D_I32 p2 = points.get(end);

        for (int i = start + 1; i < end; i++) {
            double dist = perpendicularDistance(points.get(i), p1, p2);
            if (dist > maxDist) {
                maxDist = dist;
                index = i;
            }
        }

        if (maxDist > tol) {
            simplifyRecursive(points, start, index, tol, result);
            simplifyRecursive(points, index, end, tol, result);
        } else {
            if (result.isEmpty() || !result.get(result.size() - 1).equals(p1))
                result.add(p1);
            result.add(p2);
        }
    }

    private double perpendicularDistance(Point2D_I32 p, Point2D_I32 lineStart, Point2D_I32 lineEnd) {
        double dx = lineEnd.x - lineStart.x;
        double dy = lineEnd.y - lineStart.y;
        if (dx == 0 && dy == 0) {
            dx = p.x - lineStart.x;
            dy = p.y - lineStart.y;
            return Math.sqrt(dx * dx + dy * dy);
        }
        double t = ((p.x - lineStart.x) * dx + (p.y - lineStart.y) * dy) / (dx * dx + dy * dy);
        double projX = lineStart.x + t * dx;
        double projY = lineStart.y + t * dy;
        double distX = p.x - projX;
        double distY = p.y - projY;
        return Math.sqrt(distX * distX + distY * distY);
    }
}
