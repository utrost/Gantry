package org.trostheide.svgtoolbox.processors;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.Processor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class LayerProcessor implements Processor {

    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final String INKSCAPE_NS = "http://www.inkscape.org/namespaces/inkscape";

    @Override
    public void process(Document doc, Config config) {
        Element root = doc.getDocumentElement();
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:inkscape", INKSCAPE_NS);

        // Map Key (Hex Color) -> Layer Group Element
        Map<String, Element> layers = new HashMap<>();

        // 1. Find all relevant shapes recursively
        List<Element> allShapes = new ArrayList<>();
        collectShapes(root, allShapes);

        // 2. Move shapes to layers
        int movedCount = 0;
        for (Element shape : allShapes) {
            if (shape.getParentNode() instanceof Element &&
                    "layer".equals(((Element) shape.getParentNode()).getAttributeNS(INKSCAPE_NS, "groupmode"))) {
                continue;
            }

            String color = getStrokeColor(shape);

            if (color == null || "none".equalsIgnoreCase(color) || color.isEmpty() || "unknown".equals(color)) {
                continue;
            }

            // IMPORTANT: Bake the color onto the shape itself before moving it.
            // This fixes "Paint undefined" errors if the color was inherited from a parent
            // group we are leaving.
            if (!shape.hasAttribute("stroke")) {
                shape.setAttribute("stroke", color);
            }
            // Also ensure stroke-width is preserved if inherited, though usually
            // HatchProcessor sets it on group
            // For now, assuming HatchProcessor set it on group, we might lose width too.
            // Ideally HatchProcessor sets it on the group, so we should grab that too.
            String width = getStrokeWidth(shape);
            if (width != null && !shape.hasAttribute("stroke-width")) {
                shape.setAttribute("stroke-width", width);
            }

            if (!layers.containsKey(color)) {
                Element layer = doc.createElementNS(SVG_NS, "g");
                layer.setAttributeNS(INKSCAPE_NS, "inkscape:groupmode", "layer");
                layer.setAttributeNS(INKSCAPE_NS, "inkscape:label", "Color " + color);
                layer.setAttribute("id", "layer_" + color.replace("#", ""));
                root.appendChild(layer);
                layers.put(color, layer);
            }

            layers.get(color).appendChild(shape);
            movedCount++;
        }

        // 3. Cleanup empty groups
        removeEmptyGroups(root);

        // 4. Auto-Fit ViewBox (Fixes "White Image" issues)
        updateViewBox(root, allShapes);

        System.out.println("Organized " + movedCount + " shapes into " + layers.size() + " flat layers.");
    }

    private void updateViewBox(Element root, List<Element> shapes) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        boolean found = false;

        for (Element shape : shapes) {
            try {
                if ("line".equals(shape.getTagName())) {
                    double x1 = Double.parseDouble(shape.getAttribute("x1"));
                    double y1 = Double.parseDouble(shape.getAttribute("y1"));
                    double x2 = Double.parseDouble(shape.getAttribute("x2"));
                    double y2 = Double.parseDouble(shape.getAttribute("y2"));

                    minX = Math.min(minX, Math.min(x1, x2));
                    minY = Math.min(minY, Math.min(y1, y2));
                    maxX = Math.max(maxX, Math.max(x1, x2));
                    maxY = Math.max(maxY, Math.max(y1, y2));
                    found = true;
                }
            } catch (Exception e) {
                /* ignore */ }
        }

        if (found && maxX > minX && maxY > minY) {
            double width = maxX - minX;
            double height = maxY - minY;
            double margin = Math.max(width, height) * 0.05;

            double finalX = minX - margin;
            double finalY = minY - margin;
            double finalW = width + (margin * 2);
            double finalH = height + (margin * 2);

            String viewBox = String.format(Locale.US, "%.2f %.2f %.2f %.2f", finalX, finalY, finalW, finalH);

            root.setAttribute("viewBox", viewBox);
            root.setAttribute("width", String.format(Locale.US, "%.2fpx", finalW));
            root.setAttribute("height", String.format(Locale.US, "%.2fpx", finalH));

            System.out.println("RESIZED CANVAS to fit content: " + viewBox);
        } else {
            System.out.println("WARNING: Could not calculate bounds. Content might be empty.");
        }
    }

    private void collectShapes(Element parent, List<Element> collector) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element) {
                Element el = (Element) node;
                if (isShape(el.getTagName())) {
                    collector.add(el);
                } else if ("g".equals(el.getTagName())) {
                    collectShapes(el, collector);
                }
            }
        }
    }

    private void removeEmptyGroups(Element parent) {
        NodeList children = parent.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node node = children.item(i);
            if (node instanceof Element) {
                Element el = (Element) node;
                if ("g".equals(el.getTagName())) {
                    removeEmptyGroups(el);
                    if (!hasChildElements(el)) {
                        if (!"layer".equals(el.getAttributeNS(INKSCAPE_NS, "groupmode"))) {
                            parent.removeChild(el);
                        }
                    }
                }
            }
        }
    }

    private boolean hasChildElements(Element el) {
        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element)
                return true;
        }
        return false;
    }

    private boolean isShape(String tag) {
        return tag.matches("rect|circle|ellipse|line|polyline|polygon|path");
    }

    private String getStrokeColor(Element el) {
        if (el.hasAttribute("stroke") && !"none".equalsIgnoreCase(el.getAttribute("stroke"))) {
            return el.getAttribute("stroke");
        }
        Node parent = el.getParentNode();
        while (parent instanceof Element) {
            Element pEl = (Element) parent;
            if (pEl.hasAttribute("stroke") && !"none".equalsIgnoreCase(pEl.getAttribute("stroke"))) {
                return pEl.getAttribute("stroke");
            }
            parent = pEl.getParentNode();
        }
        if (el.hasAttribute("fill") && !"none".equalsIgnoreCase(el.getAttribute("fill"))) {
            return el.getAttribute("fill");
        }
        return "unknown";
    }

    private String getStrokeWidth(Element el) {
        if (el.hasAttribute("stroke-width")) {
            return el.getAttribute("stroke-width");
        }
        Node parent = el.getParentNode();
        while (parent instanceof Element) {
            Element pEl = (Element) parent;
            if (pEl.hasAttribute("stroke-width")) {
                return pEl.getAttribute("stroke-width");
            }
            parent = pEl.getParentNode();
        }
        return null;
    }
}