package org.trostheide.gantry.app.plot;

import java.util.HashMap;
import java.util.Map;

/**
 * Plot-time configuration: machine geometry, coordinate transform flags,
 * canvas alignment and refill stations.
 */
public class PlotSettings {

    /** Plotter model, used only to derive default machine dimensions when none are set. */
    public int model = 1;

    public boolean invertX;
    public boolean invertY;
    public boolean swapXY;
    public boolean flipY;

    /** Rotation of the input drawing in degrees: 0, 90, 180 or 270. */
    public int dataRotation = 0;

    /** "top-left", "top-right", "bottom-left", "bottom-right", "center", or null to disable. */
    public String canvasAlign;

    /**
     * When non-null, this {x, y} offset (mm) is used verbatim instead of recomputing the
     * alignment offset from content bounds. Lets the GUI plot exactly what the live preview
     * shows, including interactive drag/numeric positioning, instead of re-aligning the
     * (already-baked) content and silently undoing the placement.
     */
    public double[] alignmentOffsetOverride;

    /** True when the machine's physical origin is at the top-right. */
    public boolean originRight;

    /** True when the machine's physical origin is on the bottom edge. */
    public boolean originBottom;

    public double paddingX;
    public double paddingY;

    /** Machine bed size in mm. Null falls back to the model defaults. */
    public Double machineWidth;
    public Double machineHeight;

    /** Print drift between commanded and actual hardware position after each move. */
    public boolean debugPosition;

    /** Report the commanded position via the position callback (suppressed if realtimePosition is true). */
    public boolean reportPosition;

    /** True when the backend reports its own realtime position via a callback. */
    public boolean realtimePosition;

    public Map<String, StationConfig> stations = new HashMap<>();

    /** Resolves {@link #machineWidth}, falling back to the model default (300mm for model 1, 430mm for model 2). */
    public double resolveMachineWidth() {
        if (machineWidth != null) {
            return machineWidth;
        }
        return model == 1 ? 300.0 : 430.0;
    }

    /** Resolves {@link #machineHeight}, falling back to the model default (215mm for model 1, 297mm for model 2). */
    public double resolveMachineHeight() {
        if (machineHeight != null) {
            return machineHeight;
        }
        return model == 1 ? 215.0 : 297.0;
    }
}
