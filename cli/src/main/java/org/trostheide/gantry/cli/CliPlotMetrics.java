package org.trostheide.gantry.cli;

import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.plotter.GcodeOptions;

import java.io.File;
import java.util.ArrayList;
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
        Bounds bounds,
        PlotTime plotTime,
        List<Warning> warnings) {

    private static final double TINY_SEGMENT_MM = 0.5;
    private static final double HIGH_TRAVEL_RATIO = 0.50;

    /** Optional plot-time estimate based on batch G-code feed rates. */
    public record PlotTime(int feedRateDraw, int feedRateTravel, int penDownDelayMillis,
                           double estimatedSeconds, String formatted) {
    }

    /** Machine-readable plottability warning for review queues and batch reports. */
    public record Warning(String code, String message, double value, double threshold) {
    }

    static CliPlotMetrics of(ProcessorOutput output, File commandFile) {
        return of(output, commandFile, null);
    }

    static CliPlotMetrics of(ProcessorOutput output, File commandFile, GcodeOptions gcode) {
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
        double travelRatio = total <= 0 ? 0 : travel / total;
        PlotTime plotTime = estimatePlotTime(gcode, draw, travel, strokes);
        List<Warning> warnings = warnings(travelRatio, tinySegments, plotTime);
        return new CliPlotMetrics(
                commandFile.getName(),
                output.layers().size(),
                output.metadata().totalCommands(),
                strokes,
                points,
                draw,
                travel,
                travelRatio,
                tinySegments,
                output.metadata().bounds(),
                plotTime,
                warnings);
    }

    private static List<Warning> warnings(double travelRatio, int tinySegments, PlotTime plotTime) {
        List<Warning> warnings = new ArrayList<>();
        if (travelRatio >= HIGH_TRAVEL_RATIO) {
            warnings.add(new Warning("HIGH_TRAVEL_RATIO",
                    "Pen-up travel is at least half of all motion; try path reordering, merging, or a less scattered preset.",
                    travelRatio, HIGH_TRAVEL_RATIO));
        }
        if (tinySegments > 0) {
            warnings.add(new Warning("TINY_SEGMENTS",
                    "Contains sub-0.5 mm draw segments that may chatter, blob, or vanish on paper.",
                    tinySegments, TINY_SEGMENT_MM));
        }
        if (plotTime != null && plotTime.estimatedSeconds() >= 3600.0) {
            warnings.add(new Warning("LONG_PLOT_TIME",
                    "Estimated plot time is at least one hour; verify paper, ink, and machine supervision before running.",
                    plotTime.estimatedSeconds(), 3600.0));
        }
        return warnings;
    }

    private static PlotTime estimatePlotTime(GcodeOptions gcode, double draw, double travel, int penDownCount) {
        if (gcode == null) {
            return null;
        }
        double drawSeconds = gcode.feedRateDraw <= 0 ? 0 : (draw / gcode.feedRateDraw) * 60.0;
        double travelSeconds = gcode.feedRateTravel <= 0 ? 0 : (travel / gcode.feedRateTravel) * 60.0;
        double penDownSeconds = penDownCount * Math.max(0, gcode.penDownDelayMillis) / 1000.0;
        double seconds = drawSeconds + travelSeconds + penDownSeconds;
        return new PlotTime(gcode.feedRateDraw, gcode.feedRateTravel,
                gcode.penDownDelayMillis, seconds, formatDuration(seconds));
    }

    private static String formatDuration(double seconds) {
        long total = Math.round(Math.max(0, seconds));
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    private static double dist(double x1, double y1, double x2, double y2) {
        return Math.hypot(x2 - x1, y2 - y1);
    }
}
