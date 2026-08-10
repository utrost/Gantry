package org.trostheide.gantry.pipeline.svgimport;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Detects SVG text whose requested font-family fallback list cannot be resolved locally. */
public final class SvgFontPreflight {
    private static final Map<String, String> LOGICAL_FAMILIES = Map.ofEntries(
            Map.entry("dialog", Font.DIALOG), Map.entry("dialoginput", Font.DIALOG_INPUT),
            Map.entry("serif", Font.SERIF), Map.entry("sansserif", Font.SANS_SERIF),
            Map.entry("sans-serif", Font.SANS_SERIF), Map.entry("monospaced", Font.MONOSPACED),
            Map.entry("monospace", Font.MONOSPACED), Map.entry("system-ui", Font.SANS_SERIF),
            Map.entry("cursive", Font.SANS_SERIF), Map.entry("fantasy", Font.SANS_SERIF));

    private SvgFontPreflight() { }

    /** One unresolved fallback list and the number of text elements that use it. */
    public record MissingFont(List<String> requestedFamilies, String substituteFamily, int textElements) {
        public MissingFont {
            requestedFamilies = List.copyOf(requestedFamilies);
        }
    }

    public record Result(List<MissingFont> missingFonts) {
        public Result { missingFonts = List.copyOf(missingFonts); }
        public boolean hasMissingFonts() { return !missingFonts.isEmpty(); }
    }

    public static Result inspect(File svg) throws IOException {
        return inspect(SvgImportStage.loadDocument(svg));
    }

    static Result inspect(Document document) {
        Map<List<String>, Integer> missing = new LinkedHashMap<>();
        NodeList texts = document.getElementsByTagNameNS("*", "text");
        if (texts.getLength() == 0) texts = document.getElementsByTagName("text");
        for (int i = 0; i < texts.getLength(); i++) {
            Element text = (Element) texts.item(i);
            FontResolution resolution = resolve(text);
            if (!resolution.matched()) {
                missing.merge(resolution.requestedFamilies(), 1, Integer::sum);
            }
        }
        List<MissingFont> issues = new ArrayList<>();
        for (Map.Entry<List<String>, Integer> issue : missing.entrySet()) {
            issues.add(new MissingFont(issue.getKey(), Font.SANS_SERIF, issue.getValue()));
        }
        return new Result(issues);
    }

    record FontResolution(List<String> requestedFamilies, String resolvedFamily, boolean matched) { }

    /** Resolves the same ordered SVG fallback list used by the glyph outliner. */
    static FontResolution resolve(Element text) {
        String declaration = inheritedFontFamily(text);
        if (declaration == null || declaration.isBlank()) {
            return new FontResolution(List.of(Font.SANS_SERIF), Font.SANS_SERIF, true);
        }
        List<String> requested = parseFamilies(declaration);
        if (requested.isEmpty()) requested = List.of(Font.SANS_SERIF);

        Map<String, String> installed = installedFamilies();
        for (String family : requested) {
            String logical = LOGICAL_FAMILIES.get(normalize(family));
            if (logical != null) return new FontResolution(requested, logical, true);
            String actual = installed.get(normalize(family));
            if (actual != null) return new FontResolution(requested, actual, true);
        }
        return new FontResolution(requested, Font.SANS_SERIF, false);
    }

    private static Map<String, String> installedFamilies() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames(Locale.ROOT)) {
            result.putIfAbsent(normalize(family), family);
        }
        return result;
    }

    private static String inheritedFontFamily(Element element) {
        for (Node node = element; node instanceof Element current; node = node.getParentNode()) {
            String direct = current.getAttribute("font-family");
            if (direct != null && !direct.isBlank()) return direct;
            String styled = styleValue(current.getAttribute("style"), "font-family");
            if (styled != null && !styled.isBlank()) return styled;
        }
        return null;
    }

    private static String styleValue(String style, String property) {
        if (style == null || style.isBlank()) return null;
        for (String declaration : style.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon > 0 && property.equalsIgnoreCase(declaration.substring(0, colon).trim())) {
                return declaration.substring(colon + 1).trim();
            }
        }
        return null;
    }

    static List<String> parseFamilies(String declaration) {
        Set<String> result = new LinkedHashSet<>();
        for (String token : declaration.split(",")) {
            String family = token.trim();
            if (family.length() >= 2 && ((family.startsWith("\"") && family.endsWith("\""))
                    || (family.startsWith("'") && family.endsWith("'")))) {
                family = family.substring(1, family.length() - 1).trim();
            }
            if (!family.isEmpty()) result.add(family);
        }
        return List.copyOf(result);
    }

    private static String normalize(String family) {
        return family.replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
