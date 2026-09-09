package org.trostheide.gantry.watercolor;

import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.model.command.RefillCommand;

import java.util.ArrayList;
import java.util.List;

/** Rebuilds the automatic refill commands for an already imported layer. */
public final class LayerRefillPlanner {
    private static final double EPSILON = 1e-9;

    private LayerRefillPlanner() { }

    public static Layer configure(Layer layer, String stationId, double maxDrawDistance) {
        return configure(layer, stationId, maxDrawDistance, layer.dipBehavior());
    }

    public static Layer configure(Layer layer, String stationId, double maxDrawDistance, String dipBehavior) {
        if (maxDrawDistance < 0 || !Double.isFinite(maxDrawDistance)) {
            throw new IllegalArgumentException("maxDrawDistance must be a finite non-negative value");
        }
        String targetStation = stationId == null ? "" : stationId.trim();
        List<Command> source = layer.commands().stream()
                .filter(command -> !(command instanceof RefillCommand)).toList();
        List<Command> result = new ArrayList<>();
        int nextId = layer.commands().stream().mapToInt(Command::getId).max().orElse(0) + 1;
        boolean hasDrawing = source.stream().anyMatch(DrawCommand.class::isInstance);
        if (maxDrawDistance > 0 && hasDrawing) {
            result.add(new RefillCommand(nextId++, targetStation));
        }

        double painted = 0;
        for (Command command : source) {
            if (!(command instanceof DrawCommand draw) || maxDrawDistance == 0 || draw.points.size() < 2) {
                result.add(command);
                continue;
            }
            List<Point> segment = new ArrayList<>();
            Point current = draw.points.get(0);
            segment.add(current);
            boolean firstPiece = true;
            for (int i = 1; i < draw.points.size(); i++) {
                Point target = draw.points.get(i);
                double remainingSegment = distance(current, target);
                while (remainingSegment > EPSILON && painted + remainingSegment > maxDrawDistance + EPSILON) {
                    double allowance = maxDrawDistance - painted;
                    if (allowance <= EPSILON) {
                        result.add(new RefillCommand(nextId++, targetStation));
                        result.add(new MoveCommand(nextId++, current.x(), current.y()));
                        painted = 0;
                        continue;
                    }
                    Point split = interpolate(current, target, allowance / remainingSegment);
                    segment.add(split);
                    result.add(new DrawCommand(firstPiece ? draw.id : nextId++, segment));
                    firstPiece = false;
                    result.add(new RefillCommand(nextId++, targetStation));
                    result.add(new MoveCommand(nextId++, split.x(), split.y()));
                    segment = new ArrayList<>();
                    segment.add(split);
                    painted = 0;
                    current = split;
                    remainingSegment = distance(current, target);
                }
                segment.add(target);
                painted += remainingSegment;
                current = target;
            }
            if (segment.size() > 1) {
                result.add(new DrawCommand(firstPiece ? draw.id : nextId++, segment));
            }
        }
        return new Layer(layer.id(), targetStation, layer.color(), maxDrawDistance, dipBehavior, result);
    }

    private static double distance(Point a, Point b) {
        return Math.hypot(b.x() - a.x(), b.y() - a.y());
    }

    private static Point interpolate(Point a, Point b, double fraction) {
        return new Point(a.x() + (b.x() - a.x()) * fraction,
                a.y() + (b.y() - a.y()) * fraction);
    }
}
