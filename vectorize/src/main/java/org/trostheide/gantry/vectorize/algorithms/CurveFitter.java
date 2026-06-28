package org.trostheide.gantry.vectorize.algorithms;

import georegression.struct.point.Point2D_I32;
import java.util.List;
import java.util.Locale;

/**
 * Utility for fitting smooth curves to a set of points.
 * Uses Catmull-Rom spline to Cubic Bezier conversion.
 */
public class CurveFitter {

    /**
     * Converts a list of points into a smooth SVG path data string.
     * 
     * @param points List of points (polyline)
     * @return SVG path data string (e.g., "M 10,10 C ...")
     */
    public static String fitBezier(List<Point2D_I32> points) {
        if (points == null || points.size() < 2) {
            return "";
        }

        // If only 2 points, it's a straight line
        if (points.size() == 2) {
            Point2D_I32 p0 = points.get(0);
            Point2D_I32 p1 = points.get(1);
            return String.format("M %d,%d L %d,%d", p0.x, p0.y, p1.x, p1.y);
        }

        StringBuilder path = new StringBuilder();

        // Start
        Point2D_I32 p0 = points.get(0);
        path.append(String.format("M %d,%d", p0.x, p0.y));

        // Loop through segments
        for (int i = 0; i < points.size() - 1; i++) {
            // Catmull-Rom needs 4 points: p-1, p, p+1, p+2
            // We handle boundaries by duplicating end points
            Point2D_I32 pPrev = (i == 0) ? points.get(0) : points.get(i - 1);
            Point2D_I32 pStart = points.get(i);
            Point2D_I32 pEnd = points.get(i + 1);
            Point2D_I32 pNext = (i == points.size() - 2) ? points.get(i + 1) : points.get(i + 2);

            // Convert Catmull-Rom to Cubic Bezier Control Points
            // Formula:
            // cp1 = pStart + (pEnd - pPrev) / 6
            // cp2 = pEnd - (pNext - pStart) / 6

            double cp1x = pStart.x + (pEnd.x - pPrev.x) / 6.0;
            double cp1y = pStart.y + (pEnd.y - pPrev.y) / 6.0;

            double cp2x = pEnd.x - (pNext.x - pStart.x) / 6.0;
            double cp2y = pEnd.y - (pNext.y - pStart.y) / 6.0;

            path.append(String.format(Locale.ROOT, " C %.2f,%.2f %.2f,%.2f %d,%d",
                    cp1x, cp1y, cp2x, cp2y, pEnd.x, pEnd.y));
        }

        return path.toString();
    }
}
