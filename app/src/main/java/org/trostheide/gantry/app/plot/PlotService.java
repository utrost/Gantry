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

    private final PlotterBackend backend;
    private final PlotSettings settings;

    private LayerGate layerGate = LayerGate.IMMEDIATE;
    private Consumer<String> logCallback = line -> { };
    private BiConsumer<Double, Double> commandedPositionCallback = (x, y) -> { };
    private Consumer<Layer> layerStartedCallback = layer -> { };

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
                    settings.dataRotation, settings.originRight,
                    settings.paddingX, settings.paddingY);
            offsetX = offset[0];
            offsetY = offset[1];
            logCallback.accept(String.format("Alignment offset -> X=%.2f, Y=%.2f", offsetX, offsetY));
        }

        checkPreflightBounds(contentBounds, machineW, machineH, offsetX, offsetY);

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
            executeLayer(layer, machineW, machineH, offsetX, offsetY, contentBounds);
        }
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

    /** Transforms+offsets+flips the four content corners and warns if any fall outside the machine bed. */
    private void checkPreflightBounds(double[] contentBounds, double machineW, double machineH,
            double offsetX, double offsetY) {
        if (contentBounds == null) {
            return;
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
            plotMinX = Math.min(plotMinX, tx);
            plotMaxX = Math.max(plotMaxX, tx);
            plotMinY = Math.min(plotMinY, ty);
            plotMaxY = Math.max(plotMaxY, ty);
        }

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
            double offsetX, double offsetY, double[] contentBounds) {
        int[] oobCount = {0};
        int totalCommands = layer.commands().size();
        int commandIndex = 0;

        for (Command cmd : layer.commands()) {
            commandIndex++;
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
            if (commandIndex % PROGRESS_LOG_INTERVAL == 0 || commandIndex == totalCommands) {
                logCallback.accept(String.format("Layer '%s': %d/%d commands (%.0f%%)",
                        layer.id(), commandIndex, totalCommands, 100.0 * commandIndex / totalCommands));
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
     * Moves to the station, dips (pendown 0.5s, penup), and for "dip_swirl" stations also
     * swirls the brush +-2mm before lifting. Falls back to the "default_station" entry for
     * unknown station IDs, and logs a warning if neither is configured.
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

        backend.moveto(station.x(), station.y());
        backend.pendown();
        sleepQuietly(500);
        backend.penup();

        if ("dip_swirl".equals(station.behavior())) {
            backend.pendown();
            backend.lineto(station.x() + 2, station.y());
            backend.lineto(station.x() - 2, station.y());
            backend.moveto(station.x(), station.y());
            backend.penup();
        }

        logCallback.accept("--- Refill Complete ---");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
