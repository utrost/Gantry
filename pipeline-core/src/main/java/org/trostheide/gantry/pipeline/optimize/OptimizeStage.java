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
 * Pre-refill optimization stage: simplifies each stroke's polyline (Ramer-Douglas-Peucker) and
 * greedily reorders strokes within a layer to minimize pen-up travel (nearest-neighbor + 2-opt).
 *
 * <p>Runs per-layer so layer-to-station assignment (the {@link Layer#stationId()}) and any
 * {@code RefillCommand} positions are preserved; only the MOVE/DRAW stroke pairs between them
 * are reordered.
 */
public final class OptimizeStage {

    private OptimizeStage() {
    }

    /** Aggregate travel/point stats for a {@link ProcessorOutput}, used to report before/after savings. */
    public record Stats(double travelDistanceMm, int pointCount, int strokeCount) {
    }

    /**
     * Returns a new {@link ProcessorOutput} with each layer's strokes simplified and (optionally)
     * reordered. {@code simplifyTolerance <= 0} disables simplification.
     */
    public static ProcessorOutput optimize(ProcessorOutput input, double simplifyTolerance, boolean reorderStrokes) {
        List<Layer> layers = new ArrayList<>();
        for (Layer layer : input.layers()) {
            layers.add(optimizeLayer(layer, simplifyTolerance, reorderStrokes));
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

    private static Layer optimizeLayer(Layer layer, double tolerance, boolean reorder) {
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
                cursor = appendStrokes(result, moves, draws, tolerance, reorder, cursor);
            } else {
                result.add(cmd);
                if (cmd instanceof MoveCommand move) {
                    cursor = new Point(move.x, move.y);
                }
                i++;
            }
        }
        return new Layer(layer.id(), layer.stationId(), result);
    }

    private static Point appendStrokes(List<Command> result, List<MoveCommand> moves, List<DrawCommand> draws,
            double tolerance, boolean reorder, Point cursor) {
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

        Point finalCursor = cursor;
        for (int idx : order) {
            MoveCommand move = moves.get(idx);
            List<Point> pts = simplifiedPoints.get(idx);
            result.add(new MoveCommand(move.id, move.x, move.y));
            result.add(new DrawCommand(draws.get(idx).id, pts));
            finalCursor = pts.isEmpty() ? new Point(move.x, move.y) : pts.get(pts.size() - 1);
        }
        return finalCursor;
    }
}
