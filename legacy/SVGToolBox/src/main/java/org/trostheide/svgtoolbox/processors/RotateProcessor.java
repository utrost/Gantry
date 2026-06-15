package org.trostheide.svgtoolbox.processors;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.Processor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RotateProcessor implements Processor {
    private static final String SVG_NS = "http://www.w3.org/2000/svg";

    @Override
    public void process(Document doc, Config config) {
        if (Math.abs(config.rotationDegrees()) < 0.001)
            return;

        double angle = config.rotationDegrees();
        // Normalize angle to 90, 180, 270...
        // We only support strict 90 degree increments for "canvas" rotation
        // because we swap width/height. Arbitrary rotation would require calculating
        // bounding box.
        // Let's assume we implement generic rotation by wrapping everything in a Group.

        Element root = doc.getDocumentElement();
        double w = getDimension(root, "width");
        double h = getDimension(root, "height");

        // Move all children into a new group
        Element wrapper = doc.createElementNS(SVG_NS, "g");
        NodeList children = root.getChildNodes();
        List<Node> toMove = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            toMove.add(children.item(i));
        }
        for (Node n : toMove) {
            wrapper.appendChild(n);
        }

        // Apply transform
        // Rotate around center: rotate(deg, cx, cy)
        double cx = w / 2.0;
        double cy = h / 2.0;

        // However, user typically expects "Rotate Page", meaning width and height swap
        // if 90/270.
        boolean swapDims = Math.abs(angle % 180) > 45; // roughly 90 or 270

        if (swapDims) {
            // Update root dimensions
            root.setAttribute("width", String.format(Locale.US, "%.2f", h));
            root.setAttribute("height", String.format(Locale.US, "%.2f", w));
            root.setAttribute("viewBox", String.format(Locale.US, "0 0 %.2f %.2f", h, w));

            // We need to translate to fit the new bounds.
            // Rot 90 around (0,0) moves (w,0) to (0,w) -> y becomes positive x.
            // Simple rotation:
            // transform="translate(newW, 0) rotate(90)" ?
        }

        // Let's just create a center-based rotation and let LayerProcessor fit/resize
        // later?
        // LayerProcessor resizes the canvas to fit content.
        // So we just need to rotate the content.

        wrapper.setAttribute("transform", String.format(Locale.US, "rotate(%.2f, %.2f, %.2f)", angle, cx, cy));

        root.appendChild(wrapper);
        System.out.println("Applied rotation: " + angle + " degrees.");
    }

    private double getDimension(Element root, String attr) {
        String val = root.getAttribute(attr);
        if (val.isEmpty())
            return 100.0; // fallback
        try {
            return Double.parseDouble(val.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return 100.0;
        }
    }
}
