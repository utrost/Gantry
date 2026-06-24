package org.trostheide.gantry.pipeline.optimize;

import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-refill optimization stage: simplifies each stroke's polyline (Ramer-Douglas-Peucker),
 * greedily reorders strokes within a layer to minimize pen-up travel (nearest-neighbor + 2-opt),
 * and welds consecutive strokes that touch end-to-end into a single continuous polyline.
 *
 * <p>Runs per-layer so layer-to-station assignment (the {@link Layer#stationId()}) and any
 * {@code RefillCommand} positions are preserved; only the MOVE/DRAW stroke pairs between them
 * are reordered.
 *
 * <p>The merge pass matters for two reasons: SVGs (especially those emitted by non-Inkscape
 * generators) often express a single visual line as many separate {@code <line>}/{@code <path>}
 * segments whose endpoints coincide. Drawing them separately means a redundant pen-up / travel /
 * pen-down between every segment — wasted motion, and a wet pen leaves an ink dot at the start of
 * each one. Welding touching strokes removes both: one pen-down, one smooth line.
 */
public final class OptimizeStage {

    private OptimizeStage() {
    }

    /** Aggregate travel/point stats for a {@link ProcessorOutput}, used to report before/after savings. */
    public record Stats(double travelDistanceMm, int pointCount, int strokeCount) {
    }

    /**
     * Returns a new {@link ProcessorOutput} with each layer's strokes simplified and (optionally)
     * reordered. {@code simplifyTolerance <= 0} disables simplification. Stroke welding is off.
     */
    public static ProcessorOutput optimize(ProcessorOutput input, double simplifyTolerance, boolean reorderStrokes) {
        return optimize(input, simplifyTolerance, reorderStrokes, 0.0);
    }

    /**
     * Returns a new {@link ProcessorOutput} with each layer's strokes simplified, (optionally)
     * reordered, and welded where consecutive strokes touch end-to-end.
     *
     * @param simplifyTolerance Ramer-Douglas-Peucker tolerance in mm; {@code <= 0} disables it.
     * @param reorderStrokes    reorder strokes within a layer to cut pen-up travel.
     * @param mergeTolerance    weld two consecutive strokes into one polyline when the end of one
     *                          is within this many mm of the other's start (or end, reversing it);
     *                          {@code <= 0} disables welding.
     */
    public static ProcessorOutput optimize(ProcessorOutput input, double simplifyTolerance,
            boolean reorderStrokes, double mergeTolerance) {
        List<Layer> layers = new ArrayList<>();
        for (Layer layer : input.layers()) {
            layers.add(optimizeLayer(layer, simplifyTolerance, reorderStrokes, mergeTolerance));
        }
        return new ProcessorOutput(input.metadata(), layers);
    }

    /** Computes total pen-up travel distance, total draw-point count and stroke count for {@code output}. */
    public static Stats computeStats(ProcessorOutput output) {
        double travel = 0;
        int points = 0;
        int strokes = 0;
        Point cursor = new Point(0, 0);

        for (Layer layer : output.layers()) {
            for (Command cmd : layer.commands()) {
                if (cmd instanceof MoveCommand move) {
                    travel += Math.hypot(move.x - cursor.x(), move.y - cursor.y());
                    cursor = new Point(move.x, move.y);
                } else if (cmd instanceof DrawCommand draw) {
                    points += draw.points.size();
                    if (!draw.points.isEmpty()) {
                        strokes++;
                        cursor = draw.points.get(draw.points.size() - 1);
                    }
                }
            }
        }
        return new Stats(travel, points, strokes);
    }

    private static Layer optimizeLayer(Layer layer, double tolerance, boolean reorder, double mergeTolerance) {
        List<Command> source = layer.commands();
        List<Command> result = new ArrayList<>();
        Point cursor = new Point(0, 0);

        int i = 0;
        while (i < source.size()) {
            Command cmd = source.get(i);
            if (cmd instanceof MoveCommand && i + 1 < source.size() && source.get(i + 1) instanceof DrawCommand) {
                List<MoveCommand> moves = new ArrayList<>();
                List<DrawCommand> draws = new ArrayList<>();
                while (i < source.size() && source.get(i) instanceof MoveCommand m
                        && i + 1 < source.size() && source.get(i + 1) instanceof DrawCommand d) {
                    moves.add(m);
                    draws.add(d);
                    i += 2;
                }
                cursor = appendStrokes(result, moves, draws, tolerance, reorder, mergeTolerance, cursor);
            } else {
                result.add(cmd);
                if (cmd instanceof MoveCommand move) {
                    cursor = new Point(move.x, move.y);
                }
                i++;
            }
        }
        return new Layer(layer.id(), layer.stationId(), layer.color(), result);
    }

    private static Point appendStrokes(List<Command> result, List<MoveCommand> moves, List<DrawCommand> draws,
            double tolerance, boolean reorder, double mergeTolerance, Point cursor) {
        int n = moves.size();
        List<List<Point>> simplifiedPoints = new ArrayList<>();
        for (DrawCommand draw : draws) {
            simplifiedPoints.add(RamerDouglasPeucker.simplify(draw.points, tolerance));
        }

        List<Integer> order;
        if (reorder && n > 1) {
            List<PathOptimizer.Stroke> strokes = new ArrayList<>();
            for (int k = 0; k < n; k++) {
                MoveCommand move = moves.get(k);
                List<Point> pts = simplifiedPoints.get(k);
                Point end = pts.isEmpty() ? new Point(move.x, move.y) : pts.get(pts.size() - 1);
                strokes.add(new PathOptimizer.Stroke(new Point(move.x, move.y), end));
            }
            order = PathOptimizer.optimizeOrder(strokes, cursor);
        } else {
            order = new ArrayList<>();
            for (int k = 0; k < n; k++) {
                order.add(k);
            }
        }

        // Weld strokes that touch end-to-end into a single chain (one MOVE + one DRAW), so the pen
        // stays down across what was authored as several segments. Welding runs over the chosen
        // order, so reordering first lets more neighbours line up.
        double tolSq = mergeTolerance > 0 ? mergeTolerance * mergeTolerance : -1;
        List<Chain> chains = new ArrayList<>();
        for (int idx : order) {
            MoveCommand move = moves.get(idx);
            List<Point> pts = simplifiedPoints.get(idx);

            if (tolSq >= 0 && !pts.isEmpty() && !chains.isEmpty()) {
                Chain last = chains.get(chains.size() - 1);
                if (!last.points.isEmpty()) {
                    Point lastEnd = last.points.get(last.points.size() - 1);
                    if (distSq(lastEnd, pts.get(0)) <= tolSq) {
                        for (int k = 1; k < pts.size(); k++) {
                            last.points.add(pts.get(k));
                        }
                        continue;
                    }
                    if (distSq(lastEnd, pts.get(pts.size() - 1)) <= tolSq) {
                        for (int k = pts.size() - 2; k >= 0; k--) {
                            last.points.add(pts.get(k));
                        }
                        continue;
                    }
                }
            }

            chains.add(new Chain(move.id, move.x, move.y, draws.get(idx).id, new ArrayList<>(pts)));
        }

        Point finalCursor = cursor;
        for (Chain c : chains) {
            result.add(new MoveCommand(c.moveId, c.moveX, c.moveY));
            result.add(new DrawCommand(c.drawId, c.points));
            finalCursor = c.points.isEmpty() ? new Point(c.moveX, c.moveY) : c.points.get(c.points.size() - 1);
        }
        return finalCursor;
    }

    private static double distSq(Point a, Point b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        return dx * dx + dy * dy;
    }

    /** A run of welded strokes emitted as a single MOVE (to {@code moveX,moveY}) + DRAW. */
    private static final class Chain {
        final int moveId;
        final double moveX;
        final double moveY;
        final int drawId;
        final List<Point> points;

        Chain(int moveId, double moveX, double moveY, int drawId, List<Point> points) {
            this.moveId = moveId;
            this.moveX = moveX;
            this.moveY = moveY;
            this.drawId = drawId;
            this.points = points;
        }
    }
}
