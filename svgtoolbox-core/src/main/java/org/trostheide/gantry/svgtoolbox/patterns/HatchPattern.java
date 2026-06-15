package org.trostheide.gantry.svgtoolbox.patterns;

import org.trostheide.gantry.svgtoolbox.Config;
import org.trostheide.gantry.svgtoolbox.HatchStyle;

import java.awt.Shape;
import java.util.List;

public interface HatchPattern {
    /**
     * Generates hatch geometry for a specific shape.
     * 
     * @param shape  The shape to fill.
     * @param config The global configuration.
     * @param style  The specific hatch style (angle, gap, etc) derived for this
     *               shape.
     * @return List of Shape objects representing the fill.
     */
    List<Shape> generate(Shape shape, Config config, HatchStyle style);
}
