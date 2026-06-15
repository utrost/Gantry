package org.trostheide.svgtoolbox.core;

import org.apache.batik.parser.AWTPathProducer;
import org.apache.batik.parser.PathParser;
import org.w3c.dom.Element;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

public class ShapeParser {

    public static Shape parse(Element el) {
        String tag = el.getTagName();
        try {
            if ("rect".equals(tag)) {
                double x = Double.parseDouble(el.getAttribute("x"));
                double y = Double.parseDouble(el.getAttribute("y"));
                double w = Double.parseDouble(el.getAttribute("width"));
                double h = Double.parseDouble(el.getAttribute("height"));
                return new Rectangle2D.Double(x, y, w, h);
            } else if ("circle".equals(tag)) {
                double cx = Double.parseDouble(el.getAttribute("cx"));
                double cy = Double.parseDouble(el.getAttribute("cy"));
                double r = Double.parseDouble(el.getAttribute("r"));
                return new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
            } else if ("ellipse".equals(tag)) {
                double cx = Double.parseDouble(el.getAttribute("cx"));
                double cy = Double.parseDouble(el.getAttribute("cy"));
                double rx = Double.parseDouble(el.getAttribute("rx"));
                double ry = Double.parseDouble(el.getAttribute("ry"));
                return new Ellipse2D.Double(cx - rx, cy - ry, rx * 2, ry * 2);
            } else if ("polygon".equals(tag) || "polyline".equals(tag)) {
                String points = el.getAttribute("points");
                String[] pairs = points.trim().split("[\\s,]+");
                Path2D p = new Path2D.Double();
                if (pairs.length >= 2) {
                    p.moveTo(Double.parseDouble(pairs[0]), Double.parseDouble(pairs[1]));
                    for (int i = 2; i < pairs.length; i += 2) {
                        p.lineTo(Double.parseDouble(pairs[i]), Double.parseDouble(pairs[i + 1]));
                    }
                    if ("polygon".equals(tag))
                        p.closePath();
                }
                return p;
            } else if ("path".equals(tag)) {
                String d = el.getAttribute("d");
                PathParser parser = new PathParser();
                AWTPathProducer producer = new AWTPathProducer();
                parser.setPathHandler(producer);
                parser.parse(d);
                return producer.getShape();
            } else if ("line".equals(tag)) {
                double x1 = Double.parseDouble(el.getAttribute("x1"));
                double y1 = Double.parseDouble(el.getAttribute("y1"));
                double x2 = Double.parseDouble(el.getAttribute("x2"));
                double y2 = Double.parseDouble(el.getAttribute("y2"));
                return new java.awt.geom.Line2D.Double(x1, y1, x2, y2);
            }
        } catch (Exception e) {
            // Swallow, return null
        }
        return null;
    }
}
