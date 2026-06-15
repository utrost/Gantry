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
 * Reorders paths for minimum pen-travel using Greedy Nearest-Neighbor,
 * considering both start AND end points of each path.
 * Optional 2-opt improvement pass. Per-layer (per &lt;g&gt; group) optimization.
 */
public class LinesortProcessor implements Processor {

    @Override
    public void process(Document doc, Config config) {
        if (!config.linesort()) return;

        boolean twoOpt = config.linesortTwoOpt();

        NodeList groups = doc.getElementsByTagName("g");
        if (groups.getLength() > 0) {
            for (int i = 0; i < groups.getLength(); i++) {
                sortGroup((Element) groups.item(i), twoOpt);
            }
        } else {
            sortGroup(doc.getDocumentElement(), twoOpt);
        }
        System.out.println("Linesort: path order optimized");
    }

    private void sortGroup(Element group, boolean twoOpt) {
        List<ShapeEntry> entries = new ArrayList<>();
        NodeList kids = group.getChildNodes();

        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n instanceof Element) {
                String tag = ((Element) n).getTagName();
                if ("path".equals(tag) || "line".equals(tag) || "polyline".equals(tag) ||
                        "polygon".equals(tag) || "rect".equals(tag) || "circle".equals(tag) || "ellipse".equals(tag)) {
                    ShapeEntry entry = new ShapeEntry();
                    entry.element = (Element) n;
                    entry.start = getStartPoint(entry.element);
                    entry.end = getEndPoint(entry.element);
                    entry.reversed = false;
                    entries.add(entry);
                }
            }
        }

        if (entries.size() < 2) return;

        // Greedy nearest-neighbor considering both start and end points
        List<ShapeEntry> sorted = new ArrayList<>();
        List<ShapeEntry> remaining = new ArrayList<>(entries);

        Point2D.Double currentPos = new Point2D.Double(0, 0);

        // Start with the entry whose start or end is closest to origin
        int bestIdx = 0;
        double bestDist = Double.MAX_VALUE;
        boolean bestReversed = false;
        for (int i = 0; i < remaining.size(); i++) {
            ShapeEntry e = remaining.get(i);
            double ds = currentPos.distanceSq(e.start);
            double de = currentPos.distanceSq(e.end);
            if (ds < bestDist) {
                bestDist = ds;
                bestIdx = i;
                bestReversed = false;
            }
            if (de < bestDist) {
                bestDist = de;
                bestIdx = i;
                bestReversed = true;
            }
        }
        ShapeEntry first = remaining.remove(bestIdx);
        first.reversed = bestReversed;
        sorted.add(first);
        currentPos = first.reversed ? first.start : first.end;

        while (!remaining.isEmpty()) {
            bestIdx = 0;
            bestDist = Double.MAX_VALUE;
            bestReversed = false;
            for (int i = 0; i < remaining.size(); i++) {
                ShapeEntry e = remaining.get(i);
                double ds = currentPos.distanceSq(e.start);
                double de = currentPos.distanceSq(e.end);
                if (ds < bestDist) {
                    bestDist = ds;
                    bestIdx = i;
                    bestReversed = false;
                }
                if (de < bestDist) {
                    bestDist = de;
                    bestIdx = i;
                    bestReversed = true;
                }
            }
            ShapeEntry next = remaining.remove(bestIdx);
            next.reversed = bestReversed;
            sorted.add(next);
            currentPos = next.reversed ? next.start : next.end;
        }

        // Optional 2-opt improvement
        if (twoOpt) {
            twoOptImprove(sorted);
        }

        // Reorder in DOM
        for (ShapeEntry entry : sorted) {
            group.appendChild(entry.element); // moves to end
        }
    }

    private void twoOptImprove(List<ShapeEntry> route) {
        if (route.size() < 4) return;

        boolean improved = true;
        int maxIterations = 5;
        while (improved && maxIterations-- > 0) {
            improved = false;
            for (int i = 0; i < route.size() - 2; i++) {
                for (int j = i + 2; j < route.size(); j++) {
                    double currentDist = travelCost(route, i, j);
                    double swappedDist = swappedTravelCost(route, i, j);
                    if (swappedDist < currentDist - 0.001) {
                        reverseSubList(route, i + 1, j);
                        // Flip reversed flags in the reversed segment
                        for (int k = i + 1; k <= j; k++) {
                            route.get(k).reversed = !route.get(k).reversed;
                        }
                        improved = true;
                    }
                }
            }
        }
    }

    private double travelCost(List<ShapeEntry> route, int i, int j) {
        Point2D.Double endI = effectiveEnd(route.get(i));
        Point2D.Double startI1 = effectiveStart(route.get(i + 1));
        double cost = endI.distanceSq(startI1);

        if (j + 1 < route.size()) {
            Point2D.Double endJ = effectiveEnd(route.get(j));
            Point2D.Double startJ1 = effectiveStart(route.get(j + 1));
            cost += endJ.distanceSq(startJ1);
        }
        return cost;
    }

    private double swappedTravelCost(List<ShapeEntry> route, int i, int j) {
        // After reversing [i+1..j], entry at i+1 becomes route[j] (reversed)
        // and entry at j becomes route[i+1] (reversed)
        Point2D.Double endI = effectiveEnd(route.get(i));
        // route[j] reversed: its effective start becomes its old effective end
        ShapeEntry entryJ = route.get(j);
        Point2D.Double newStartI1 = entryJ.reversed ? entryJ.start : entryJ.end;
        double cost = endI.distanceSq(newStartI1);

        if (j + 1 < route.size()) {
            ShapeEntry entryI1 = route.get(i + 1);
            Point2D.Double newEndJ = entryI1.reversed ? entryI1.end : entryI1.start;
            Point2D.Double startJ1 = effectiveStart(route.get(j + 1));
            cost += newEndJ.distanceSq(startJ1);
        }
        return cost;
    }

    private Point2D.Double effectiveStart(ShapeEntry e) {
        return e.reversed ? e.end : e.start;
    }

    private Point2D.Double effectiveEnd(ShapeEntry e) {
        return e.reversed ? e.start : e.end;
    }

    private void reverseSubList(List<ShapeEntry> list, int from, int to) {
        while (from < to) {
            ShapeEntry temp = list.get(from);
            list.set(from, list.get(to));
            list.set(to, temp);
            from++;
            to--;
        }
    }

    private Point2D.Double getStartPoint(Element e) {
        return getPoint(e, true);
    }

    private Point2D.Double getEndPoint(Element e) {
        return getPoint(e, false);
    }

    private Point2D.Double getPoint(Element e, boolean start) {
        String tag = e.getTagName();
        try {
            if ("line".equals(tag)) {
                double x = Double.parseDouble(e.getAttribute(start ? "x1" : "x2"));
                double y = Double.parseDouble(e.getAttribute(start ? "y1" : "y2"));
                return new Point2D.Double(x, y);
            } else if ("path".equals(tag)) {
                String d = e.getAttribute("d");
                if (d == null || d.trim().isEmpty()) return new Point2D.Double(0, 0);

                AWTPathProducer producer = new AWTPathProducer();
                producer.setWindingRule(java.awt.geom.Path2D.WIND_EVEN_ODD);
                PathParser parser = new PathParser();
                parser.setPathHandler(producer);
                parser.parse(d);

                Shape shape = producer.getShape();
                PathIterator pi = shape.getPathIterator(null);
                double[] coords = new double[6];
                Point2D.Double firstPoint = null;
                Point2D.Double lastPoint = null;

                while (!pi.isDone()) {
                    int type = pi.currentSegment(coords);
                    switch (type) {
                        case PathIterator.SEG_MOVETO:
                            Point2D.Double pMove = new Point2D.Double(coords[0], coords[1]);
                            if (firstPoint == null) firstPoint = pMove;
                            lastPoint = pMove;
                            break;
                        case PathIterator.SEG_LINETO:
                            lastPoint = new Point2D.Double(coords[0], coords[1]);
                            break;
                        case PathIterator.SEG_QUADTO:
                            lastPoint = new Point2D.Double(coords[2], coords[3]);
                            break;
                        case PathIterator.SEG_CUBICTO:
                            lastPoint = new Point2D.Double(coords[4], coords[5]);
                            break;
                    }
                    if (start && firstPoint != null) return firstPoint;
                    pi.next();
                }
                if (start && firstPoint != null) return firstPoint;
                if (!start && lastPoint != null) return lastPoint;

            } else if ("rect".equals(tag)) {
                double x = Double.parseDouble(e.getAttribute("x"));
                double y = Double.parseDouble(e.getAttribute("y"));
                return new Point2D.Double(x, y);
            } else if ("circle".equals(tag) || "ellipse".equals(tag)) {
                double cx = Double.parseDouble(e.getAttribute("cx"));
                double cy = Double.parseDouble(e.getAttribute("cy"));
                double rx = "circle".equals(tag) ?
                        Double.parseDouble(e.getAttribute("r")) :
                        Double.parseDouble(e.getAttribute("rx"));
                return new Point2D.Double(cx + rx, cy);
            }
        } catch (Exception ex) {
            // Fall through to default
        }
        return new Point2D.Double(0, 0);
    }

    static class ShapeEntry {
        Element element;
        Point2D.Double start;
        Point2D.Double end;
        boolean reversed;
    }
}
