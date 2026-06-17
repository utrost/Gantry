package org.trostheide.gantry.plotter;

import java.util.List;

/**
 * A plotter backend capable of absolute/relative moves and pen control.
 * {@code queryPosition} and {@code sendRaw} are optional capabilities with
 * safe defaults for backends that don't support them.
 */
public interface PlotterBackend {

    boolean connect();

    void disconnect();

    /** Absolute rapid move with the pen up. */
    void moveto(double x, double y);

    /** Absolute move with the pen down (drawing). */
    void lineto(double x, double y);

    /** Relative move, pen state unchanged. */
    void move(double dx, double dy);

    void penup();

    void pendown();

    /** Returns the last known {x, y} work position in mm, or null if unsupported. */
    default double[] queryPosition() {
        return null;
    }

    /** Sends a raw command and returns the response lines. */
    default List<String> sendRaw(String command) {
        return List.of("(not supported by this backend)");
    }

    /** Adjusts the realtime feed-rate override ("up", "down" or "reset"). No-op if unsupported. */
    default void adjustSpeed(String direction) {
    }

    /**
     * Runs a homing cycle against physical limit switches (GRBL {@code $H}) and zeroes the
     * work origin at the resulting position. No-op if unsupported.
     */
    default void home() {
    }
}
