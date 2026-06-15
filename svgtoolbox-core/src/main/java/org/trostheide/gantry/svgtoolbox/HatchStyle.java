package org.trostheide.gantry.svgtoolbox;

public record HatchStyle(double angle, double gap, String patternName) {
    public static HatchStyle of(double angle, double gap) {
        return new HatchStyle(angle, gap, "linear");
    }
}
