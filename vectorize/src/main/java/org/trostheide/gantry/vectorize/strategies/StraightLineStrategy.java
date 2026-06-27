package org.trostheide.gantry.vectorize.strategies;

import georegression.struct.point.Point2D_I32;
import org.trostheide.gantry.vectorize.PolylineGeometry;
import org.trostheide.gantry.vectorize.VectorGeometry;
import org.trostheide.gantry.vectorize.VectorizationStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * Strategy that fits a single best-fit line (least squares) through each contour.
 * This strategy handles its own min/max length filtering, as the detailFactor
 * is used to adjust the effective filtering thresholds.
 */
public class StraightLineStrategy implements VectorizationStrategy {

    @Override
    public VectorGeometry processContour(List<Point2D_I32> rawPoints,
                                         double tolerance, // Unused in this strategy
                                         double detailFactor,
                                         double minLength,
                                         double maxLength) {
        if (rawPoints == null || rawPoints.size() < 2) {
            return new PolylineGeometry(new ArrayList<>());
        }

        // Clamp detailFactor to [0.0, 1.0]
        detailFactor = Math.max(0.0, Math.min(1.0, detailFactor));

        // Adjust min/max lengths based on detailFactor
        double effectiveMinLength = minLength * (1.0 + (1.0 - detailFactor) * 1.0);

        double effectiveMaxLength = maxLength;
        if (maxLength > 0) {
            effectiveMaxLength = maxLength * (1.0 - (1.0 - detailFactor) * 0.5);
        }

        // Compute centroid (mean position)
        double meanX = 0, meanY = 0;
        for (Point2D_I32 p : rawPoints) {
            meanX += p.x;
            meanY += p.y;
        }
        meanX /= rawPoints.size();
        meanY /= rawPoints.size();

        // Compute covariance terms
        double Sxx = 0, Sxy = 0, Syy = 0;
        for (Point2D_I32 p : rawPoints) {
            double dx = p.x - meanX;
            double dy = p.y - meanY;
            Sxx += dx * dx;
            Sxy += dx * dy;
            Syy += dy * dy;
        }

        // Compute dominant direction (principal axis)
        double theta = 0.5 * Math.atan2(2 * Sxy, Sxx - Syy);
        double dirX = Math.cos(theta);
        double dirY = Math.sin(theta);

        // Project points onto that direction and find endpoints
        double minProj = Double.POSITIVE_INFINITY;
        double maxProj = Double.NEGATIVE_INFINITY;
        for (Point2D_I32 p : rawPoints) {
            double proj = (p.x - meanX) * dirX + (p.y - meanY) * dirY;
            if (proj < minProj) minProj = proj;
            if (proj > maxProj) maxProj = proj;
        }

        // Convert projections to coordinates
        Point2D_I32 start = new Point2D_I32(
                (int) Math.round(meanX + minProj * dirX),
                (int) Math.round(meanY + minProj * dirY)
        );
        Point2D_I32 end = new Point2D_I32(
                (int) Math.round(meanX + maxProj * dirX),
                (int) Math.round(meanY + maxProj * dirY)
        );

        // Compute actual line length
        double length = Math.hypot(end.x - start.x, end.y - start.y);

        // Apply min/max length filtering using effective lengths
        if (length < effectiveMinLength) {
            return new PolylineGeometry(new ArrayList<>());
        }
        if (effectiveMaxLength > 0 && length > effectiveMaxLength) {
            return new PolylineGeometry(new ArrayList<>());
        }

        List<Point2D_I32> simplified = new ArrayList<>();
        simplified.add(start);
        simplified.add(end);

        // Wrap the result in an SvgPolyline
        return new PolylineGeometry(simplified);
    }

    @Override
    public String getName() {
        return "line"; // Use lowercase to match CliParser
    }

    @Override
    public double computeEffectiveTolerance(double baseTolerance, double detailFactor) {
        // This strategy doesn't use 'tolerance', so we just return the base.
        return baseTolerance;
    }
}