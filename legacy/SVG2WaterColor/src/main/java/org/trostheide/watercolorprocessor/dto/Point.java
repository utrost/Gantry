package org.trostheide.watercolorprocessor.dto;

/**
 * Represents a 2D coordinate in the plotting space.
 * Used for polyline definitions.
 *
 * @param x The X coordinate in mm.
 * @param y The Y coordinate in mm.
 */
public record Point(double x, double y) {}