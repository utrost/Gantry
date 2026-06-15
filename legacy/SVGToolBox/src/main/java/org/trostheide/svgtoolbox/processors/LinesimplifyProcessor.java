package org.trostheide.svgtoolbox.processors;

import org.apache.batik.parser.AWTPathProducer;
import org.apache.batik.parser.PathParser;
import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.Processor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ramer-Douglas-Peucker point reduction on path elements.
 * Reduces the number of points in paths while preserving shape within tolerance.
 */
public class LinesimplifyProcessor implements Processor {

    @Override
    public void process(Document doc, Config config) {
        if (!config.linesimplify()) return;

        double tolerance = config.linesimplifyTolerance();
        if (tolerance <= 0) return;

        NodeList elements = doc.getElementsByTagName("path");
        int count = 0;

        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);
            if (simplifyPath(el, tolerance)) {
                count++;
            }
        }
        System.out.println("Linesimplify: simplified " + count + " paths (tolerance: " + tolerance + ")");
    }

    private boolean simplifyPath(Element el, double tolerance) {
        String d = el.getAttribute("d");
        if (d == null || d.trim().isEmpty()) return false;

        List<Subpath> subpaths = parseSubpaths(d);
        if (subpaths.isEmpty()) return false;

        boolean anySimplified = false;
        for (Subpath sp : subpaths) {
            if (sp.points.size() >= 3) {
                List<double[]> simplified = ramerDouglasPeucker(sp.points, tolerance);
                if (simplified.size() < sp.points.size()) {
                    sp.points = simplified;
                    anySimplified = true;
                }
            }
        }

        if (anySimplified) {
            el.setAttribute("d", toPathString(subpaths));
        }
        return anySimplified;
    }

    private List<Subpath> parseSubpaths(String d) {
        List<Subpath> subpaths = new ArrayList<>();
        try {
            PathParser parser = new PathParser();
            AWTPathProducer producer = new AWTPathProducer();
            parser.setPathHandler(producer);
            parser.parse(d);
            Shape shape = producer.getShape();

            PathIterator pi = shape.getPathIterator(null);
            double[] coords = new double[6];
            Subpath current = null;

            while (!pi.isDone()) {
                int type = pi.currentSegment(coords);
                switch (type) {
                    case PathIterator.SEG_MOVETO:
                        current = new Subpath();
                        current.points.add(new double[]{coords[0], coords[1]});
                        subpaths.add(current);
                        break;
                    case PathIterator.SEG_LINETO:
                        if (current != null) {
                            current.points.add(new double[]{coords[0], coords[1]});
                        }
                        break;
                    case PathIterator.SEG_QUADTO:
                        // Flatten quadratic bezier: keep control point and endpoint
                        if (current != null) {
                            current.points.add(new double[]{coords[2], coords[3]});
                        }
                        break;
                    case PathIterator.SEG_CUBICTO:
                        // Flatten cubic bezier: keep endpoint only
                        if (current != null) {
                            current.points.add(new double[]{coords[4], coords[5]});
                        }
                        break;
                    case PathIterator.SEG_CLOSE:
                        if (current != null) {
                            current.closed = true;
                        }
                        break;
                }
                pi.next();
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
        return subpaths;
    }

    private List<double[]> ramerDouglasPeucker(List<double[]> points, double epsilon) {
        if (points.size() < 3) return new ArrayList<>(points);

        double dmax = 0;
        int index = 0;
        int end = points.size() - 1;

        for (int i = 1; i < end; i++) {
            double d = perpendicularDistance(points.get(i), points.get(0), points.get(end));
            if (d > dmax) {
                index = i;
                dmax = d;
            }
        }

        if (dmax > epsilon) {
            List<double[]> left = ramerDouglasPeucker(points.subList(0, index + 1), epsilon);
            List<double[]> right = ramerDouglasPeucker(points.subList(index, end + 1), epsilon);

            List<double[]> result = new ArrayList<>(left.subList(0, left.size() - 1));
            result.addAll(right);
            return result;
        } else {
            List<double[]> result = new ArrayList<>();
            result.add(points.get(0));
            result.add(points.get(end));
            return result;
        }
    }

    private double perpendicularDistance(double[] p, double[] lineStart, double[] lineEnd) {
        double dx = lineEnd[0] - lineStart[0];
        double dy = lineEnd[1] - lineStart[1];
        double lengthSq = dx * dx + dy * dy;
        if (lengthSq == 0) {
            // lineStart == lineEnd
            double ex = p[0] - lineStart[0];
            double ey = p[1] - lineStart[1];
            return Math.sqrt(ex * ex + ey * ey);
        }
        double area = Math.abs((lineStart[0] * lineEnd[1] + lineEnd[0] * p[1] + p[0] * lineStart[1])
                - (lineEnd[0] * lineStart[1] + p[0] * lineEnd[1] + lineStart[0] * p[1]));
        return area / Math.sqrt(lengthSq);
    }

    private String toPathString(List<Subpath> subpaths) {
        StringBuilder sb = new StringBuilder();
        for (Subpath sp : subpaths) {
            if (sp.points.isEmpty()) continue;
            double[] first = sp.points.get(0);
            sb.append(String.format(Locale.US, "M%.4f,%.4f ", first[0], first[1]));
            for (int i = 1; i < sp.points.size(); i++) {
                double[] pt = sp.points.get(i);
                sb.append(String.format(Locale.US, "L%.4f,%.4f ", pt[0], pt[1]));
            }
            if (sp.closed) {
                sb.append("Z ");
            }
        }
        return sb.toString().trim();
    }

    static class Subpath {
        List<double[]> points = new ArrayList<>();
        boolean closed = false;
    }
}
