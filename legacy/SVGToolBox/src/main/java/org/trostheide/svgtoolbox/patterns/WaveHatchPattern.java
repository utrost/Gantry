package org.trostheide.svgtoolbox.patterns;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.HatchStyle;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.NoninvertibleTransformException;
import java.util.ArrayList;
import java.util.List;

public class WaveHatchPattern extends LinearHatchPattern {

    @Override
    public List<Shape> generate(Shape shape, Config config, HatchStyle style) {
        List<Shape> result = new ArrayList<>();
        double angleDeg = style.angle();
        double gap = style.gap();

        AffineTransform toAligned = AffineTransform.getRotateInstance(Math.toRadians(-angleDeg));
        Shape alignedShape = toAligned.createTransformedShape(shape);
        java.awt.geom.Rectangle2D bounds = alignedShape.getBounds2D();
        double startY = bounds.getMinY();
        double endY = bounds.getMaxY();

        // Copy-paste flatten logic since it is private in parent
        java.awt.geom.PathIterator pi = alignedShape.getPathIterator(null, 0.5);
        List<Line2D> edges = new ArrayList<>();
        double[] coords = new double[6];
        double startX = 0, startYCoords = 0;
        double currX = 0, currY = 0;
        while (!pi.isDone()) {
            int type = pi.currentSegment(coords);
            switch (type) {
                case java.awt.geom.PathIterator.SEG_MOVETO:
                    startX = currX = coords[0];
                    startYCoords = currY = coords[1];
                    break;
                case java.awt.geom.PathIterator.SEG_LINETO:
                    edges.add(new Line2D.Double(currX, currY, coords[0], coords[1]));
                    currX = coords[0];
                    currY = coords[1];
                    break;
                case java.awt.geom.PathIterator.SEG_CLOSE:
                    edges.add(new Line2D.Double(currX, currY, startX, startYCoords));
                    currX = startX;
                    currY = startYCoords;
                    break;
            }
            pi.next();
        }

        double frequency = 2 * Math.PI / (gap * 2); // Wavelength = 2 * gap
        double amplitude = gap / 3.0;

        for (double y = startY + gap; y < endY; y += gap) {
            List<Double> intersections = new ArrayList<>();
            for (Line2D edge : edges) {
                if ((edge.getY1() <= y && edge.getY2() > y) || (edge.getY2() <= y && edge.getY1() > y)) {
                    double dy = edge.getY2() - edge.getY1();
                    double x;
                    if (Math.abs(dy) < 0.00001)
                        x = edge.getX1();
                    else
                        x = edge.getX1() + (y - edge.getY1()) * (edge.getX2() - edge.getX1()) / dy;
                    intersections.add(x);
                }
            }
            java.util.Collections.sort(intersections);

            for (int i = 0; i < intersections.size() - 1; i += 2) {
                double x1 = intersections.get(i);
                double x2 = intersections.get(i + 1);

                // Construct Sine Wave
                Path2D wave = new Path2D.Double();
                wave.moveTo(x1, y + Math.sin(x1 * frequency) * amplitude);

                double step = 1.0; // 1 unit resolution
                for (double cx = x1 + step; cx < x2; cx += step) {
                    wave.lineTo(cx, y + Math.sin(cx * frequency) * amplitude);
                }
                wave.lineTo(x2, y + Math.sin(x2 * frequency) * amplitude);

                try {
                    AffineTransform toWorld = toAligned.createInverse();
                    result.add(toWorld.createTransformedShape(wave));
                } catch (NoninvertibleTransformException e) {
                }
            }
        }
        return result;
    }
}
