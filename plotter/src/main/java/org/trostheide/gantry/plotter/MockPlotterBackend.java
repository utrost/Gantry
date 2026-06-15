package org.trostheide.gantry.plotter;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** A {@link PlotterBackend} that just logs every call and tracks a simulated XY position. */
public class MockPlotterBackend implements PlotterBackend {

    private final Consumer<String> log;
    private double x;
    private double y;
    private int feedOverride = 100;

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
        return List.of("ok");
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
