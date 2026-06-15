package org.trostheide.svgtoolbox;

public record HatchStyle(double angle, double gap, String patternName) {
    // Static factory for default fallback
    public static HatchStyle of(double angle, double gap) {
        return new HatchStyle(angle, gap, "linear");
    }
}