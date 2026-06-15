package org.trostheide.svgtoolbox.core;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.*;

public class SvgAnalyzer {

    /**
     * Represents a detected layer in the SVG.
     */
    public static class LayerInfo {
        public final String id;
        public final String label;
        public final String primaryColor;
        public final Set<String> colors;

        public LayerInfo(String id, String label, Set<String> colors) {
            this.id = id;
            this.label = label;
            this.colors = colors;
            this.primaryColor = colors.isEmpty() ? "#000000" : colors.iterator().next();
        }
    }

    private static final String INKSCAPE_NS = "http://www.inkscape.org/namespaces/inkscape";

    /**
     * Extract layers from an SVG file.
     * 
     * Strategy:
     * 1. Look for top-level <g> elements with inkscape:label or id — treat each as a layer.
     * 2. If no structured layers found, fall back to color-based grouping.
     */
    public static List<LayerInfo> extractLayers(File svgFile) throws Exception {
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
        Document doc = factory.createDocument(svgFile.toURI().toString());

        List<LayerInfo> layers = extractStructuredLayers(doc.getDocumentElement());

        if (!layers.isEmpty()) {
            return layers;
        }

        // Fallback: group by color (legacy behavior for SVGs without layer structure)
        return extractColorLayers(doc.getDocumentElement());
    }

    /**
     * Legacy method for backward compatibility.
     * Returns Map<colorHex, layerName>.
     */
    public static Map<String, String> extractLayerNames(File svgFile) throws Exception {
        List<LayerInfo> layers = extractLayers(svgFile);
        Map<String, String> result = new LinkedHashMap<>();
        for (LayerInfo layer : layers) {
            result.put(layer.primaryColor, layer.label);
        }
        return result;
    }

    /**
     * Find top-level <g> elements that represent layers (Inkscape or plain id).
     */
    private static List<LayerInfo> extractStructuredLayers(Element root) {
        List<LayerInfo> layers = new ArrayList<>();
        NodeList children = root.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element)) continue;
            Element el = (Element) node;
            if (!"g".equals(el.getTagName())) continue;

            String label = null;
            String id = el.getAttribute("id");

            // Check for Inkscape layer
            if ("layer".equals(el.getAttributeNS(INKSCAPE_NS, "groupmode"))) {
                label = el.getAttributeNS(INKSCAPE_NS, "label");
            }

            // Fall back to id attribute
            if ((label == null || label.isEmpty()) && id != null && !id.isEmpty()) {
                label = id;
            }

            if (label == null || label.isEmpty()) continue;

            // Collect all colors used within this layer
            Set<String> colors = new LinkedHashSet<>();
            collectColors(el, colors);

            if (!colors.isEmpty()) {
                layers.add(new LayerInfo(
                    id != null ? id : label,
                    label,
                    colors
                ));
            }
        }

        return layers;
    }

    /**
     * Fallback: group shapes by color when no layer structure exists.
     */
    private static List<LayerInfo> extractColorLayers(Element root) {
        Map<String, Set<String>> colorMap = new LinkedHashMap<>();
        collectColorsFlat(root, colorMap);

        List<LayerInfo> layers = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : colorMap.entrySet()) {
            String color = entry.getKey();
            Set<String> colors = new LinkedHashSet<>();
            colors.add(color);
            layers.add(new LayerInfo("color_" + color.replace("#", ""), "Color " + color, colors));
        }
        return layers;
    }

    private static void collectColors(Element parent, Set<String> colors) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element)) continue;
            Element el = (Element) node;

            if (isShape(el.getTagName())) {
                String color = getStrokeColor(el);
                if (color != null && !"none".equalsIgnoreCase(color) && !"unknown".equals(color)) {
                    colors.add(color.toLowerCase());
                }
                // Also check fill for filled shapes (water, parks)
                if (el.hasAttribute("fill") && !"none".equalsIgnoreCase(el.getAttribute("fill"))) {
                    colors.add(el.getAttribute("fill").toLowerCase());
                }
            }
            collectColors(el, colors);
        }
    }

    private static void collectColorsFlat(Element parent, Map<String, Set<String>> colorMap) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element)) continue;
            Element el = (Element) node;

            if (isShape(el.getTagName())) {
                String color = getStrokeColor(el);
                if (color != null && !"none".equalsIgnoreCase(color) && !"unknown".equals(color)) {
                    colorMap.computeIfAbsent(color.toLowerCase(), k -> new LinkedHashSet<>());
                }
            }
            collectColorsFlat(el, colorMap);
        }
    }

    private static String getStrokeColor(Element el) {
        if (el.hasAttribute("stroke") && !"none".equalsIgnoreCase(el.getAttribute("stroke"))) {
            return el.getAttribute("stroke");
        }
        if (isShape(el.getTagName()) && el.hasAttribute("fill") && !"none".equalsIgnoreCase(el.getAttribute("fill"))) {
            return el.getAttribute("fill");
        }
        return "unknown";
    }

    private static boolean isShape(String tag) {
        return tag.matches("rect|circle|ellipse|line|polyline|polygon|path");
    }
}
