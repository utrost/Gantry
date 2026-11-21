package org.trostheide.gantry.app.plot;

import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.RefillCommand;
import org.trostheide.gantry.plotter.GcodeOptions;
import org.trostheide.gantry.plotter.PlotterBackend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Feed/dwell estimate of the actual prepared plot, with no connection, movement or sleeping. */
public final class TimeEstimator {
    private TimeEstimator() { }

    public record LayerEstimate(String layerId, double drawDistanceMm, double travelDistanceMm,
                                int refillCount, double estimatedSeconds, double fixedSeconds) {
        public LayerEstimate(String layerId, double drawDistanceMm, double travelDistanceMm,
                             int refillCount, double estimatedSeconds) {
            this(layerId, drawDistanceMm, travelDistanceMm, refillCount, estimatedSeconds, 0);
        }
        /** Feed overrides change motion time; pen settling and controller dwells stay fixed. */
        public double atSpeedPercent(int percent) {
            if (percent <= 0) throw new IllegalArgumentException("Speed must be positive.");
            return fixedSeconds + (estimatedSeconds - fixedSeconds) * 100.0 / percent;
        }
    }

    public record PlotEstimate(List<LayerEstimate> layers, double totalSeconds) {
        public double atSpeedPercent(int percent) {
            if (percent <= 0) throw new IllegalArgumentException("Speed must be positive.");
            double fixed = layers.stream().mapToDouble(LayerEstimate::fixedSeconds).sum();
            return fixed + (totalSeconds - fixed) * 100.0 / percent;
        }
    }

    /** Compatibility for callers with raw, untransformed commands and station positions. */
    public static PlotEstimate estimate(ProcessorOutput output, GcodeOptions options,
                                        Map<String, StationConfig> stations) {
        var settings = new PlotSettings();
        settings.machineWidth = options.machineWidth; settings.machineHeight = options.machineHeight;
        settings.stations = stations;
        return estimate(output, options, settings);
    }

    /** Uses the same transforms, clamping, park, refill and rinse path as PlotService.plot. */
    public static PlotEstimate estimate(ProcessorOutput output, GcodeOptions options, PlotSettings settings) {
        var recorder = new EstimateBackend(options);
        var service = new PlotService(recorder, settings);
        service.setLayerStartedCallback(layer -> recorder.startLayer(layer, settings));
        service.plot(output);
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        var layers = recorder.layers.stream().map(row -> row.finish(options)).toList();
        return new PlotEstimate(layers, layers.stream().mapToDouble(LayerEstimate::estimatedSeconds).sum());
    }

    private static final class LayerTotals {
        final String id;
        final int refills;
        double draw, travel, fixed;
        LayerTotals(String id, int refills) { this.id = id; this.refills = refills; }
        LayerEstimate finish(GcodeOptions options) {
            return new LayerEstimate(id, draw, travel, refills,
                    travel / options.feedRateTravel * 60 + draw / options.feedRateDraw * 60 + fixed, fixed);
        }
    }

    /** Records the production orchestration instead of maintaining another route/dip implementation. */
    private static final class EstimateBackend implements PlotterBackend {
        final GcodeOptions options;
        final List<LayerTotals> layers = new ArrayList<>();
        LayerTotals current;
        double x, y;
        boolean down;
        EstimateBackend(GcodeOptions options) { this.options = options; }
        void startLayer(Layer layer, PlotSettings settings) {
            int refills = (int) layer.commands().stream().filter(command -> command instanceof RefillCommand refill
                    && PlotService.resolveStation(settings.stations, refill.stationId) != null).count();
            if (!layers.isEmpty() && PlotService.rinseStation(settings.stations) != null) refills++;
            current = new LayerTotals(layer.id(), refills); layers.add(current);
        }
        public boolean connect() { throw new UnsupportedOperationException("Estimation never connects."); }
        public void disconnect() { }
        public MotionCompletion awaitMotionComplete() { return MotionCompletion.OUTPUT_WRITTEN; }
        public double[] queryPosition() { return new double[]{x, y}; }
        public void moveto(double nextX, double nextY) {
            penup(); current.travel += Math.hypot(nextX - x, nextY - y); x = nextX; y = nextY;
        }
        public void lineto(double nextX, double nextY) {
            if (!down) pendown();
            current.draw += Math.hypot(nextX - x, nextY - y); x = nextX; y = nextY;
        }
        public void move(double dx, double dy) {
            if (down) lineto(x + dx, y + dy); else moveto(x + dx, y + dy);
        }
        public void penup() { down = false; }
        public void pendown() { down = true; current.fixed += Math.max(0, options.penDownDelayMillis) / 1000.0; }
        public void dwell(long millis) { current.fixed += Math.max(0, millis) / 1000.0; }
    }

    /** Formats a duration in seconds as H:MM:SS (or M:SS when under an hour). */
    public static String format(double seconds) {
        long total = Math.round(Math.max(0, seconds));
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }
}
