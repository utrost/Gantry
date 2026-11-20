package org.trostheide.gantry.app.plot;

import org.trostheide.gantry.model.CoordinateTransform;
import java.awt.geom.AffineTransform;

/** Immutable presentation of motor coordinates on the same physical page as the workspace.
 * Does not transform executable commands or change machine configuration.
 */
public record MachinePageProjection(double motorWidthMm, double motorHeightMm,
                                    boolean swapXY, boolean originRight, boolean originBottom) {
    public static MachinePageProjection from(PlotSettings settings) {
        return new MachinePageProjection(settings.resolveMachineWidth(), settings.resolveMachineHeight(),
                settings.swapXY, settings.originRight, settings.originBottom);
    }

    public double widthMm() { return swapXY ? motorHeightMm : motorWidthMm; }
    public double heightMm() { return swapXY ? motorWidthMm : motorHeightMm; }

    public double[] point(double x, double y) {
        return CoordinateTransform.physicalToScreen(x, y, swapXY, originRight, originBottom,
                motorWidthMm, motorHeightMm);
    }

    public AffineTransform transform() {
        double[] zero = point(0, 0), x = point(1, 0), y = point(0, 1);
        return new AffineTransform(x[0] - zero[0], x[1] - zero[1],
                y[0] - zero[0], y[1] - zero[1], zero[0], zero[1]);
    }

    /** Project a motor-grid cell centre without recomputing coverage or resampling counts. */
    public double[] cell(int x, int y, int columns, int rows) {
        return CoordinateTransform.physicalToScreen(x + .5, y + .5, swapXY,
                originRight, originBottom, columns, rows);
    }
}
