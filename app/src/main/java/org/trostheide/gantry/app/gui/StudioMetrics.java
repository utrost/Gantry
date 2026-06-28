package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;

import java.util.List;

/**
 * Plotter-aware readout metrics for the vectorize studio (ROADMAP Phase 19, Tier 2), computed
 * from the command model the traced SVG imports to.
 *
 * <p>Counts (layers / strokes / points) are exact. {@link #travelRatio()} — the fraction of
 * total motion spent on pen-up travel rather than drawing — is <b>scale-invariant</b>, so it is
 * meaningful in the studio even before the user has chosen a page size: it captures how
 * efficiently a trace will plot (a lot of short, scattered strokes ⇒ high travel ⇒ slow plot),
 * which is exactly the "is this plottable?" signal a generic image tracer can't give you.
 */
public record StudioMetrics(int layers, int strokes, int points,
                            double drawDistance, double travelDistance) {

    /** Pen-up travel as a fraction of all motion, in {@code [0,1]}; lower plots more efficiently. */
    public double travelRatio() {
        double total = drawDistance + travelDistance;
        return total <= 0 ? 0 : travelDistance / total;
    }

    /** Computes metrics from an imported command model. */
    public static StudioMetrics of(ProcessorOutput output) {
        int strokes = 0;
        int points = 0;
        double draw = 0;
        double travel = 0;

        double lastX = 0, lastY = 0;
        boolean penPositioned = false;

        for (Layer layer : output.layers()) {
            for (Command cmd : layer.commands()) {
                if (cmd instanceof MoveCommand m) {
                    if (penPositioned) {
                        travel += dist(lastX, lastY, m.x, m.y);
                    }
                    lastX = m.x;
                    lastY = m.y;
                    penPositioned = true;
                } else if (cmd instanceof DrawCommand d) {
                    strokes++;
                    List<Point> pts = d.points;
                    points += pts.size();
                    if (pts.isEmpty()) {
                        continue;
                    }
                    // Approaching the stroke start is pen-up travel; it's ~0 when a preceding
                    // MoveCommand already positioned the pen there, so this never double-counts.
                    Point first = pts.get(0);
                    if (penPositioned) {
                        travel += dist(lastX, lastY, first.x(), first.y());
                    }
                    for (int i = 1; i < pts.size(); i++) {
                        Point a = pts.get(i - 1);
                        Point b = pts.get(i);
                        draw += dist(a.x(), a.y(), b.x(), b.y());
                    }
                    Point last = pts.get(pts.size() - 1);
                    lastX = last.x();
                    lastY = last.y();
                    penPositioned = true;
                }
            }
        }
        return new StudioMetrics(output.layers().size(), strokes, points, draw, travel);
    }

    /** A compact one-line summary for the studio status bar. */
    public String summary() {
        return String.format("%d layer(s) · %d strokes · %s pts · %d%% travel",
                layers, strokes, humanCount(points), Math.round(travelRatio() * 100));
    }

    private static String humanCount(int n) {
        return n >= 1000 ? String.format("%.1fk", n / 1000.0) : Integer.toString(n);
    }

    private static double dist(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return Math.hypot(dx, dy);
    }
}
