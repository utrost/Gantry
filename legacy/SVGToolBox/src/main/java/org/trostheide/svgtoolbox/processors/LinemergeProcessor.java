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
import java.util.Locale;

/**
 * Merges open paths whose endpoints are within tolerance.
 * Reverses path direction if needed to connect end→start.
 * Skips closed paths (Z command).
 */
public class LinemergeProcessor implements Processor {

    @Override
    public void process(Document doc, Config config) {
        if (!config.linemerge()) return;

        double tolerance = config.linemergeTolerance();
        if (tolerance <= 0) return;

        double tolSq = tolerance * tolerance;

        // Process each <g> group, or root if no groups
        NodeList groups = doc.getElementsByTagName("g");
        if (groups.getLength() > 0) {
            for (int i = 0; i < groups.getLength(); i++) {
                mergePathsInParent((Element) groups.item(i), doc, tolSq);
            }
        } else {
            mergePathsInParent(doc.getDocumentElement(), doc, tolSq);
        }
    }

    private void mergePathsInParent(Element parent, Document doc, double tolSq) {
        // Collect open path elements
        List<PathData> openPaths = new ArrayList<>();
        NodeList children = parent.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element && "path".equals(((Element) n).getTagName())) {
                Element el = (Element) n;
                String d = el.getAttribute("d");
                if (d == null || d.trim().isEmpty()) continue;

                PathData pd = parsePath(el);
                if (pd == null || pd.closed) continue;
                if (pd.points.size() < 2) continue;
                openPaths.add(pd);
            }
        }

        if (openPaths.size() < 2) return;

        // Greedy merge: repeatedly find mergeable pairs
        boolean merged = true;
        while (merged) {
            merged = false;
            for (int i = 0; i < openPaths.size() && !merged; i++) {
                PathData a = openPaths.get(i);
                Point2D.Double aEnd = endPoint(a);

                for (int j = 0; j < openPaths.size() && !merged; j++) {
                    if (i == j) continue;
                    PathData b = openPaths.get(j);

                    // Try a.end → b.start
                    Point2D.Double bStart = startPoint(b);
                    if (aEnd.distanceSq(bStart) <= tolSq) {
                        mergePaths(a, b, false);
                        replaceMerged(parent, doc, a, b, openPaths, i, j);
                        merged = true;
                        continue;
                    }

                    // Try a.end → b.end (reverse b)
                    Point2D.Double bEnd = endPoint(b);
                    if (aEnd.distanceSq(bEnd) <= tolSq) {
                        mergePaths(a, b, true);
                        replaceMerged(parent, doc, a, b, openPaths, i, j);
                        merged = true;
                    }
                }
            }
        }

        System.out.println("Linemerge: " + openPaths.size() + " paths remain in group");
    }

    private void replaceMerged(Element parent, Document doc, PathData a, PathData b,
                               List<PathData> openPaths, int ai, int bi) {
        // Remove b's element from DOM
        parent.removeChild(b.element);

        // Update a's element with merged path
        a.element.setAttribute("d", toPathString(a.points));

        // Remove b from list (adjust index if needed)
        openPaths.remove(b);
    }

    private void mergePaths(PathData a, PathData b, boolean reverseB) {
        List<double[]> bPoints;
        if (reverseB) {
            bPoints = new ArrayList<>(b.points);
            java.util.Collections.reverse(bPoints);
        } else {
            bPoints = b.points;
        }
        // Skip first point of b (it's within tolerance of a's end)
        for (int i = 1; i < bPoints.size(); i++) {
            a.points.add(bPoints.get(i));
        }
    }

    private Point2D.Double startPoint(PathData pd) {
        double[] p = pd.points.get(0);
        return new Point2D.Double(p[0], p[1]);
    }

    private Point2D.Double endPoint(PathData pd) {
        double[] p = pd.points.get(pd.points.size() - 1);
        return new Point2D.Double(p[0], p[1]);
    }

    private PathData parsePath(Element el) {
        String d = el.getAttribute("d");
        try {
            PathParser parser = new PathParser();
            AWTPathProducer producer = new AWTPathProducer();
            parser.setPathHandler(producer);
            parser.parse(d);
            Shape shape = producer.getShape();

            PathIterator pi = shape.getPathIterator(null);
            double[] coords = new double[6];
            List<double[]> points = new ArrayList<>();
            boolean closed = false;

            while (!pi.isDone()) {
                int type = pi.currentSegment(coords);
                switch (type) {
                    case PathIterator.SEG_MOVETO:
                        points.add(new double[]{coords[0], coords[1]});
                        break;
                    case PathIterator.SEG_LINETO:
                        points.add(new double[]{coords[0], coords[1]});
                        break;
                    case PathIterator.SEG_QUADTO:
                        points.add(new double[]{coords[2], coords[3]});
                        break;
                    case PathIterator.SEG_CUBICTO:
                        points.add(new double[]{coords[4], coords[5]});
                        break;
                    case PathIterator.SEG_CLOSE:
                        closed = true;
                        break;
                }
                pi.next();
            }

            PathData pd = new PathData();
            pd.element = el;
            pd.points = points;
            pd.closed = closed;
            return pd;
        } catch (Exception e) {
            return null;
        }
    }

    private String toPathString(List<double[]> points) {
        if (points.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        double[] first = points.get(0);
        sb.append(String.format(Locale.US, "M%.4f,%.4f ", first[0], first[1]));
        for (int i = 1; i < points.size(); i++) {
            double[] pt = points.get(i);
            sb.append(String.format(Locale.US, "L%.4f,%.4f ", pt[0], pt[1]));
        }
        return sb.toString().trim();
    }

    static class PathData {
        Element element;
        List<double[]> points;
        boolean closed;
    }
}
