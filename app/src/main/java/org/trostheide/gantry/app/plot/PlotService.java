package org.trostheide.gantry.app.plot;

import org.trostheide.gantry.model.CoordinateTransform;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.model.command.RefillCommand;
import org.trostheide.gantry.plotter.PlotterBackend;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * In-process port of {@code driver.py}'s plot orchestration: applies the
 * coordinate transform/alignment pipeline to each command, soft-clamps to the
 * machine bed, drives a {@link PlotterBackend}, and performs station refills.
 *
 * <p>Per-layer operator confirmation (the "Press Enter to start this layer"
 * gate in the Python CLI) is delegated to a {@link LayerGate} so the GUI can
 * implement it with a button instead of stdin.
 */
public class PlotService {

    /** Gates the start of each layer, e.g. waiting for an operator to press a "Start Layer" button. */
    @FunctionalInterface
    public interface LayerGate {
        void await(Layer layer) throws InterruptedException;

        /** A gate that never blocks, useful for tests and headless runs. */
        LayerGate IMMEDIATE = layer -> { };
    }

    /** Fired after each command with the global (across-all-layers) done and total command counts. */
    @FunctionalInterface
    public interface ProgressCallback {
        void update(int done, int total);
    }

    private final PlotterBackend backend;
    private final PlotSettings settings;

    private LayerGate layerGate = LayerGate.IMMEDIATE;
    private Consumer<String> logCallback = line -> { };
    private BiConsumer<Double, Double> commandedPositionCallback = (x, y) -> { };
    private Consumer<Layer> layerStartedCallback = layer -> { };
    private ProgressCallback progressCallback = (d, t) -> { };

    private volatile boolean cancelled;
    private volatile boolean paused;
    private final Object pauseLock = new Object();

    public PlotService(PlotterBackend backend, PlotSettings settings) {
        this.backend = backend;
        this.settings = settings;
    }

    /** Registers the gate used to pause before each layer until the operator confirms. */
    public void setLayerGate(LayerGate layerGate) {
        this.layerGate = layerGate != null ? layerGate : LayerGate.IMMEDIATE;
    }

    /** Registers a callback for informational/warning messages (clamping, alignment, etc). */
    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback != null ? logCallback : (line -> { });
    }

    /**
     * Registers a callback for the commanded (target) position after each move, used when
     * {@link PlotSettings#reportPosition} is set and {@link PlotSettings#realtimePosition} is not
     * (i.e. the backend doesn't already report realtime hardware position).
     */
    public void setCommandedPositionCallback(BiConsumer<Double, Double> callback) {
        this.commandedPositionCallback = callback != null ? callback : ((x, y) -> { });
    }

    /** Registers a callback fired right before each layer's commands start executing (after the layer gate). */
    public void setLayerStartedCallback(Consumer<Layer> callback) {
        this.layerStartedCallback = callback != null ? callback : (layer -> { });
    }

    /** Registers a callback fired after each command with global (all-layers) done/total counts. */
    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback != null ? callback : ((d, t) -> { });
    }

    /** Requests that the current/upcoming {@link #plot} call stop as soon as possible. */
    public void cancel() {
        cancelled = true;
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    /** Pauses plotting before the next command and lifts the pen. Resumable via {@link #resume()}. */
    public void pause() {
        synchronized (pauseLock) {
            if (paused) {
                return;
            }
            paused = true;
        }
        backend.penup();
        logCallback.accept("--- Paused (pen up) ---");
    }

    /** Resumes a plot previously paused via {@link #pause()}. */
    public void resume() {
        synchronized (pauseLock) {
            if (!paused) {
                return;
            }
            paused = false;
            pauseLock.notifyAll();
        }
        logCallback.accept("--- Resumed ---");
    }

    /** Blocks the calling (plot) thread while paused, returning immediately once resumed or cancelled. */
    private void awaitResume() {
        synchronized (pauseLock) {
            while (paused && !cancelled) {
                try {
                    pauseLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Runs every layer of {@code output} against the backend: waits for the layer gate, then
     * applies the transform/alignment/clamp pipeline to each MOVE/DRAW command and performs
     * REFILL commands at the configured station.
     */
    public void plot(ProcessorOutput output) {
        cancelled = false;
        List<Layer> layers = output.layers();

        double machineW = settings.resolveMachineWidth();
        double machineH = settings.resolveMachineHeight();

        double[] contentBounds = computeContentBounds(layers);
        int totalCommands = layers.stream().mapToInt(l -> l.commands().size()).sum();
        int[] doneCommands = {0};

        double offsetX = 0;
        double offsetY = 0;
        if (settings.alignmentOffsetOverride != null) {
            offsetX = settings.alignmentOffsetOverride[0];
            offsetY = settings.alignmentOffsetOverride[1];
            logCallback.accept(String.format("Alignment offset (from preview) -> X=%.2f, Y=%.2f", offsetX, offsetY));
        } else if (settings.canvasAlign != null && contentBounds != null) {
            double[] offset = CoordinateTransform.calculateAlignmentOffset(
                    settings.canvasAlign, contentBounds, machineW, machineH,
                    settings.swapXY, settings.invertX, settings.invertY,
                    settings.dataRotation, settings.originRight, settings.originBottom,
                    settings.flipY,
                    settings.paddingX, settings.paddingY);
            offsetX = offset[0];
            offsetY = offset[1];
            logCallback.accept(String.format("Alignment offset -> X=%.2f, Y=%.2f", offsetX, offsetY));
        }

        checkPreflightBounds(contentBounds, machineW, machineH, offsetX, offsetY);

        StationConfig rinseStation = findRinseStation();

        try {
            int layerIndex = 0;
            for (Layer layer : layers) {
                layerIndex++;
                if (cancelled) {
                    return;
                }
                try {
                    layerGate.await(layer);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (cancelled) {
                    return;
                }
                layerStartedCallback.accept(layer);
                logCallback.accept(String.format("=== Layer '%s' (%d/%d): %d commands ===",
                        layer.id(), layerIndex, layers.size(), layer.commands().size()));
                // Clean the brush at the rinse pot before each new colour (not before the first).
                if (rinseStation != null && layerIndex > 1) {
                    performRinse(rinseStation);
                }
                executeLayer(layer, machineW, machineH, offsetX, offsetY, contentBounds, doneCommands, totalCommands);

                if (!cancelled) {
                    parkAtOrigin();
                }
            }
        } finally {
            if (cancelled) {
                // On Stop, always bring the head to a safe state: lift the pen so it doesn't sit
                // on the paper bleeding ink while stopped. This runs on the plot thread itself, so
                // it never races a backend-driven halt. (For GRBL, the GUI also fires a realtime
                // soft-reset via haltMotion() to abort any buffered motion.)
                backend.penup();
                logCallback.accept("--- Plot stopped: pen lifted ---");
            }
        }
    }

    /** Raises the pen and returns to the work origin, so the head is clear for a pen change between layers. */
    private void parkAtOrigin() {
        backend.penup();
        backend.moveto(0, 0);
        reportPosition(0, 0);
        logCallback.accept("--- Layer complete: parked at origin (0, 0), pen up ---");
    }

    /**
     * Scans every DRAW command across all layers for the raw content bounds.
     *
     * @return {minX, maxX, minY, maxY}, or null if no DRAW points exist.
     */
    public static double[] computeContentBounds(List<Layer> layers) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        boolean hasPoints = false;

        for (Layer layer : layers) {
            for (Command cmd : layer.commands()) {
                if (cmd instanceof DrawCommand draw) {
                    for (Point p : draw.points) {
                        hasPoints = true;
                        minX = Math.min(minX, p.x());
                        maxX = Math.max(maxX, p.x());
                        minY = Math.min(minY, p.y());
                        maxY = Math.max(maxY, p.y());
                    }
                }
            }
        }

        return hasPoints ? new double[] { minX, maxX, minY, maxY } : null;
    }

    /**
     * Computes the axis-aligned machine-space bounding box of {@code output} after the full
     * transform/offset/flip pipeline, soft-clamped to the bed — i.e. the rectangle the head will
     * actually sweep. The alignment offset is derived exactly as {@link #plot} derives it (the
     * preview override if set, otherwise the configured canvas alignment). Used by the GUI's
     * "frame the job" pre-flight trace so it walks the same clamped corners the plot will use,
     * never commanding the head outside the bed.
     *
     * @return {minX, maxX, minY, maxY} in machine coordinates, or null if the output has no points.
     */
    public double[] computeFrameBounds(ProcessorOutput output) {
        double machineW = settings.resolveMachineWidth();
        double machineH = settings.resolveMachineHeight();
        double[] contentBounds = computeContentBounds(output.layers());
        if (contentBounds == null) {
            return null;
        }
        double offsetX = 0;
        double offsetY = 0;
        if (settings.alignmentOffsetOverride != null) {
            offsetX = settings.alignmentOffsetOverride[0];
            offsetY = settings.alignmentOffsetOverride[1];
        } else if (settings.canvasAlign != null) {
            double[] offset = CoordinateTransform.calculateAlignmentOffset(
                    settings.canvasAlign, contentBounds, machineW, machineH,
                    settings.swapXY, settings.invertX, settings.invertY,
                    settings.dataRotation, settings.originRight, settings.originBottom,
                    settings.flipY,
                    settings.paddingX, settings.paddingY);
            offsetX = offset[0];
            offsetY = offset[1];
        }
        return plotBounds(contentBounds, machineW, machineH, offsetX, offsetY, true);
    }

    /**
     * Transforms+offsets+flips the four content corners into machine space; optionally soft-clamps
     * each to the bed. Returns {minX, maxX, minY, maxY}, or null if {@code contentBounds} is null.
     */
    private double[] plotBounds(double[] contentBounds, double machineW, double machineH,
            double offsetX, double offsetY, boolean clamp) {
        if (contentBounds == null) {
            return null;
        }
        double minX = contentBounds[0], maxX = contentBounds[1];
        double minY = contentBounds[2], maxY = contentBounds[3];
        double[][] corners = { {minX, minY}, {maxX, minY}, {minX, maxY}, {maxX, maxY} };

        double plotMinX = Double.POSITIVE_INFINITY, plotMaxX = Double.NEGATIVE_INFINITY;
        double plotMinY = Double.POSITIVE_INFINITY, plotMaxY = Double.NEGATIVE_INFINITY;
        for (double[] c : corners) {
            double[] t = doTransform(c[0], c[1], machineW, machineH, contentBounds);
            double tx = t[0] + offsetX;
            double ty = t[1] + offsetY;
            if (settings.flipY) {
                ty = machineH - ty;
            }
            if (clamp) {
                tx = Math.max(0.0, Math.min(tx, machineW));
                ty = Math.max(0.0, Math.min(ty, machineH));
            }
            plotMinX = Math.min(plotMinX, tx);
            plotMaxX = Math.max(plotMaxX, tx);
            plotMinY = Math.min(plotMinY, ty);
            plotMaxY = Math.max(plotMaxY, ty);
        }
        return new double[] { plotMinX, plotMaxX, plotMinY, plotMaxY };
    }

    /** Transforms+offsets+flips the four content corners and warns if any fall outside the machine bed. */
    private void checkPreflightBounds(double[] contentBounds, double machineW, double machineH,
            double offsetX, double offsetY) {
        double[] b = plotBounds(contentBounds, machineW, machineH, offsetX, offsetY, false);
        if (b == null) {
            return;
        }
        double plotMinX = b[0], plotMaxX = b[1], plotMinY = b[2], plotMaxY = b[3];

        logCallback.accept(String.format(
                "Plot bounds -> X: %.1f to %.1f, Y: %.1f to %.1f", plotMinX, plotMaxX, plotMinY, plotMaxY));

        boolean oob = false;
        if (plotMinX < -0.5 || plotMaxX > machineW + 0.5) {
            logCallback.accept(String.format(
                    "WARNING: Plot X range [%.1f, %.1f] exceeds machine width %.0fmm!", plotMinX, plotMaxX, machineW));
            oob = true;
        }
        if (plotMinY < -0.5 || plotMaxY > machineH + 0.5) {
            logCallback.accept(String.format(
                    "WARNING: Plot Y range [%.1f, %.1f] exceeds machine height %.0fmm!", plotMinY, plotMaxY, machineH));
            oob = true;
        }
        if (oob) {
            logCallback.accept("WARNING: Some coordinates will be clamped to machine bounds. Drawing may be clipped.");
        }
    }

    /** How often (in commands) to log a "Layer ... x% done" progress line while drawing. */
    private static final int PROGRESS_LOG_INTERVAL = 100;

    private void executeLayer(Layer layer, double machineW, double machineH,
            double offsetX, double offsetY, double[] contentBounds,
            int[] doneCommands, int totalCommands) {
        int[] oobCount = {0};
        int layerTotal = layer.commands().size();
        int layerIndex = 0;

        for (Command cmd : layer.commands()) {
            layerIndex++;
            if (cancelled) {
                return;
            }
            awaitResume();
            if (cancelled) {
                return;
            }
            if (cmd instanceof MoveCommand move) {
                double[] p = transformAndClamp(move.x, move.y, machineW, machineH, offsetX, offsetY, contentBounds, oobCount);
                backend.moveto(p[0], p[1]);
                reportPosition(p[0], p[1]);
            } else if (cmd instanceof DrawCommand draw) {
                for (Point point : draw.points) {
                    if (cancelled) {
                        return;
                    }
                    awaitResume();
                    if (cancelled) {
                        return;
                    }
                    double[] p = transformAndClamp(point.x(), point.y(), machineW, machineH, offsetX, offsetY, contentBounds, oobCount);
                    backend.lineto(p[0], p[1]);
                    reportPosition(p[0], p[1]);
                }
            } else if (cmd instanceof RefillCommand refill) {
                performRefill(refill.stationId);
            }
            doneCommands[0]++;
            progressCallback.update(doneCommands[0], totalCommands);
            if (layerIndex % PROGRESS_LOG_INTERVAL == 0 || layerIndex == layerTotal) {
                logCallback.accept(String.format("Layer '%s': %d/%d commands (%.0f%%)",
                        layer.id(), layerIndex, layerTotal, 100.0 * layerIndex / layerTotal));
            }
        }

        if (oobCount[0] > 0) {
            logCallback.accept(String.format(
                    "WARNING: %d point(s) were clamped to machine bounds (%.0fx%.0fmm)", oobCount[0], machineW, machineH));
        }
    }

    private double[] transformAndClamp(double x, double y, double machineW, double machineH,
            double offsetX, double offsetY, double[] contentBounds, int[] oobCount) {
        double[] p = doTransform(x, y, machineW, machineH, contentBounds);
        double px = p[0] + offsetX;
        double py = p[1] + offsetY;
        if (settings.flipY) {
            py = machineH - py;
        }
        return softClamp(px, py, machineW, machineH, oobCount);
    }

    private double[] doTransform(double x, double y, double machineW, double machineH, double[] contentBounds) {
        return CoordinateTransform.transformPoint(x, y,
                settings.swapXY, settings.invertX, settings.invertY,
                machineW, machineH, settings.dataRotation, contentBounds);
    }

    private double[] softClamp(double x, double y, double machineW, double machineH, int[] oobCount) {
        double cx = Math.max(0.0, Math.min(x, machineW));
        double cy = Math.max(0.0, Math.min(y, machineH));
        if (cx != x || cy != y) {
            oobCount[0]++;
            logCallback.accept(String.format(
                    "[SOFT LIMIT] Clamped (%.2f, %.2f) -> (%.2f, %.2f) (bed: %.0fx%.0f)", x, y, cx, cy, machineW, machineH));
        }
        return new double[] { cx, cy };
    }

    private void reportPosition(double x, double y) {
        if (settings.reportPosition && !settings.realtimePosition) {
            commandedPositionCallback.accept(x, y);
        }
        if (settings.debugPosition) {
            double[] actual = backend.queryPosition();
            if (actual != null) {
                double dx = actual[0] - x;
                double dy = actual[1] - y;
                String drift = (Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5) ? " *** DRIFT" : "";
                logCallback.accept(String.format(
                        "[POS] cmd=(%.2f, %.2f) hw=(%.2f, %.2f) delta=(%+.2f, %+.2f)%s",
                        x, y, actual[0], actual[1], dx, dy, drift));
            }
        }
    }

    /**
     * Moves to the station and dips: pen down for the station's configured dwell, then up. For
     * "dip_swirl"/"rinse" stations the brush is then swirled in a circle of the station's
     * configured radius before lifting. Falls back to the "default_station" entry for unknown
     * station IDs, and logs a warning if neither is configured.
     */
    private void performRefill(String stationId) {
        StationConfig station = settings.stations.get(stationId);
        if (station == null) {
            station = settings.stations.get("default_station");
            if (station == null) {
                logCallback.accept("WARNING: Unknown station '" + stationId + "' and no default_station configured. Skipping refill.");
                return;
            }
            logCallback.accept("WARNING: Unknown station '" + stationId + "'. Using default_station.");
        }

        logCallback.accept(String.format("--- Refilling at %s (%.1f mm / %.1f mm) ---", stationId, station.x(), station.y()));
        dip(station);
        logCallback.accept("--- Refill Complete ---");
    }

    /**
     * Pen-up dry visit to a station (roadmap Phase 17 test-run wizard): lift the pen and move over
     * the station so the operator can eyeball whether the brush lines up with the physical pot, with
     * no dip. Uses the same {@code penup()}/{@code moveto()} the real refill path uses.
     */
    public void dryVisitStation(StationConfig station) {
        backend.penup();
        backend.moveto(station.x(), station.y());
        commandedPositionCallback.accept(station.x(), station.y());
        logCallback.accept(String.format("--- Over station (%.1f mm / %.1f mm), pen up ---",
                station.x(), station.y()));
    }

    /**
     * Wet test of a station (Phase 17): run the station's <em>real</em> refill behaviour — dip for
     * the configured dwell, then swirl for {@code dip_swirl}/{@code rinse} — so the operator can
     * confirm the dip depth and swirl radius clear the pot rim. Delegates to the exact {@link #dip}
     * used during a plot, so the test can never diverge from what a real refill does.
     */
    public void wetTestStation(StationConfig station) {
        logCallback.accept(String.format("--- Wet test at (%.1f mm / %.1f mm), behaviour '%s' ---",
                station.x(), station.y(), station.behavior()));
        dip(station);
        backend.penup();
        logCallback.accept("--- Wet test complete ---");
    }

    /** Number of straight segments used to approximate one full swirl circle. */
    private static final int SWIRL_SEGMENTS = 16;

    /** Dips the brush in a station's pot and, for swirl/rinse stations, circles it to load/clean evenly. */
    private void dip(StationConfig station) {
        backend.moveto(station.x(), station.y());
        dipPenDown(station);
        sleepQuietly(station.dwellMs());
        backend.penup();

        String behavior = station.behavior();
        if ("dip_swirl".equals(behavior) || "rinse".equals(behavior)) {
            double r = station.swirlRadius();
            dipPenDown(station);
            for (int i = 0; i <= SWIRL_SEGMENTS; i++) {
                double angle = 2 * Math.PI * i / SWIRL_SEGMENTS;
                backend.lineto(station.x() + r * Math.cos(angle), station.y() + r * Math.sin(angle));
            }
            backend.moveto(station.x(), station.y());
            backend.penup();
        }
    }

    /**
     * Lowers the pen for a dip, honouring the station's {@code zDown} dip depth on machines with a
     * real Z axis. A depth of 0 means "unset" — use the backend's normal pen-down depth instead, so
     * servo pens and unconfigured stations behave exactly as before.
     */
    private void dipPenDown(StationConfig station) {
        if (station.zDown() != 0) {
            backend.pendown(station.zDown());
        } else {
            backend.pendown();
        }
    }

    /** Finds the configured rinse/clean pot (a station whose behavior or id marks it as such), if any. */
    private StationConfig findRinseStation() {
        for (Map.Entry<String, StationConfig> e : settings.stations.entrySet()) {
            StationConfig s = e.getValue();
            if ("rinse".equals(s.behavior()) || "rinse".equalsIgnoreCase(e.getKey())
                    || "water".equalsIgnoreCase(e.getKey())) {
                return s;
            }
        }
        return null;
    }

    /** Cleans the brush at the rinse station between colour layers. */
    private void performRinse(StationConfig rinse) {
        logCallback.accept(String.format("--- Rinsing brush (%.1f mm / %.1f mm) ---", rinse.x(), rinse.y()));
        dip(rinse);
        logCallback.accept("--- Rinse Complete ---");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
