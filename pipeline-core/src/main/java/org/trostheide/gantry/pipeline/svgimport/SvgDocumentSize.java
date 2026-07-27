package org.trostheide.gantry.pipeline.svgimport;

import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads an SVG root element's declared physical width and height. */
public final class SvgDocumentSize {
    private static final Pattern LENGTH = Pattern.compile(
            "^\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*(mm|cm|in|pt|pc|px)?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private SvgDocumentSize() {
    }

    /**
     * Returns the declared SVG document size in millimetres. Unitless values and px use the
     * SVG/CSS reference resolution of 96 dpi; percentages and missing dimensions are rejected.
     */
    public static Optional<PaperFormat> read(File svg) {
        if (svg == null || !svg.isFile()) {
            return Optional.empty();
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Element root = factory.newDocumentBuilder().parse(svg).getDocumentElement();
            Double width = toMillimetres(root.getAttribute("width"));
            Double height = toMillimetres(root.getAttribute("height"));
            if (width == null || height == null || width <= 0 || height <= 0) {
                return Optional.empty();
            }
            return Optional.of(new PaperFormat(width, height));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    static Double toMillimetres(String value) {
        if (value == null) return null;
        Matcher matcher = LENGTH.matcher(value);
        if (!matcher.matches()) return null;
        double amount;
        try {
            amount = Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
        String unit = matcher.group(2);
        unit = unit == null ? "px" : unit.toLowerCase(Locale.ROOT);
        return switch (unit) {
            case "mm" -> amount;
            case "cm" -> amount * 10.0;
            case "in" -> amount * 25.4;
            case "pt" -> amount * 25.4 / 72.0;
            case "pc" -> amount * 25.4 / 6.0;
            case "px" -> amount * 25.4 / 96.0;
            default -> null;
        };
    }
}
