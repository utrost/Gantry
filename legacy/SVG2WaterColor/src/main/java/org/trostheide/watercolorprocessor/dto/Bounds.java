package org.trostheide.watercolorprocessor.dto;

/**
 * Defines the physical extents of the drawing for safety validation.
 */
public record Bounds(double minX, double minY, double maxX, double maxY) {
    /**
     * Helper to create an empty/initial bounds object.
     */
    public static Bounds empty() {
        return new Bounds(Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE);
    }
}