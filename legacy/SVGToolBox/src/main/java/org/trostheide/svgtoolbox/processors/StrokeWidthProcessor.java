package org.trostheide.svgtoolbox.processors;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.Processor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class StrokeWidthProcessor implements Processor {

    @Override
    public void process(Document doc, Config config) {
        boolean hasGlobal = config.strokeWidth() > 0;
        boolean hasOverrides = config.strokeWidthOverrides() != null && !config.strokeWidthOverrides().isEmpty();
        
        if (!hasGlobal && !hasOverrides) return;

        String globalWidthStr = String.valueOf(config.strokeWidth());
        NodeList elements = doc.getElementsByTagName("*");
        int count = 0;

        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);
            // Apply to common shapes
            if (isShape(el.getTagName())) {
                String color = getStrokeColor(el).toLowerCase();
                String widthStr = null;

                if (hasOverrides && config.strokeWidthOverrides().containsKey(color)) {
                    widthStr = String.valueOf(config.strokeWidthOverrides().get(color));
                } else if (hasGlobal) {
                    widthStr = globalWidthStr;
                }

                if (widthStr != null) {
                    el.setAttribute("stroke-width", widthStr);
                    count++;
                    // Ensure stroke is visible if previously none
                    if (!el.hasAttribute("stroke")) {
                        el.setAttribute("stroke", "#000000"); // default fallback
                    }
                }
            }
        }
        if (count > 0) {
            System.out.println("Applied stroke-width to " + count + " elements.");
        }
    }

    private String getStrokeColor(Element el) {
        if (el.hasAttribute("stroke") && !"none".equalsIgnoreCase(el.getAttribute("stroke"))) {
            return el.getAttribute("stroke");
        }
        if (el.hasAttribute("fill") && !"none".equalsIgnoreCase(el.getAttribute("fill"))) {
            return el.getAttribute("fill");
        }
        return "unknown";
    }

    private boolean isShape(String tag) {
        return tag.matches("rect|circle|ellipse|line|polyline|polygon|path");
    }
}