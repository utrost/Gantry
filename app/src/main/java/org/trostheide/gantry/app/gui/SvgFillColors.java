package org.trostheide.gantry.app.gui;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Reads resolved SVG fill colours for pre-populating the hatch override editor. */
final class SvgFillColors {
    private static final Map<String, String> NAMED = Map.ofEntries(
            Map.entry("black", "#000000"), Map.entry("white", "#ffffff"),
            Map.entry("red", "#ff0000"), Map.entry("green", "#008000"),
            Map.entry("blue", "#0000ff"), Map.entry("yellow", "#ffff00"),
            Map.entry("cyan", "#00ffff"), Map.entry("magenta", "#ff00ff"),
            Map.entry("gray", "#808080"), Map.entry("grey", "#808080"),
            Map.entry("orange", "#ffa500"), Map.entry("purple", "#800080"),
            Map.entry("brown", "#a52a2a"), Map.entry("pink", "#ffc0cb"));

    private SvgFillColors() { }

    static Set<String> read(File svg) {
        Set<String> colors = new LinkedHashSet<>();
        if (svg == null || !svg.isFile()) return colors;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            NodeList elements = factory.newDocumentBuilder().parse(svg).getElementsByTagName("*");
            for (int i = 0; i < elements.getLength(); i++) {
                String color = resolveFill((Element) elements.item(i));
                if (color != null) colors.add(color);
            }
        } catch (Exception ignored) {
            // Import reports malformed SVG input; colour discovery is only a UI convenience.
        }
        return colors;
    }

    private static String resolveFill(Element element) {
        for (Node node = element; node instanceof Element current; node = current.getParentNode()) {
            String styleFill = property(current.getAttribute("style"), "fill");
            if (styleFill != null) return normalize(styleFill);
            if (current.hasAttribute("fill")) return normalize(current.getAttribute("fill"));
        }
        return null;
    }

    private static String property(String style, String name) {
        if (style == null) return null;
        for (String declaration : style.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon > 0 && declaration.substring(0, colon).trim().equalsIgnoreCase(name)) {
                return declaration.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String color = value.trim().toLowerCase(Locale.ROOT);
        if (color.isEmpty() || color.equals("none") || color.equals("transparent")) return null;
        if (color.matches("#[0-9a-f]{6}")) return color;
        if (color.matches("#[0-9a-f]{3}")) {
            return "#" + color.charAt(1) + color.charAt(1)
                    + color.charAt(2) + color.charAt(2)
                    + color.charAt(3) + color.charAt(3);
        }
        if (color.startsWith("rgb(") && color.endsWith(")")) {
            String[] components = color.substring(4, color.length() - 1).split(",");
            if (components.length == 3) {
                try {
                    return String.format(Locale.ROOT, "#%02x%02x%02x",
                            byteValue(components[0]), byteValue(components[1]), byteValue(components[2]));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return NAMED.get(color);
    }

    private static int byteValue(String value) {
        String component = value.trim();
        int parsed = component.endsWith("%")
                ? (int) Math.round(Double.parseDouble(
                        component.substring(0, component.length() - 1)) * 2.55)
                : Integer.parseInt(component);
        return Math.max(0, Math.min(255, parsed));
    }
}
