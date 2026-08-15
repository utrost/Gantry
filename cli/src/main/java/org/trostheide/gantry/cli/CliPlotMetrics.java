package org.trostheide.gantry.cli;

import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;

import java.io.File;
import java.util.List;

/** Headless plotter-cost metrics for batch/import validation sidecars. */
public record CliPlotMetrics(
        String commandFile,
        int layers,
        int commands,
        int strokes,
        int points,
        double drawDistanceMm,
        double travelDistanceMm,
        double travelRatio,
        int tinySegments,
        Bounds bounds) {

    private static final double TINY_SEGMENT_MM = 0.5;

    static CliPlotMetrics of(ProcessorOutput output, File commandFile) {
        int strokes = 0;
        int points = 0;
        int tinySegments = 0;
        double draw = 0;
        double travel = 0;

        double lastX = 0;
        double lastY = 0;
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
                    Point first = pts.get(0);
                    if (penPositioned) {
                        travel += dist(lastX, lastY, first.x(), first.y());
                    }
                    for (int i = 1; i < pts.size(); i++) {
                        Point a = pts.get(i - 1);
                        Point b = pts.get(i);
                        double segment = dist(a.x(), a.y(), b.x(), b.y());
                        draw += segment;
                        if (segment > 0 && segment < TINY_SEGMENT_MM) {
                            tinySegments++;
                        }
                    }
                    Point last = pts.get(pts.size() - 1);
                    lastX = last.x();
                    lastY = last.y();
                    penPositioned = true;
                }
            }
        }

        double total = draw + travel;
        return new CliPlotMetrics(
                commandFile.getName(),
                output.layers().size(),
                output.metadata().totalCommands(),
                strokes,
                points,
                draw,
                travel,
                total <= 0 ? 0 : travel / total,
                tinySegments,
                output.metadata().bounds());
    }

    private static double dist(double x1, double y1, double x2, double y2) {
        return Math.hypot(x2 - x1, y2 - y1);
    }
}
