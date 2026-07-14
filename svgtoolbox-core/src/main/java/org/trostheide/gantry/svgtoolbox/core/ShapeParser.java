package org.trostheide.gantry.svgtoolbox.core;

import org.apache.batik.parser.AWTPathProducer;
import org.apache.batik.parser.PathParser;
import org.w3c.dom.Element;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

public class ShapeParser {

    public static Shape parse(Element el) {
        String tag = el.getLocalName() == null ? el.getTagName() : el.getLocalName();
        try {
            if ("rect".equals(tag)) {
                double x = number(el, "x", 0);
                double y = number(el, "y", 0);
                double w = number(el, "width", 0);
                double h = number(el, "height", 0);
                double rx = number(el, "rx", 0);
                double ry = number(el, "ry", 0);
                if (rx > 0 || ry > 0) {
                    if (rx <= 0) rx = ry;
                    if (ry <= 0) ry = rx;
                    rx = Math.min(rx, w / 2);
                    ry = Math.min(ry, h / 2);
                    return new RoundRectangle2D.Double(x, y, w, h, rx * 2, ry * 2);
                }
                return new Rectangle2D.Double(x, y, w, h);
            } else if ("circle".equals(tag)) {
                double cx = number(el, "cx", 0);
                double cy = number(el, "cy", 0);
                double r = number(el, "r", 0);
                return new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
            } else if ("ellipse".equals(tag)) {
                double cx = number(el, "cx", 0);
                double cy = number(el, "cy", 0);
                double rx = number(el, "rx", 0);
                double ry = number(el, "ry", 0);
                return new Ellipse2D.Double(cx - rx, cy - ry, rx * 2, ry * 2);
            } else if ("polygon".equals(tag) || "polyline".equals(tag)) {
                String points = el.getAttribute("points");
                String[] pairs = points.trim().split("[\\s,]+");
                Path2D p = new Path2D.Double();
                if (pairs.length >= 2 && pairs.length % 2 == 0) {
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
                double x1 = number(el, "x1", 0);
                double y1 = number(el, "y1", 0);
                double x2 = number(el, "x2", 0);
                double y2 = number(el, "y2", 0);
                return new java.awt.geom.Line2D.Double(x1, y1, x2, y2);
            }
        } catch (Exception e) {
            // Swallow, return null
        }
        return null;
    }

    private static double number(Element el, String attribute, double defaultValue) {
        String value = el.getAttribute(attribute);
        return value == null || value.isBlank() ? defaultValue : Double.parseDouble(value);
    }
}
