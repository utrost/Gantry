package org.trostheide.svgtoolbox.processors;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.Processor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class PathOptimizeProcessor implements Processor {

    @Override
    public void process(Document doc, Config config) {
        System.out.println("Optimizing path order...");
        // Traverse looking for groups to optimize
        // For simplicity, we can just process the root or layer groups if we assume
        // LayerProcessor ran first?
        // But SvgToolboxRunner adds this BEFORE LayerProcessor if I recall correctly?
        // No, I added it AFTER LayerProcessor in my plan implementation.
        // Wait, SvgToolboxRunner.java:
        // pipeline.add(new LayerProcessor());
        // pipeline.add(new CropProcessor());
        // if (config.optimizePaths()) pipeline.add(new PathOptimizeProcessor());

        // So optimizing after layers. This is good. We should optimize each Layer
        // group.

        NodeList groups = doc.getElementsByTagName("g");
        for (int i = 0; i < groups.getLength(); i++) {
            optimizeGroup((Element) groups.item(i));
        }

        // Also optimize root if it has direct children?
        // LayerProcessor puts everything in groups, so this should suffice.
    }

    private void optimizeGroup(Element group) {
        List<Element> shapes = new ArrayList<>();
        NodeList kids = group.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n instanceof Element) {
                String tag = ((Element) n).getTagName();
                if (tag.equals("path") || tag.equals("line") || tag.equals("polyline") ||
                        tag.equals("polygon") || tag.equals("rect") || tag.equals("circle") || tag.equals("ellipse")) {
                    shapes.add((Element) n);
                }
            }
        }

        if (shapes.size() < 2)
            return;

        // Nearest Neighbor Greedy Sort
        List<Element> sorted = new ArrayList<>();
        List<Element> remaining = new ArrayList<>(shapes);

        Point2D.Double currentPos = new Point2D.Double(0, 0);

        // Heuristic: Start with the shape closest to 0,0? Or just the first one?
        // Let's find start closest to 0,0
        Element first = findClosest(currentPos, remaining);
        sorted.add(first);
        remaining.remove(first);
        currentPos = getEndPoint(first);

        while (!remaining.isEmpty()) {
            Element next = findClosest(currentPos, remaining);
            sorted.add(next);
            remaining.remove(next);
            currentPos = getEndPoint(next);
        }

        // 2-opt improvement: iteratively uncross path segments
        sorted = twoOptImprove(sorted);

        // Reorder in DOM
        for (Element s : sorted) {
            group.appendChild(s); // Moves it to the end
        }
    }

    private List<Element> twoOptImprove(List<Element> route) {
        if (route.size() < 4) return route;

        boolean improved = true;
        int maxIterations = 5; // cap iterations for large inputs
        while (improved && maxIterations-- > 0) {
            improved = false;
            for (int i = 0; i < route.size() - 2; i++) {
                for (int j = i + 2; j < route.size(); j++) {
                    double currentDist = segmentDistance(route, i, j);
                    double swappedDist = swappedSegmentDistance(route, i, j);
                    if (swappedDist < currentDist - 0.001) {
                        // Reverse the segment between i+1 and j
                        reverseSubList(route, i + 1, j);
                        improved = true;
                    }
                }
            }
        }
        return route;
    }

    private double segmentDistance(List<Element> route, int i, int j) {
        Point2D.Double endI = getEndPoint(route.get(i));
        Point2D.Double startI1 = getStartPoint(route.get(i + 1));
        double d1 = endI.distanceSq(startI1);

        if (j + 1 < route.size()) {
            Point2D.Double endJ = getEndPoint(route.get(j));
            Point2D.Double startJ1 = getStartPoint(route.get(j + 1));
            return d1 + endJ.distanceSq(startJ1);
        }
        return d1;
    }

    private double swappedSegmentDistance(List<Element> route, int i, int j) {
        Point2D.Double endI = getEndPoint(route.get(i));
        Point2D.Double startJ = getStartPoint(route.get(j)); // after reverse, j becomes i+1's neighbor
        double d1 = endI.distanceSq(startJ);

        if (j + 1 < route.size()) {
            Point2D.Double endI1 = getEndPoint(route.get(i + 1)); // after reverse, i+1 becomes j's neighbor
            Point2D.Double startJ1 = getStartPoint(route.get(j + 1));
            return d1 + endI1.distanceSq(startJ1);
        }
        return d1;
    }

    private void reverseSubList(List<Element> list, int from, int to) {
        while (from < to) {
            Element temp = list.get(from);
            list.set(from, list.get(to));
            list.set(to, temp);
            from++;
            to--;
        }
    }

    private Element findClosest(Point2D.Double p, List<Element> candidates) {
        Element best = null;
        double minDistSq = Double.MAX_VALUE;

        for (Element e : candidates) {
            Point2D.Double start = getStartPoint(e);
            double d = p.distanceSq(start);
            if (d < minDistSq) {
                minDistSq = d;
                best = e;
            }
        }
        return best;
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
            if (tag.equals("line")) {
                double x = Double.parseDouble(e.getAttribute(start ? "x1" : "x2"));
                double y = Double.parseDouble(e.getAttribute(start ? "y1" : "y2"));
                return new Point2D.Double(x, y);
            } else if (tag.equals("path")) {
                String d = e.getAttribute("d");
                if (d == null || d.trim().isEmpty()) {
                    return new Point2D.Double(0, 0);
                }

                org.apache.batik.parser.AWTPathProducer producer = new org.apache.batik.parser.AWTPathProducer();
                producer.setWindingRule(java.awt.geom.Path2D.WIND_EVEN_ODD);
                org.apache.batik.parser.PathParser parser = new org.apache.batik.parser.PathParser();
                parser.setPathHandler(producer);
                parser.parse(d);

                java.awt.Shape shape = producer.getShape();
                java.awt.geom.PathIterator pi = shape.getPathIterator(null);
                
                double[] coords = new double[6];
                Point2D.Double firstPoint = null;
                Point2D.Double lastPoint = null;
                Point2D.Double currentSubpathStart = null;

                while (!pi.isDone()) {
                    int type = pi.currentSegment(coords);
                    switch (type) {
                        case java.awt.geom.PathIterator.SEG_MOVETO:
                            Point2D.Double pMove = new Point2D.Double(coords[0], coords[1]);
                            if (firstPoint == null) {
                                firstPoint = pMove;
                            }
                            lastPoint = pMove;
                            currentSubpathStart = pMove;
                            break;
                        case java.awt.geom.PathIterator.SEG_LINETO:
                            lastPoint = new Point2D.Double(coords[0], coords[1]);
                            break;
                        case java.awt.geom.PathIterator.SEG_QUADTO:
                            lastPoint = new Point2D.Double(coords[2], coords[3]);
                            break;
                        case java.awt.geom.PathIterator.SEG_CUBICTO:
                            lastPoint = new Point2D.Double(coords[4], coords[5]);
                            break;
                        case java.awt.geom.PathIterator.SEG_CLOSE:
                            if (currentSubpathStart != null) {
                                lastPoint = currentSubpathStart;
                            }
                            break;
                    }
                    if (start && firstPoint != null) {
                        return firstPoint;
                    }
                    pi.next();
                }
                
                if (start && firstPoint != null) {
                    return firstPoint;
                }
                if (!start && lastPoint != null) {
                    return lastPoint;
                }

            } else if (tag.equals("rect")) {
                double x = Double.parseDouble(e.getAttribute("x"));
                double y = Double.parseDouble(e.getAttribute("y"));
                return new Point2D.Double(x, y);
            } else if (tag.equals("circle") || tag.equals("ellipse")) {
                 double cx = Double.parseDouble(e.getAttribute("cx"));
                 double cy = Double.parseDouble(e.getAttribute("cy"));
                 double rx = tag.equals("circle") ? Double.parseDouble(e.getAttribute("r")) : Double.parseDouble(e.getAttribute("rx"));
                 // Approximating start/end of a circle/ellipse as its right-most point (0 degrees)
                 return new Point2D.Double(cx + rx, cy);
            }
        } catch (Exception ex) {
            System.err.println("Warning: Could not parse geometry for " + tag + ": " + ex.getMessage());
        }
        return new Point2D.Double(0, 0);
    }
}
