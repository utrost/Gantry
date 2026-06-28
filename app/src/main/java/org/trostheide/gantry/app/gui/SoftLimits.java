package org.trostheide.gantry.app.gui;

/**
 * Optional jog soft limits: clamp a relative jog delta so the commanded position stays inside the
 * bed rectangle {@code [0,maxX] × [0,maxY]} — i.e. the 0/0 corner and the opposite (width/height)
 * corner. Lets the operator hold a jog button without driving the gantry past either end of an axis.
 *
 * <p>Each axis is clamped independently, so a diagonal jog into a corner slides along the wall
 * rather than stopping dead.
 */
final class SoftLimits {

    private SoftLimits() {
    }

    /**
     * @return the largest part of {@code {dx,dy}} that keeps {@code (curX+dx, curY+dy)} within
     *         {@code [0,maxX] × [0,maxY]}; a component is reduced (possibly to 0) when it would
     *         otherwise cross a wall.
     */
    static double[] clampDelta(double curX, double curY, double dx, double dy, double maxX, double maxY) {
        return new double[] {clampAxis(curX, dx, maxX), clampAxis(curY, dy, maxY)};
    }

    private static double clampAxis(double cur, double delta, double max) {
        double target = cur + delta;
        if (target < 0) {
            target = 0;
        } else if (target > max) {
            target = max;
        }
        return target - cur;
    }
}
