package org.trostheide.svgtoolbox.patterns;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.HatchStyle;

import java.awt.Shape;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LinearHatchPattern implements HatchPattern {

    @Override
    public List<Shape> generate(Shape shape, Config config, HatchStyle style) {
        // We only generate one pass here. Cross-hatching is handled by
        // CrossHatchPattern.
        return new ArrayList<>(scanlineHatch(shape, style.angle(), style.gap()));
    }

    protected List<Line2D> scanlineHatch(Shape shape, double angleDeg, double gap) {
        List<Line2D> result = new ArrayList<>();
        AffineTransform toAligned = AffineTransform.getRotateInstance(Math.toRadians(-angleDeg));
        Shape alignedShape = toAligned.createTransformedShape(shape);
        Rectangle2D bounds = alignedShape.getBounds2D();
        double startY = bounds.getMinY();
        double endY = bounds.getMaxY();

        PathIterator pi = alignedShape.getPathIterator(null, 0.5);
        List<Line2D> edges = flattenPath(pi);

        // Adjust startY to be a multiple of gap to ensure alignment across shapes?
        // Current implementation just starts at bounds.MinY + gap.
        // For consistent texturing, it is better to lock to global grid.
        // But let's keep original behavior for regression test first.
        AffineTransform toWorld;
        try {
            toWorld = toAligned.createInverse();
        } catch (NoninvertibleTransformException e) {
            return result; // Should not happen for rotation
        }

        for (double y = startY + gap; y < endY; y += gap) {
            List<Double> intersections = new ArrayList<>();
            for (Line2D edge : edges) {
                if (lineIntersectsY(edge, y)) {
                    double x = getXAtY(edge, y);
                    intersections.add(x);
                }
            }
            Collections.sort(intersections);
            for (int i = 0; i < intersections.size() - 1; i += 2) {
                double x1 = intersections.get(i);
                double x2 = intersections.get(i + 1);
                Point2D p1 = new Point2D.Double(x1, y);
                Point2D p2 = new Point2D.Double(x2, y);
                Point2D w1 = toWorld.transform(p1, null);
                Point2D w2 = toWorld.transform(p2, null);
                result.add(new Line2D.Double(w1, w2));
            }
        }
        return result;
    }

    private boolean lineIntersectsY(Line2D l, double y) {
        return (l.getY1() <= y && l.getY2() > y) || (l.getY2() <= y && l.getY1() > y);
    }

    private double getXAtY(Line2D l, double y) {
        double dy = l.getY2() - l.getY1();
        if (Math.abs(dy) < 0.00001)
            return l.getX1();
        return l.getX1() + (y - l.getY1()) * (l.getX2() - l.getX1()) / dy;
    }

    private List<Line2D> flattenPath(PathIterator pi) {
        List<Line2D> edges = new ArrayList<>();
        double[] coords = new double[6];
        double startX = 0, startY = 0;
        double currX = 0, currY = 0;
        while (!pi.isDone()) {
            int type = pi.currentSegment(coords);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    startX = currX = coords[0];
                    startY = currY = coords[1];
                    break;
                case PathIterator.SEG_LINETO:
                    edges.add(new Line2D.Double(currX, currY, coords[0], coords[1]));
                    currX = coords[0];
                    currY = coords[1];
                    break;
                case PathIterator.SEG_CLOSE:
                    edges.add(new Line2D.Double(currX, currY, startX, startY));
                    currX = startX;
                    currY = startY;
                    break;
            }
            pi.next();
        }
        return edges;
    }
}
