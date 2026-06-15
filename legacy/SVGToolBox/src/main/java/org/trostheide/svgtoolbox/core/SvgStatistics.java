package org.trostheide.svgtoolbox.core;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.awt.geom.PathIterator;
import java.util.concurrent.atomic.AtomicInteger;

public class SvgStatistics {

    public record Stats(int elementCount, double totalLengthMeters) {
    }

    public static Stats analyze(Document doc) {
        AtomicInteger count = new AtomicInteger(0);
        double totalLengthPx = 0.0;

        NodeList elements = doc.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);
            String tagName = el.getTagName().toLowerCase();

            if (isShape(tagName)) {
                count.incrementAndGet();
                totalLengthPx += estimateLength(el);
            }
        }

        // Convert px to meters (assuming 96 DPI default)
        // 1 inch = 25.4mm. 96px = 25.4mm.
        // 1px = 0.264583333 mm.
        double mm = totalLengthPx * 0.264583333;
        double meters = mm / 1000.0;

        return new Stats(count.get(), meters);
    }

    private static boolean isShape(String tag) {
        return tag.equals("path") || tag.equals("line") || tag.equals("rect") ||
                tag.equals("circle") || tag.equals("ellipse") || tag.equals("polyline") ||
                tag.equals("polygon");
    }

    private static double estimateLength(Element el) {
        java.awt.Shape shape = ShapeParser.parse(el);
        if (shape == null)
            return 0.0;
        return calculateFlattenedLength(shape);
    }

    private static double calculateFlattenedLength(java.awt.Shape shape) {
        PathIterator pi = shape.getPathIterator(null, 0.5); // 0.5px tolerance
        double total = 0.0;
        double[] c = new double[6];
        double lastX = 0;
        double lastY = 0;
        double moveX = 0;
        double moveY = 0;

        while (!pi.isDone()) {
            int type = pi.currentSegment(c);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    moveX = lastX = c[0];
                    moveY = lastY = c[1];
                    break;
                case PathIterator.SEG_LINETO:
                    total += java.awt.geom.Point2D.distance(lastX, lastY, c[0], c[1]);
                    lastX = c[0];
                    lastY = c[1];
                    break;
                case PathIterator.SEG_CLOSE:
                    total += java.awt.geom.Point2D.distance(lastX, lastY, moveX, moveY);
                    lastX = moveX;
                    lastY = moveY;
                    break;
            }
            pi.next();
        }
        return total;
    }
}
