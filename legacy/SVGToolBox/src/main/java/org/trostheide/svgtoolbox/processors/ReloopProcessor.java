package org.trostheide.svgtoolbox.processors;

import org.apache.batik.parser.AWTPathProducer;
import org.apache.batik.parser.PathParser;
import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.Processor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Rotates the starting point of closed paths (ending with Z/z) to the vertex
 * nearest the current pen position (end of the previous path), minimizing
 * pen-travel when approaching closed shapes. Must run after LinesortProcessor.
 */
public class ReloopProcessor implements Processor {

    @Override
    public void process(Document doc, Config config) {
        if (!config.reloop()) return;

        NodeList groups = doc.getElementsByTagName("g");
        if (groups.getLength() > 0) {
            for (int i = 0; i < groups.getLength(); i++) {
                reloopGroup((Element) groups.item(i));
            }
        } else {
            reloopGroup(doc.getDocumentElement());
        }
        System.out.println("Reloop: closed-path start points optimized");
    }

    private void reloopGroup(Element group) {
        Point2D.Double penPos = new Point2D.Double(0, 0);

        NodeList kids = group.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (!(n instanceof Element)) continue;
            Element el = (Element) n;
            if (!"path".equals(el.getTagName())) continue;

            String d = el.getAttribute("d");
            if (d == null || d.trim().isEmpty()) continue;

            if (isClosedPath(d)) {
                List<Segment> segments = parseSegments(d);
                if (segments.size() >= 2) {
                    int bestIdx = findClosestVertex(segments, penPos);
                    if (bestIdx > 0) {
                        String newD = rotatePathData(segments, bestIdx);
                        el.setAttribute("d", newD);
                    }
                }
            }

            // Update pen position to end of this path
            penPos = getEndPoint(el);
        }
    }

    private boolean isClosedPath(String d) {
        String trimmed = d.trim();
        return trimmed.endsWith("Z") || trimmed.endsWith("z");
    }

    private int findClosestVertex(List<Segment> segments, Point2D.Double target) {
        double bestDist = Double.MAX_VALUE;
        int bestIdx = 0;
        for (int i = 0; i < segments.size(); i++) {
            Point2D.Double ep = segments.get(i).endpoint;
            double dist = target.distanceSq(ep);
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /**
     * Rotates the path so that the vertex at rotateIdx becomes the new start.
     * Original: M p0, seg→p1, seg→p2, ..., seg→pN-1, Z
     * Rotated to k: M pk, seg→pk+1, ..., seg→pN-1, L p0, seg→p1, ..., seg→pk-1, Z
     * (the implicit close edge becomes an explicit LINETO)
     */
    private String rotatePathData(List<Segment> segments, int rotateIdx) {
        int n = segments.size();
        StringBuilder sb = new StringBuilder();

        // New MOVETO at the rotation point
        Point2D.Double startPt = segments.get(rotateIdx).endpoint;
        sb.append(String.format("M %.4f %.4f", startPt.x, startPt.y));

        // Emit segments after rotateIdx (indices rotateIdx+1 through n-1)
        for (int i = rotateIdx + 1; i < n; i++) {
            sb.append(' ').append(segments.get(i).toSvg());
        }

        // Bridge: explicit LINETO to original start (p0) — replaces implicit close edge
        Point2D.Double origStart = segments.get(0).endpoint;
        sb.append(String.format(" L %.4f %.4f", origStart.x, origStart.y));

        // Emit segments 1 through rotateIdx (going from p0 to pk)
        for (int i = 1; i <= rotateIdx; i++) {
            sb.append(' ').append(segments.get(i).toSvg());
        }

        sb.append(" Z");
        return sb.toString();
    }

    // --- Path parsing ---

    static class Segment {
        int type; // PathIterator segment type
        double[] coords; // raw coords from PathIterator
        Point2D.Double endpoint; // the destination point of this segment

        String toSvg() {
            return switch (type) {
                case PathIterator.SEG_MOVETO ->
                        String.format("M %.4f %.4f", coords[0], coords[1]);
                case PathIterator.SEG_LINETO ->
                        String.format("L %.4f %.4f", coords[0], coords[1]);
                case PathIterator.SEG_QUADTO ->
                        String.format("Q %.4f %.4f %.4f %.4f", coords[0], coords[1], coords[2], coords[3]);
                case PathIterator.SEG_CUBICTO ->
                        String.format("C %.4f %.4f %.4f %.4f %.4f %.4f",
                                coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
                default -> "";
            };
        }
    }

    private List<Segment> parseSegments(String d) {
        List<Segment> segments = new ArrayList<>();
        try {
            AWTPathProducer producer = new AWTPathProducer();
            producer.setWindingRule(java.awt.geom.Path2D.WIND_EVEN_ODD);
            PathParser parser = new PathParser();
            parser.setPathHandler(producer);
            parser.parse(d);

            Shape shape = producer.getShape();
            PathIterator pi = shape.getPathIterator(null);
            double[] coords = new double[6];

            while (!pi.isDone()) {
                int type = pi.currentSegment(coords);
                if (type == PathIterator.SEG_CLOSE) {
                    pi.next();
                    continue;
                }

                Segment seg = new Segment();
                seg.type = type;
                seg.coords = coords.clone();

                switch (type) {
                    case PathIterator.SEG_MOVETO:
                    case PathIterator.SEG_LINETO:
                        seg.endpoint = new Point2D.Double(coords[0], coords[1]);
                        break;
                    case PathIterator.SEG_QUADTO:
                        seg.endpoint = new Point2D.Double(coords[2], coords[3]);
                        break;
                    case PathIterator.SEG_CUBICTO:
                        seg.endpoint = new Point2D.Double(coords[4], coords[5]);
                        break;
                }

                segments.add(seg);
                pi.next();
            }
        } catch (Exception e) {
            // Parse failure — return empty to skip this path
        }
        return segments;
    }

    private Point2D.Double getEndPoint(Element e) {
        String d = e.getAttribute("d");
        if (d == null || d.trim().isEmpty()) return new Point2D.Double(0, 0);

        try {
            AWTPathProducer producer = new AWTPathProducer();
            producer.setWindingRule(java.awt.geom.Path2D.WIND_EVEN_ODD);
            PathParser parser = new PathParser();
            parser.setPathHandler(producer);
            parser.parse(d);

            Shape shape = producer.getShape();
            PathIterator pi = shape.getPathIterator(null);
            double[] coords = new double[6];
            Point2D.Double last = new Point2D.Double(0, 0);

            while (!pi.isDone()) {
                int type = pi.currentSegment(coords);
                switch (type) {
                    case PathIterator.SEG_MOVETO:
                    case PathIterator.SEG_LINETO:
                        last = new Point2D.Double(coords[0], coords[1]);
                        break;
                    case PathIterator.SEG_QUADTO:
                        last = new Point2D.Double(coords[2], coords[3]);
                        break;
                    case PathIterator.SEG_CUBICTO:
                        last = new Point2D.Double(coords[4], coords[5]);
                        break;
                }
                pi.next();
            }

            // For closed paths, the end point is the start point (pen returns to start)
            if (isClosedPath(d)) {
                List<Segment> segs = parseSegments(d);
                if (!segs.isEmpty()) {
                    return segs.get(0).endpoint;
                }
            }

            return last;
        } catch (Exception ex) {
            return new Point2D.Double(0, 0);
        }
    }
}
