package org.trostheide.gantry.pipeline.optimize;

import org.trostheide.gantry.model.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Greedy nearest-neighbor + 2-opt travel-minimizing reorder, ported from SVGToolBox's
 * {@code PathOptimizeProcessor}, generalized to operate on (start, end) points of any stroke.
 */
public final class PathOptimizer {

    private PathOptimizer() {
    }

    /** A single pen-down stroke, identified by its travel-in start point and travel-out end point. */
    public record Stroke(Point start, Point end) {
    }

    /**
     * Returns the indices of {@code strokes} reordered to (heuristically) minimize total travel
     * distance, starting from {@code origin}.
     */
    public static List<Integer> optimizeOrder(List<Stroke> strokes, Point origin) {
        return optimizeOrder(strokes, origin, () -> false);
    }

    static List<Integer> optimizeOrder(List<Stroke> strokes, Point origin,
            BooleanSupplier cancellationRequested) {
        int n = strokes.size();
        List<Integer> route = new ArrayList<>();
        if (n == 0) {
            return route;
        }

        Set<Integer> remaining = new LinkedHashSet<>();
        for (int i = 0; i < n; i++) {
            remaining.add(i);
        }

        Point current = origin;
        while (!remaining.isEmpty()) {
            OptimizeStage.checkCancelled(cancellationRequested);
            int best = -1;
            double bestDist = Double.MAX_VALUE;
            for (int idx : remaining) {
                OptimizeStage.checkCancelled(cancellationRequested);
                double d = distSq(current, strokes.get(idx).start());
                if (d < bestDist) {
                    bestDist = d;
                    best = idx;
                }
            }
            route.add(best);
            remaining.remove(best);
            current = strokes.get(best).end();
        }

        twoOptImprove(route, strokes, cancellationRequested);
        return route;
    }

    private static void twoOptImprove(List<Integer> route, List<Stroke> strokes,
            BooleanSupplier cancellationRequested) {
        if (route.size() < 4) {
            return;
        }
        boolean improved = true;
        int maxIterations = 5;
        while (improved && maxIterations-- > 0) {
            OptimizeStage.checkCancelled(cancellationRequested);
            improved = false;
            for (int i = 0; i < route.size() - 2; i++) {
                for (int j = i + 2; j < route.size(); j++) {
                    OptimizeStage.checkCancelled(cancellationRequested);
                    double currentDist = segmentDistance(route, strokes, i, j);
                    double swappedDist = swappedSegmentDistance(route, strokes, i, j);
                    if (swappedDist < currentDist - 0.001) {
                        Collections.reverse(route.subList(i + 1, j + 1));
                        improved = true;
                    }
                }
            }
        }
    }

    private static double segmentDistance(List<Integer> route, List<Stroke> strokes, int i, int j) {
        Point endI = strokes.get(route.get(i)).end();
        Point startI1 = strokes.get(route.get(i + 1)).start();
        double d1 = distSq(endI, startI1);
        if (j + 1 < route.size()) {
            Point endJ = strokes.get(route.get(j)).end();
            Point startJ1 = strokes.get(route.get(j + 1)).start();
            return d1 + distSq(endJ, startJ1);
        }
        return d1;
    }

    private static double swappedSegmentDistance(List<Integer> route, List<Stroke> strokes, int i, int j) {
        Point endI = strokes.get(route.get(i)).end();
        Point startJ = strokes.get(route.get(j)).start();
        double d1 = distSq(endI, startJ);
        if (j + 1 < route.size()) {
            Point endI1 = strokes.get(route.get(i + 1)).end();
            Point startJ1 = strokes.get(route.get(j + 1)).start();
            return d1 + distSq(endI1, startJ1);
        }
        return d1;
    }

    private static double distSq(Point a, Point b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return dx * dx + dy * dy;
    }
}
