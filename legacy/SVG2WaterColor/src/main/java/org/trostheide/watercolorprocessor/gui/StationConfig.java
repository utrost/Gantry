package org.trostheide.watercolorprocessor.gui;

/**
 * Configuration for a single refill station.
 * Used for persistence (JSON) and station editor UI.
 */
public record StationConfig(double x, double y, int z_down, String behavior) {
}
