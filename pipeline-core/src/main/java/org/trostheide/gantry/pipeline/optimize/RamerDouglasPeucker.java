package org.trostheide.gantry.pipeline.optimize;

import org.trostheide.gantry.model.Point;

import java.util.ArrayList;
import java.util.List;

/** Ramer-Douglas-Peucker polyline simplification, ported from SVGToolBox's {@code SimplifyProcessor}. */
public final class RamerDouglasPeucker {

    private RamerDouglasPeucker() {
    }

    /** Returns a simplified copy of {@code points}, dropping points within {@code epsilon} of the chord. */
    public static List<Point> simplify(List<Point> points, double epsilon) {
        if (points.size() < 3 || epsilon <= 0) {
            return new ArrayList<>(points);
        }
        return simplify(points, 0, points.size() - 1, epsilon);
    }

    private static List<Point> simplify(List<Point> points, int start, int end, double epsilon) {
        double maxDist = 0;
        int index = start;
        for (int i = start + 1; i < end; i++) {
            double d = perpendicularDistance(points.get(i), points.get(start), points.get(end));
            if (d > maxDist) {
                maxDist = d;
                index = i;
            }
        }

        List<Point> result = new ArrayList<>();
        if (maxDist > epsilon) {
            List<Point> left = simplify(points, start, index, epsilon);
            List<Point> right = simplify(points, index, end, epsilon);
            result.addAll(left.subList(0, left.size() - 1));
            result.addAll(right);
        } else {
            result.add(points.get(start));
            result.add(points.get(end));
        }
        return result;
    }

    private static double perpendicularDistance(Point p, Point lineStart, Point lineEnd) {
        double area = Math.abs(0.5 * (lineStart.x() * lineEnd.y() + lineEnd.x() * p.y() + p.x() * lineStart.y()
                - lineEnd.x() * lineStart.y() - p.x() * lineEnd.y() - lineStart.x() * p.y()));
        double bottom = Math.hypot(lineStart.x() - lineEnd.x(), lineStart.y() - lineEnd.y());
        if (bottom == 0) {
            // Degenerate segment (e.g. a closed path's start == end): fall back to point distance
            // so closed strokes aren't mistaken for perfectly-collinear points and collapsed away.
            return Math.hypot(p.x() - lineStart.x(), p.y() - lineStart.y());
        }
        return (area * 2.0) / bottom;
    }
}
