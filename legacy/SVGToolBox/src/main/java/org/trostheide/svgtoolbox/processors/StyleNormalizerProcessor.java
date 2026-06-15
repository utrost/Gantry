package org.trostheide.svgtoolbox.processors;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.Processor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class StyleNormalizerProcessor implements Processor {

    // Attributes we care about for processing
    private static final Set<String> TARGET_PROPERTIES = new HashSet<>(Arrays.asList(
            "fill", "stroke", "stroke-width", "opacity", "fill-opacity", "stroke-opacity"));

    @Override
    public void process(Document doc, Config config) {
        NodeList elements = doc.getElementsByTagName("*");
        int count = 0;

        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);
            if (el.hasAttribute("style")) {
                if (normalizeStyle(el)) {
                    count++;
                }
            }
        }
        System.out.println("Normalized styles for " + count + " elements.");
    }

    private boolean normalizeStyle(Element el) {
        String style = el.getAttribute("style");
        if (style == null || style.isEmpty())
            return false;

        boolean modified = false;
        String[] entries = style.split(";");

        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                String key = parts[0].trim().toLowerCase();
                String value = parts[1].trim();

                if (TARGET_PROPERTIES.contains(key)) {
                    // Only set if not already present as a direct attribute
                    // Or overwrite? Usually style takes precedence, so we should overwrite
                    // attribute.
                    el.setAttribute(key, value);
                    modified = true;
                }
            }
        }
        return modified;
    }
}
