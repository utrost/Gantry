package org.trostheide.svgtoolbox.patterns;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.HatchStyle;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class DotHatchPattern implements HatchPattern {

    @Override
    public List<Shape> generate(Shape shape, Config config, HatchStyle style) {
        // Dot pattern: distribute circles inside the shape.
        // We can check containment directly.
        List<Shape> result = new ArrayList<>();
        double gap = style.gap();
        // Gap controls distance between dots.
        // Angle controls grid rotation.

        AffineTransform toAligned = AffineTransform.getRotateInstance(Math.toRadians(-style.angle()));
        Shape alignedShape = toAligned.createTransformedShape(shape);
        java.awt.geom.Rectangle2D bounds = alignedShape.getBounds2D();

        double radius = config.strokeWidth() > 0 ? config.strokeWidth() : 1.0;
        // Actually, stroke-width is for lines. for dots, we might want them slightly
        // larger or same.
        // Let's use `gap / 5` as dot radius, or just use a small fixed visual size.
        // Or re-use stroke-width as radius?

        double startX = bounds.getMinX();
        double startY = bounds.getMinY();
        double endX = bounds.getMaxX();
        double endY = bounds.getMaxY();

        // Simple Grid
        for (double y = startY; y < endY; y += gap) {
            for (double x = startX; x < endX; x += gap) {
                if (alignedShape.contains(x, y)) {
                    // Create DOT
                    Point2D p = new Point2D.Double(x, y);
                    try {
                        Point2D w = toAligned.createInverse().transform(p, null);
                        // Create a small circle
                        result.add(new Ellipse2D.Double(w.getX() - radius, w.getY() - radius, radius * 2, radius * 2));
                    } catch (Exception e) {
                    }
                }
            }
        }
        return result;
    }
}
