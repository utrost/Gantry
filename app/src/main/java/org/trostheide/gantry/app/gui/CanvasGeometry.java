package org.trostheide.gantry.app.gui;

/** Small screen-space geometry operations shared by canvas interaction tools. */
final class CanvasGeometry {
    private CanvasGeometry() { }

    static double distanceToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double[] closest = closestOnSegment(px, py, x1, y1, x2, y2);
        return Math.hypot(px - closest[0], py - closest[1]);
    }

    static double[] closestOnSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared < 1e-9) return new double[]{x1, y1};
        double t = ((px - x1) * dx + (py - y1) * dy) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        return new double[]{x1 + t * dx, y1 + t * dy};
    }
}
