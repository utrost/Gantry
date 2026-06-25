package org.trostheide.gantry.plotter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** A {@link PlotterBackend} that just logs every call and tracks a simulated XY position. */
public class MockPlotterBackend implements PlotterBackend {

    private final Consumer<String> log;
    private double x;
    private double y;
    private int feedOverride = 100;
    // Simulated GRBL steps/mm ($100 = X, $101 = Y), so the axis-calibration wizard can be exercised
    // against the mock: a "$$" query reports these and "$100="/"$101=" writes update them.
    private double stepsPerMmX = 80.0;
    private double stepsPerMmY = 80.0;

    public MockPlotterBackend(Consumer<String> log) {
        this.log = log != null ? log : message -> { };
    }

    public MockPlotterBackend() {
        this(null);
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
        this.x = x;
        this.y = y;
        log.accept(String.format(Locale.ROOT, "[Mock] Move to (%.2f, %.2f) [pen up]", x, y));
    }

    @Override
    public void lineto(double x, double y) {
        this.x = x;
        this.y = y;
        log.accept(String.format(Locale.ROOT, "[Mock] Draw to (%.2f, %.2f) [pen down]", x, y));
    }

    @Override
    public void move(double dx, double dy) {
        this.x += dx;
        this.y += dy;
        log.accept(String.format(Locale.ROOT, "[Mock] Relative move (%.2f, %.2f)", dx, dy));
    }

    @Override
    public void penup() {
        log.accept("[Mock] Pen UP");
    }

    @Override
    public void pendown() {
        log.accept("[Mock] Pen DOWN");
    }

    @Override
    public double[] queryPosition() {
        return new double[] { x, y };
    }

    @Override
    public List<String> sendRaw(String command) {
        log.accept("[Mock] Raw: " + command);
        String cmd = command == null ? "" : command.trim();
        if (cmd.equals("$$")) {
            List<String> out = new ArrayList<>();
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
}
