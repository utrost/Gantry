package org.trostheide.gantry.svgtoolbox;

/**
 * A hatch style: the {@code angle} and {@code gap} drive every pattern, while
 * {@code amplitude}, {@code wavelength} and {@code dotRadius} tune the
 * non-linear patterns. Those three are optional — a value of {@code 0} means
 * "auto", i.e. fall back to the historical gap-derived default for that pattern,
 * keeping older configs (and the 3-arg constructor) behaving exactly as before.
 */
public record HatchStyle(double angle, double gap, String patternName,
                         double amplitude, double wavelength, double dotRadius) {

    /** Backward-compatible style with all per-pattern parameters left on "auto". */
    public HatchStyle(double angle, double gap, String patternName) {
        this(angle, gap, patternName, 0.0, 0.0, 0.0);
    }

    public static HatchStyle of(double angle, double gap) {
        return new HatchStyle(angle, gap, "linear");
    }
}
