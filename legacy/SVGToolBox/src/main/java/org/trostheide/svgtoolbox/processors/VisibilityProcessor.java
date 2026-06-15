package org.trostheide.svgtoolbox.processors;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.Processor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

public class VisibilityProcessor implements Processor {

    @Override
    public void process(Document doc, Config config) {
        if (config.hiddenLayers() == null || config.hiddenLayers().isEmpty()) {
            return;
        }

        Element root = doc.getDocumentElement();
        List<Element> toRemove = new ArrayList<>();
        collectHiddenShapes(root, config.hiddenLayers(), toRemove);

        int count = 0;
        for (Element el : toRemove) {
            Node parent = el.getParentNode();
            if (parent != null) {
                parent.removeChild(el);
                count++;
            }
        }
        
        if (count > 0) {
            System.out.println("Removed " + count + " elements from hidden layers.");
        }
    }

    private void collectHiddenShapes(Element parent, List<String> hiddenLayers, List<Element> toRemove) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element) {
                Element el = (Element) node;
                
                // Check color
                String color = getStrokeColor(el);
                if (color != null && !color.equals("none") && !color.equals("unknown")) {
                    if (hiddenLayers.contains(color.toLowerCase())) {
                        toRemove.add(el);
                        // If we are removing a group, don't need to visit children
                        continue;
                    }
                }
                
                // Visit children
                collectHiddenShapes(el, hiddenLayers, toRemove);
            }
        }
    }
    
    private String getStrokeColor(Element el) {
        if (el.hasAttribute("stroke") && !"none".equalsIgnoreCase(el.getAttribute("stroke"))) {
            return el.getAttribute("stroke");
        }
        // If it's a shape and has fill but no stroke, returning fill is correct for our layer matching
        if (isShape(el.getTagName()) && el.hasAttribute("fill") && !"none".equalsIgnoreCase(el.getAttribute("fill"))) {
            return el.getAttribute("fill");
        }
        return "unknown";
    }

    private boolean isShape(String tag) {
        return tag.matches("rect|circle|ellipse|line|polyline|polygon|path");
    }
}
