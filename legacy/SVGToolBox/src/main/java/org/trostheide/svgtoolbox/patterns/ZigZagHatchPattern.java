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

public class ZigZagHatchPattern extends LinearHatchPattern {

    @Override
    protected List<Line2D> scanlineHatch(Shape shape, double angleDeg, double gap) {
        // We override this to produce paths instead of lines, but the interface returns
        // List<Shape>
        // so we can't easily override scanlineHatch which returns List<Line2D> if we
        // want to return Path2D.
        // So we might need to implement generate() directly or change scanlineHatch
        // signature.
        // However, LinearHatchPattern.scanlineHatch returns List<Line2D>, which cannot
        // hold Path2D.
        // I will implement generate() and copy the relevant logic, or refactor
        // scanlineHatch to return List<Shape>.
        return super.scanlineHatch(shape, angleDeg, gap);
    }

    @Override
    public List<Shape> generate(Shape shape, Config config, HatchStyle style) {
        // We reimplement the logic to support ZigZag paths
        List<Shape> result = new ArrayList<>();
        double angleDeg = style.angle();
        double gap = style.gap();

        AffineTransform toAligned = AffineTransform.getRotateInstance(Math.toRadians(-angleDeg));
        Shape alignedShape = toAligned.createTransformedShape(shape);
        java.awt.geom.Rectangle2D bounds = alignedShape.getBounds2D();
        double startY = bounds.getMinY();
        double endY = bounds.getMaxY();

        java.awt.geom.PathIterator pi = alignedShape.getPathIterator(null, 0.5);
        List<Line2D> edges = new ArrayList<>(); // flattenPath logic needed
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

        double wavelength = gap;
        double amplitude = gap / 2.0;

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

                // Create ZigZag Path between (x1, y) and (x2, y)
                Path2D zigzag = new Path2D.Double();
                zigzag.moveTo(x1, y);

                double currentX = x1;
                boolean up = true;
                while (currentX < x2) {
                    double nextX = Math.min(currentX + (wavelength / 2.0), x2);
                    double nextY = up ? y - amplitude : y + amplitude;
                    zigzag.lineTo(nextX, nextY);
                    currentX = nextX;
                    up = !up;
                }

                // Transform back to world
                try {
                    AffineTransform toWorld = toAligned.createInverse();
                    result.add(toWorld.createTransformedShape(zigzag));
                } catch (NoninvertibleTransformException e) {
                }
            }
        }
        return result;
    }
}
