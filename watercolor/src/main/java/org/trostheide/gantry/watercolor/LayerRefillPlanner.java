package org.trostheide.gantry.watercolor;

import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.model.command.RefillCommand;

import java.util.ArrayList;
import java.util.List;

/** Rebuilds automatic refills while preserving the executable pen-down path. */
public final class LayerRefillPlanner {
    private LayerRefillPlanner() { }

    public static Layer configure(Layer layer, String stationId, double maxDrawDistance) {
        return configure(layer, stationId, maxDrawDistance, layer.dipBehavior());
    }

    public static Layer configure(Layer layer, String stationId, double maxDrawDistance, String dipBehavior) {
        if (maxDrawDistance < 0 || !Double.isFinite(maxDrawDistance)) {
            throw new IllegalArgumentException("maxDrawDistance must be a finite non-negative value");
        }
        String target = stationId == null ? "" : stationId.trim();
        List<Command> source = layer.commands().stream()
                .filter(command -> !(command instanceof RefillCommand)).toList();
        if (maxDrawDistance == 0 || source.stream().noneMatch(c -> c instanceof DrawCommand d && !d.points.isEmpty())) {
            return new Layer(layer.id(), target, layer.color(), maxDrawDistance, dipBehavior, source);
        }
        int nextId = layer.commands().stream().mapToInt(Command::getId).max().orElse(0) + 1;
        Route route = new Route(target, maxDrawDistance, nextId);
        for (Command command : source) {
            if (command instanceof MoveCommand move) {
                route.current = new Point(move.x, move.y);
                route.needsReturn = false;
                route.commands.add(move);
            } else if (command instanceof DrawCommand draw) {
                route.draw(draw);
            } else {
                route.commands.add(command);
            }
        }
        return new Layer(layer.id(), target, layer.color(), maxDrawDistance, dipBehavior, route.commands);
    }

    /** Tracks actual DRAW endpoints, including the segment from the preceding MOVE. */
    private static final class Route {
        final List<Command> commands = new ArrayList<>();
        final String station;
        final double limit, tolerance;
        int nextId;
        Point current = new Point(0, 0);
        double painted;
        boolean needsReturn = true;

        Route(String station, double limit, int nextId) {
            this.station = station;
            this.limit = limit;
            this.tolerance = Math.min(1e-9, limit * 1e-6);
            this.nextId = nextId;
            commands.add(new RefillCommand(this.nextId++, station));
        }

        void draw(DrawCommand draw) {
            if (draw.points.isEmpty()) { commands.add(draw); return; }
            if (needsReturn) returnToArtwork();
            List<Point> segment = new ArrayList<>();
            int id = draw.id;
            for (Point target : draw.points) {
                double remaining = distance(current, target);
                while (remaining > tolerance && painted + remaining > limit + tolerance) {
                    double allowance = limit - painted;
                    if (allowance > tolerance) {
                        Point split = new Point(current.x() + (target.x() - current.x()) * allowance / remaining,
                                current.y() + (target.y() - current.y()) * allowance / remaining);
                        segment.add(split);
                        current = split;
                    }
                    // Submit all buffered artwork before leaving for the pot, even at an exact boundary.
                    if (!segment.isEmpty()) {
                        commands.add(new DrawCommand(id, segment));
                        id = nextId++;
                        segment = new ArrayList<>();
                    }
                    commands.add(new RefillCommand(nextId++, station));
                    returnToArtwork();
                    painted = 0;
                    remaining = distance(current, target);
                }
                segment.add(target);
                painted += remaining;
                current = target;
            }
            if (!segment.isEmpty()) commands.add(new DrawCommand(id, segment));
        }

        void returnToArtwork() {
            commands.add(new MoveCommand(nextId++, current.x(), current.y()));
            needsReturn = false;
        }
    }

    private static double distance(Point a, Point b) {
        return Math.hypot(b.x() - a.x(), b.y() - a.y());
    }
}
