package org.trostheide.gantry.plotter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** A {@link PlotterBackend} that logs every call, simulates XY position, and sleeps for
 *  realistic durations derived from the configured feed rates and pen delays. */
public class MockPlotterBackend implements PlotterBackend {

    private final GcodeOptions options;
    private final Consumer<String> log;

    private volatile double x;
    private volatile double y;
    private volatile int feedOverride = 100;

    // Simulated GRBL steps/mm ($100 = X, $101 = Y), so the axis-calibration wizard can be
    // exercised against the mock: a "$$" query reports these and "$100="/"$101=" writes update them.
    private double stepsPerMmX = 80.0;
    private double stepsPerMmY = 80.0;

    // Simulated GRBL homing/limit settings ($20–$23) and the active limit pins reported by "?".
    private int softLimits = 0;
    private int hardLimits = 0;
    private int homingEnable = 0;
    private int homingDirMask = 3;
    private String simulatedPins = "";

    /** Sets the limit pins the next {@code ?} status report will show as triggered (e.g. "X", "XY"). */
    public void setSimulatedPins(String pins) {
        this.simulatedPins = pins == null ? "" : pins;
    }

    public MockPlotterBackend(GcodeOptions options, Consumer<String> log) {
        this.options = options != null ? options : new GcodeOptions();
        this.log = log != null ? log : message -> { };
    }

    public MockPlotterBackend(Consumer<String> log) {
        this(new GcodeOptions(), log);
    }

    public MockPlotterBackend() {
        this(new GcodeOptions(), null);
    }

    @Override
    public boolean connect() {
        log.accept("[Mock] Connected.");
        return true;
    }

    @Override
    public void disconnect() {
        log.accept("[Mock] Disconnected.");
    }

    @Override
    public void moveto(double x, double y) {
        log.accept(String.format(Locale.ROOT, "[Mock] Move to (%.2f, %.2f) [pen up]", x, y));
        simulatedMove(x, y, options.feedRateTravel);
    }

    @Override
    public void lineto(double x, double y) {
        log.accept(String.format(Locale.ROOT, "[Mock] Draw to (%.2f, %.2f) [pen down]", x, y));
        simulatedMove(x, y, options.feedRateDraw);
    }

    @Override
    public void move(double dx, double dy) {
        log.accept(String.format(Locale.ROOT, "[Mock] Relative move (%.2f, %.2f)", dx, dy));
        simulatedMove(x + dx, y + dy, options.feedRateTravel);
    }

    @Override
    public void penup() {
        log.accept("[Mock] Pen UP");
    }

    @Override
    public void pendown() {
        log.accept("[Mock] Pen DOWN");
        sleepQuietly(options.penDownDelayMillis);
    }

    @Override
    public double[] queryPosition() {
        return new double[] { x, y };
    }

    @Override
    public List<String> sendRaw(String command) {
        log.accept("[Mock] Raw: " + command);
        String cmd = command == null ? "" : command.trim();
        if (cmd.equals("?")) {
            String pn = simulatedPins.isEmpty() ? "" : "|Pn:" + simulatedPins;
            return List.of(String.format(Locale.ROOT,
                    "<Idle|MPos:%.3f,%.3f,0.000|FS:0,0%s>", x, y, pn));
        }
        if (cmd.equals("$$")) {
            List<String> out = new ArrayList<>();
            out.add(String.format(Locale.ROOT, "$20=%d", softLimits));
            out.add(String.format(Locale.ROOT, "$21=%d", hardLimits));
            out.add(String.format(Locale.ROOT, "$22=%d", homingEnable));
            out.add(String.format(Locale.ROOT, "$23=%d", homingDirMask));
            out.add(String.format(Locale.ROOT, "$100=%.3f", stepsPerMmX));
            out.add(String.format(Locale.ROOT, "$101=%.3f", stepsPerMmY));
            out.add("ok");
            return out;
        }
        if (cmd.startsWith("$100=") || cmd.startsWith("$101=")) {
            try {
                double v = Double.parseDouble(cmd.substring(5).trim());
                if (cmd.startsWith("$100=")) {
                    stepsPerMmX = v;
                } else {
                    stepsPerMmY = v;
                }
                log.accept(String.format(Locale.ROOT, "[Mock] %s applied", cmd));
            } catch (NumberFormatException ignored) {
                return List.of("error:3");
            }
        }
        if (cmd.matches("\\$2[0-3]=.*")) {
            try {
                int v = Integer.parseInt(cmd.substring(cmd.indexOf('=') + 1).trim());
                switch (cmd.substring(0, cmd.indexOf('='))) {
                    case "$20" -> softLimits = v;
                    case "$21" -> hardLimits = v;
                    case "$22" -> homingEnable = v;
                    case "$23" -> homingDirMask = v;
                    default -> { }
                }
                log.accept(String.format(Locale.ROOT, "[Mock] %s applied", cmd));
            } catch (NumberFormatException ignored) {
                return List.of("error:3");
            }
        }
        return List.of("ok");
    }

    @Override
    public void home() {
        x = 0;
        y = 0;
        log.accept("[Mock] Homing cycle -> origin zeroed at (0.00, 0.00)");
    }

    @Override
    public void adjustSpeed(String direction) {
        switch (direction.toLowerCase(Locale.ROOT)) {
            case "up" -> feedOverride = Math.min(200, feedOverride + 10);
            case "down" -> feedOverride = Math.max(10, feedOverride - 10);
            case "reset" -> feedOverride = 100;
            default -> { return; }
        }
        log.accept("[Mock] Feed override -> " + feedOverride + "%");
    }

    /**
     * Sleeps for the time the real machine would take to travel from the current position to
     * (toX, toY) at the given feed rate, adjusted by the current {@link #feedOverride}.
     * Updates x/y in 50 ms increments so position queries during a move return interpolated values.
     */
    private void simulatedMove(double toX, double toY, int feedRateMmMin) {
        double distMm = Math.hypot(toX - x, toY - y);
        if (distMm < 0.001) {
            x = toX;
            y = toY;
            return;
        }
        double effectiveFeedMmMin = feedRateMmMin * (feedOverride / 100.0);
        long totalMs = Math.round(distMm / effectiveFeedMmMin * 60_000.0);
        if (totalMs <= 0) {
            x = toX;
            y = toY;
            return;
        }
        double fromX = x, fromY = y;
        long elapsed = 0;
        while (elapsed < totalMs) {
            long step = Math.min(50, totalMs - elapsed);
            sleepQuietly(step);
            elapsed += step;
            double progress = (double) elapsed / totalMs;
            x = fromX + (toX - fromX) * progress;
            y = fromY + (toY - fromY) * progress;
        }
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
