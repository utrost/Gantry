package org.trostheide.gantry.plotter;

import java.util.List;

/**
 * A plotter backend capable of absolute/relative moves and pen control.
 * {@code queryPosition} and {@code sendRaw} are optional capabilities with
 * safe defaults for backends that don't support them.
 * High-level travel lifts the pen, drawing lowers it, and relative movement preserves it.
 * A serial acknowledgement does not by itself establish physical motion completion.
 * See docs/BACKEND_CONTRACT.md for per-backend capabilities and simulation limits.
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

    /**
     * Lowers the pen to a specific Z depth (mm), for per-station watercolor dips on machines with
     * a real Z axis. Backends without depth control (servo pens, mocks) fall back to {@link #pendown()}.
     */
    default void pendown(double zDown) {
        pendown();
    }

    /**
     * Pauses motion for the requested duration. Hardware backends should enqueue this pause on
     * the controller so it begins only after preceding motion has physically completed.
     */
    default void dwell(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Evidence returned by an explicit motion-completion barrier, never by an ordinary ack. */
    enum MotionCompletion { CONTROLLER_IDLE, SIMULATED_IDLE, OUTPUT_WRITTEN }

    /**
     * Waits for all previously submitted motion to finish, or throws on failure/cancellation.
     * Implementations must bound controller waits and require fresh completion evidence.
     * OUTPUT_WRITTEN means only that a file/recorder accepted the commands, never physical Idle.
     * Unsupported backends fail closed rather than enabling an unverified operator handoff.
     */
    default MotionCompletion awaitMotionComplete() {
        throw new UnsupportedOperationException("This backend cannot confirm motion completion");
    }

    /** Validates a controller-work-space rectangle before any associated motion. */
    default void validateWorkEnvelope(double minX,double minY,double maxX,double maxY) { }

    /** Whether this backend is explicitly a no-hardware simulation. */
    default boolean isSimulation() { return false; }

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

    /**
     * Immediately halts any in-progress/queued motion, e.g. via a realtime
     * soft-reset, bypassing the normal command queue. No-op if unsupported.
     */
    default void haltMotion() {
    }
}
