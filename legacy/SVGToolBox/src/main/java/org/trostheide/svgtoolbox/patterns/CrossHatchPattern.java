package org.trostheide.svgtoolbox.patterns;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.HatchStyle;

import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;

public class CrossHatchPattern implements HatchPattern {

    private final LinearHatchPattern linear = new LinearHatchPattern();

    @Override
    public List<Shape> generate(Shape shape, Config config, HatchStyle style) {
        List<Shape> result = new ArrayList<>();

        // Pass 1: Original Angle
        result.addAll(linear.generate(shape, config, style));

        // Pass 2: Rotated 90 degrees
        HatchStyle crossStyle = new HatchStyle(style.angle() + 90.0, style.gap(), "linear");
        result.addAll(linear.generate(shape, config, crossStyle));

        return result;
    }
}
