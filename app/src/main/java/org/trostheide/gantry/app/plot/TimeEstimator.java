package org.trostheide.gantry.app.plot;

import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.model.command.RefillCommand;
import org.trostheide.gantry.plotter.GcodeOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Estimates plot duration from travel/draw distances and the configured feed rates, plus fixed
 * overhead per refill dip and per pen-down (Z/servo lift transition). Distances are measured
 * directly on the (already transformed/baked) command points: the rotate/swap/invert/flip
 * transform applied at plot time is distance-preserving (rotations and reflections only), so
 * summing raw Euclidean distances here matches what the machine will actually travel.
 */
public final class TimeEstimator {

    /** Fixed dip time (matches {@code PlotService.performRefill}'s 500ms dwell). */
    private static final double REFILL_SECONDS = 0.5;

    private TimeEstimator() {
    }

    /** Per-layer estimate: distances in mm, {@code estimatedSeconds} including refill overhead. */
    public record LayerEstimate(String layerId, double drawDistanceMm, double travelDistanceMm,
            int refillCount, double estimatedSeconds) {
    }

    /** Aggregate estimate: one {@link LayerEstimate} per layer, plus the grand total in seconds. */
    public record PlotEstimate(List<LayerEstimate> layers, double totalSeconds) {
    }

    /** Estimates {@code output}'s plot duration using {@code gcode}'s feed rates and {@code stations}' positions. */
    public static PlotEstimate estimate(ProcessorOutput output, GcodeOptions gcode, Map<String, StationConfig> stations) {
        // Settle time after every pen-down (matches GcodeBackend.pendown's dwell), charged once
        // per DrawCommand since each one starts with a fresh pen-down. Independent of penMode:
        // servo and Z-axis moves both incur this dwell, and it dominates the total on hatch-dense
        // drawings where strokes are short and numerous.
        double penDownSeconds = Math.max(0, gcode.penDownDelayMillis) / 1000.0;

        List<LayerEstimate> layerEstimates = new ArrayList<>();
        double total = 0;
        Point cursor = new Point(0, 0);

        for (Layer layer : output.layers()) {
            double drawDist = 0;
            double travelDist = 0;
            int refillCount = 0;
            int penDownCount = 0;

            for (Command cmd : layer.commands()) {
                if (cmd instanceof MoveCommand move) {
                    Point target = new Point(move.x, move.y);
                    travelDist += distance(cursor, target);
                    cursor = target;
                } else if (cmd instanceof DrawCommand draw) {
                    if (!draw.points.isEmpty()) {
                        penDownCount++;
                    }
                    for (int i = 0; i + 1 < draw.points.size(); i++) {
                        drawDist += distance(draw.points.get(i), draw.points.get(i + 1));
                    }
                    if (!draw.points.isEmpty()) {
                        cursor = draw.points.get(draw.points.size() - 1);
                    }
                } else if (cmd instanceof RefillCommand refill) {
                    refillCount++;
                    StationConfig station = resolveStation(stations, refill.stationId);
                    if (station != null) {
                        Point stationPoint = new Point(station.x(), station.y());
                        travelDist += distance(cursor, stationPoint);
                        cursor = stationPoint;
                    }
                }
            }

            double seconds = (travelDist / gcode.feedRateTravel) * 60.0
                    + (drawDist / gcode.feedRateDraw) * 60.0
                    + refillCount * REFILL_SECONDS
                    + penDownCount * penDownSeconds;
            total += seconds;
            layerEstimates.add(new LayerEstimate(layer.id(), drawDist, travelDist, refillCount, seconds));
        }

        return new PlotEstimate(layerEstimates, total);
    }

    private static StationConfig resolveStation(Map<String, StationConfig> stations, String stationId) {
        StationConfig station = stations.get(stationId);
        return station != null ? station : stations.get("default_station");
    }

    private static double distance(Point a, Point b) {
        return Math.hypot(b.x() - a.x(), b.y() - a.y());
    }

    /** Formats a duration in seconds as {@code H:MM:SS} (or {@code M:SS} when under an hour). */
    public static String format(double seconds) {
        long total = Math.round(Math.max(0, seconds));
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }
}
