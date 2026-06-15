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

public class SimplifyProcessor implements Processor {

    @Override
    public void process(Document doc, Config config) {
        if (config.simplifyTolerance() <= 0)
            return;

        NodeList elements = doc.getElementsByTagName("*");
        int count = 0;

        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);
            if (simplifyElement(el, config.simplifyTolerance())) {
                count++;
            }
        }
        System.out.println("Simplified " + count + " paths (Tolerance: " + config.simplifyTolerance() + ")");
    }

    private boolean simplifyElement(Element el, double tolerance) {
        String tag = el.getTagName();
        List<Point> points;

        // 1. Extract Points
        if ("polygon".equals(tag) || "polyline".equals(tag)) {
            points = parsePolyPoints(el.getAttribute("points"));
        } else if ("path".equals(tag)) {
            points = parsePathPoints(el.getAttribute("d"));
        } else {
            return false; // Can't simplify rects/circles without converting them first
        }

        if (points.size() < 3)
            return false;

        // 2. Apply Ramer-Douglas-Peucker
        List<Point> simplified = ramerDouglasPeucker(points, tolerance);

        // 3. Write Back
        if ("polygon".equals(tag) || "polyline".equals(tag)) {
            el.setAttribute("points", toPolyString(simplified));
        } else if ("path".equals(tag)) {
            el.setAttribute("d", toPathString(simplified));
        }
        return true;
    }

    // --- Ramer-Douglas-Peucker Algorithm ---
    private List<Point> ramerDouglasPeucker(List<Point> points, double epsilon) {
        if (points.size() < 3)
            return points;

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

        List<Point> result = new ArrayList<>();
        if (dmax > epsilon) {
            List<Point> recResults1 = ramerDouglasPeucker(points.subList(0, index + 1), epsilon);
            List<Point> recResults2 = ramerDouglasPeucker(points.subList(index, end + 1), epsilon);

            result.addAll(recResults1.subList(0, recResults1.size() - 1));
            result.addAll(recResults2);
        } else {
            result.add(points.get(0));
            result.add(points.get(end));
        }
        return result;
    }

    private double perpendicularDistance(Point p, Point lineStart, Point lineEnd) {
        double area = Math.abs(0.5 * (lineStart.x * lineEnd.y + lineEnd.x * p.y + p.x * lineStart.y
                - lineEnd.x * lineStart.y - p.x * lineEnd.y - lineStart.x * p.y));
        double bottom = Math.sqrt(Math.pow(lineStart.x - lineEnd.x, 2) + Math.pow(lineStart.y - lineEnd.y, 2));
        return (area * 2.0) / bottom;
    }

    // --- Helpers ---

    private List<Point> parsePolyPoints(String str) {
        List<Point> list = new ArrayList<>();
        String[] raw = str.trim().split("[\\s,]+");
        for (int i = 0; i < raw.length; i += 2) {
            if (i + 1 < raw.length) {
                list.add(new Point(Double.parseDouble(raw[i]), Double.parseDouble(raw[i + 1])));
            }
        }
        return list;
    }

    private List<Point> parsePathPoints(String d) {
        // Naive: Convert path to flattened iterator points
        // This is lossy for curves (turns them into lines), which is effectively what
        // "Simplify" does anyway
        List<Point> list = new ArrayList<>();
        PathParser parser = new PathParser();
        AWTPathProducer producer = new AWTPathProducer();
        parser.setPathHandler(producer);
        try {
            parser.parse(d);
            Shape s = producer.getShape();
            PathIterator pi = s.getPathIterator(null, 1.0); // Flatten curves
            double[] coords = new double[6];
            while (!pi.isDone()) {
                int type = pi.currentSegment(coords);
                if (type != PathIterator.SEG_CLOSE) {
                    list.add(new Point(coords[0], coords[1]));
                }
                pi.next();
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
        return list;
    }

    private String toPolyString(List<Point> points) {
        StringBuilder sb = new StringBuilder();
        for (Point p : points) {
            sb.append(String.format("%.2f,%.2f ", p.x, p.y));
        }
        return sb.toString().trim();
    }

    private String toPathString(List<Point> points) {
        if (points.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("M %.2f %.2f ", points.get(0).x, points.get(0).y));
        for (int i = 1; i < points.size(); i++) {
            sb.append(String.format("L %.2f %.2f ", points.get(i).x, points.get(i).y));
        }
        sb.append("Z");
        return sb.toString();
    }

    record Point(double x, double y) {
    }
}