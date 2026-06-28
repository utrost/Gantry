package org.trostheide.gantry.app.plot;

import java.util.Optional;

/**
 * Derives the axis transform from a "jog-and-observe" calibration: command a raw motor move on each
 * axis, have the operator report which way the pen <em>physically</em> went, and solve for the
 * swap/invert settings that make the on-screen convention (+X = right, +Y = up) match reality.
 *
 * <p>This catches axis <em>swap</em> (press X, the head moves vertically) as well as inversion —
 * the existing two-checkbox direction step can only flip inverts. It is pure and unit-tested so the
 * sign logic (historically a source of bugs) is pinned down independently of any wizard UI.
 *
 * <p>The math inverts {@code PlotterPanel.transformDelta}, whose order is swap → invertX → invertY.
 */
public final class AxisDirectionSolver {

    private AxisDirectionSolver() {
    }

    /** A physical direction the operator sees the pen move; RIGHT/UP are the screen-positive senses. */
    public enum Dir {
        RIGHT, LEFT, UP, DOWN;

        boolean horizontal() {
            return this == RIGHT || this == LEFT;
        }

        Dir opposite() {
            return switch (this) {
                case RIGHT -> LEFT;
                case LEFT -> RIGHT;
                case UP -> DOWN;
                case DOWN -> UP;
            };
        }
    }

    /** The effective swap/invert flags fed to the motion transform. */
    public record AxisConfig(boolean swapXY, boolean invertX, boolean invertY) {
    }

    // Which signed motor axis a screen direction must command. Encodes "+X / -X / +Y / -Y".
    private enum Motor { PLUS_X, MINUS_X, PLUS_Y, MINUS_Y }

    /**
     * Solves the <em>effective</em> transform (the composite the motion code applies) from the two
     * observations, or empty if they're inconsistent (e.g. both axes reported horizontal, which
     * can't happen for orthogonal axes — a sign the operator mis-clicked).
     *
     * @param rawXObserved physical direction the pen moved for a raw motor <b>+X</b> jog
     * @param rawYObserved physical direction the pen moved for a raw motor <b>+Y</b> jog
     */
    public static Optional<AxisConfig> solveEffective(Dir rawXObserved, Dir rawYObserved) {
        if (rawXObserved.horizontal() == rawYObserved.horizontal()) {
            return Optional.empty(); // the two motor axes must map to perpendicular screen directions
        }
        Motor forRight = motorFor(Dir.RIGHT, rawXObserved, rawYObserved);
        Motor forUp = motorFor(Dir.UP, rawXObserved, rawYObserved);

        boolean swap = forRight == Motor.PLUS_Y || forRight == Motor.MINUS_Y;
        boolean invertX;
        boolean invertY;
        if (!swap) {
            invertX = forRight == Motor.MINUS_X;
            invertY = forUp == Motor.MINUS_Y;
        } else {
            // swapped: screen +X → motor ±Y (sign = invertY), screen +Y → motor ±X (sign = invertX)
            invertY = forRight == Motor.MINUS_Y;
            invertX = forUp == Motor.MINUS_X;
        }
        return Optional.of(new AxisConfig(swap, invertX, invertY));
    }

    /** The signed motor axis whose physical result is {@code want}, given the two observations. */
    private static Motor motorFor(Dir want, Dir rawX, Dir rawY) {
        if (rawX == want) return Motor.PLUS_X;
        if (rawX.opposite() == want) return Motor.MINUS_X;
        if (rawY == want) return Motor.PLUS_Y;
        return Motor.MINUS_Y; // rawY.opposite() == want
    }

    /**
     * Backs out the stored "extra" flags to persist on {@link GantryConfig} so that, given the
     * operator's stated origin corner, {@code toPlotSettings()} reproduces {@code effective}. The
     * origin contributes a baseline inversion (right ⇒ invertX, bottom ⇒ invertY) that the stored
     * flag XORs against — the same relationship {@code toPlotSettings} uses, solved the other way.
     *
     * <p>Swap has no origin baseline here; if the machine runs in Portrait on a landscape bed (where
     * {@code toPlotSettings} adds its own swap), run calibration in the final orientation.
     */
    public static AxisConfig toStoredExtra(AxisConfig effective, boolean originRight, boolean originBottom) {
        return new AxisConfig(
                effective.swapXY(),
                originRight ^ effective.invertX(),
                originBottom ^ effective.invertY());
    }
}
