package org.trostheide.gantry.app.gui;

/**
 * Optional jog soft limits, orientation-aware: keeps the head inside the bed regardless of the
 * machine origin corner or any inverted/swapped axes.
 *
 * <p>A motor position is first mapped to the on-screen "offset from the origin corner"
 * (dx = rightward, dy = upward) using the same invert→swap pipeline the visualization uses to
 * draw the cursor. In that frame the bed is a fixed rectangle whose corner is the machine origin,
 * so clamping is unambiguous; the clamped offset is then mapped back to a motor position. This is
 * why the soft stop always lands on the physical 0/0 and width/height edges even when X/Y are
 * inverted, swapped, or the origin is a different corner.
 */
final class SoftLimits {

    private SoftLimits() {
    }

    /**
     * @return {@code motor(x,y)} clamped to the bed. {@code bedWidth}/{@code bedHeight} are the
     *         on-screen (display) bed extents in mm; {@code originRight}/{@code originBottom} mark
     *         which corner the machine origin is, which fixes the sign of the bed in screen space.
     */
    static double[] clampMotorToBed(double motorX, double motorY,
                                    boolean invertX, boolean invertY, boolean swap,
                                    double bedWidth, double bedHeight,
                                    boolean originRight, boolean originBottom) {
        // motor -> screen offset from the origin corner (dx rightward, dy upward)
        double dx = motorX;
        double dy = motorY;
        if (invertY) {
            dy = -dy;
        }
        if (invertX) {
            dx = -dx;
        }
        if (swap) {
            double t = dx;
            dx = dy;
            dy = t;
        }

        // Clamp within the bed; the origin corner determines which side it extends to.
        dx = originRight ? clamp(dx, -bedWidth, 0) : clamp(dx, 0, bedWidth);
        dy = originBottom ? clamp(dy, -bedHeight, 0) : clamp(dy, 0, bedHeight);

        // screen offset -> motor (inverse: un-swap, then un-invert)
        if (swap) {
            double t = dx;
            dx = dy;
            dy = t;
        }
        if (invertX) {
            dx = -dx;
        }
        if (invertY) {
            dy = -dy;
        }
        return new double[] {dx, dy};
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
