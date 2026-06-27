package org.trostheide.gantry.vectorize;

/**
 * A VectorGeometry implementation that holds a raw SVG <path> data string.
 * e.g., "M 10 10 C 20 20, 30 20, 40 10 L 50 50"
 */
public class PathGeometry implements VectorGeometry {

    /**
     * The raw SVG path data string.
     */
    public final String pathData;

    public PathGeometry(String pathData) {
        if (pathData == null) {
            throw new IllegalArgumentException("Path data string cannot be null.");
        }
        this.pathData = pathData;
    }
}