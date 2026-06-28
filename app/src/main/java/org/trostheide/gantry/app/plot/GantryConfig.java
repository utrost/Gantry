package org.trostheide.gantry.app.plot;

import org.trostheide.gantry.plotter.GcodeOptions;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persisted plotter configuration: hardware ({@link GcodeOptions}), the coordinate-transform
 * geometry (machine origin corner, orientation, canvas alignment, rotation/padding) and refill
 * stations. Serialized as {@code config.json}.
 */
public class GantryConfig {

    public GcodeOptions gcode = new GcodeOptions();

    public boolean mock;

    /** "Top-Left", "Top-Right", "Bottom-Left" or "Bottom-Right": the machine's physical origin corner. */
    public String machineOrigin = "Top-Right";

    /** "Landscape" or "Portrait". */
    public String orientation = "Landscape";

    /** Extra axis flip/swap on top of what {@link #machineOrigin} implies. */
    public boolean invertX;
    public boolean invertY;
    public boolean swapXY;
    public boolean flipY;

    /** "Top Left", "Top Right", "Bottom Left", "Bottom Right", "Center", or null to disable. */
    public String canvasAlignment = "Top Right";

    /** Rotation of the input drawing in degrees: 0, 90, 180 or 270. */
    public int dataRotation;

    public double paddingX;
    public double paddingY;

    public Map<String, StationConfig> stations = new LinkedHashMap<>();

    /** Last directory a file chooser (Import SVG, Load/Save Commands, Export/Replay G-code) was opened in. */
    public String lastDirectory;

    /** If true, clicking Start routes through the Pre-Plot Checklist wizard instead of plotting directly. */
    public boolean preflightBeforeStart = true;

    /**
     * If true, jog moves are clamped so the commanded position stays within the bed
     * {@code [0,width] × [0,height]} (the 0/0 corner and the opposite corner) — a soft stop in
     * addition to the physical 0/0 origin, so holding a jog button can't over-travel an axis.
     */
    public boolean softLimits = true;

    /** Whether the machine has physical limit/homing switches (recorded by the calibration wizard). */
    public boolean hasLimitSwitches;

    /**
     * Derives {@link PlotSettings} from this config, applying the same machine-origin and
     * portrait-mode axis derivation rules used throughout the plot pipeline:
     * <ul>
     *   <li>{@code invertX}/{@code originRight} default from "Right" in {@link #machineOrigin}</li>
     *   <li>{@code invertY} defaults from "Bottom" in {@link #machineOrigin}</li>
     *   <li>extra {@link #invertX}/{@link #invertY}/{@link #swapXY} flags are OR'd in</li>
     *   <li>if {@link #orientation} is "Portrait" and the bed is wider than it is tall, invertX/invertY
     *       swap with each other, swapXY toggles, and the canvas alignment corner is translated</li>
     * </ul>
     */
    public PlotSettings toPlotSettings() {
        PlotSettings settings = new PlotSettings();
        settings.machineWidth = gcode.machineWidth;
        settings.machineHeight = gcode.machineHeight;
        settings.paddingX = paddingX;
        settings.paddingY = paddingY;
        settings.dataRotation = dataRotation;
        settings.flipY = flipY;
        settings.stations = stations;

        boolean originRight = machineOrigin.toLowerCase().contains("right");
        boolean originBottom = machineOrigin.toLowerCase().contains("bottom");

        // The origin corner sets the baseline axis directions; Extra Invert X/Y are manual
        // *corrections* on top of that (e.g. for a rotated machine or reversed motor wiring), so
        // they must be able to cancel an origin-derived inversion, not only add one. Hence XOR —
        // with OR the checkbox (and the Calibrate Axes wizard) could never un-invert a bottom/right
        // origin's axis.
        boolean effInvertX = originRight ^ invertX;
        boolean effInvertY = originBottom ^ invertY;
        boolean effSwapXY = swapXY;

        String align = canvasAlignment;

        boolean needsAxisSwap = "Portrait".equals(orientation) && gcode.machineWidth > gcode.machineHeight;
        if (needsAxisSwap) {
            boolean tmp = effInvertX;
            effInvertX = effInvertY;
            effInvertY = tmp;
            effSwapXY = !effSwapXY;
            if (align != null) {
                align = translateAlignmentForPortrait(align, originRight, originBottom);
            }
        }

        settings.invertX = effInvertX;
        settings.invertY = effInvertY;
        settings.swapXY = effSwapXY;
        settings.originRight = originRight;
        settings.canvasAlign = align;

        return settings;
    }

    /**
     * In portrait mode, the alignment corners sharing exactly one component with the origin
     * corner swap with each other; the origin corner and its diagonal are unchanged.
     */
    public static String translateAlignmentForPortrait(String label, boolean originRight, boolean originBottom) {
        boolean xor = originRight ^ originBottom;
        if (xor) {
            if ("Top Left".equals(label)) return "Bottom Right";
            if ("Bottom Right".equals(label)) return "Top Left";
        } else {
            if ("Top Right".equals(label)) return "Bottom Left";
            if ("Bottom Left".equals(label)) return "Top Right";
        }
        return label;
    }
}
