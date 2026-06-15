package org.trostheide.svgtoolbox.processors;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.Processor;
import org.trostheide.svgtoolbox.core.ShapeParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

public class CropProcessor implements Processor {

    @Override
    public void process(Document doc, Config config) {
        if (config.cropBounds() == null)
            return;

        Rectangle2D bounds = config.cropBounds();

        NodeList elements = doc.getElementsByTagName("*");
        List<Element> toRemove = new ArrayList<>();

        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);
            if (isShape(el)) {
                Shape s = ShapeParser.parse(el);
                if (s == null)
                    continue;

                // If the shape is completely OUTSIDE the bounds, mark for removal.
                // We use standard AWT intersection check.
                if (!s.intersects(bounds)) {
                    toRemove.add(el);
                }
            }
        }

        for (Element el : toRemove) {
            el.getParentNode().removeChild(el);
        }

        System.out.println("Cropped " + toRemove.size() + " elements outside bounds " + bounds);
    }

    private boolean isShape(Element el) {
        String t = el.getTagName();
        return t.equals("path") || t.equals("line") || t.equals("rect") || t.equals("circle");
    }
}
