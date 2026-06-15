package org.trostheide.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.HatchStyle;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisibilityProcessorTest {

    @Test
    void testHiddenLayersRemoved() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);
        
        Element g1 = doc.createElement("g");
        root.appendChild(g1);
        
        Element rect1 = doc.createElement("rect");
        rect1.setAttribute("stroke", "#ff0000");
        g1.appendChild(rect1);
        
        Element rect2 = doc.createElement("rect");
        rect2.setAttribute("stroke", "#00ff00");
        g1.appendChild(rect2);
        
        Element rect3 = doc.createElement("rect");
        rect3.setAttribute("fill", "#0000ff");
        g1.appendChild(rect3);

        Config config = new Config(
                "in", "out", 1.0f, Collections.emptyList(), false,
                HatchStyle.of(45, 5), Collections.emptyMap(), Collections.emptyMap(), 
                List.of("#ff0000", "#0000ff"), // hidden layers
                Collections.emptyList(), 0.0,
                1.0, "linear", 45.0, 5.0, 0.0, false, null, false, false, 0.378, false, 1.89, false, false, false
        );

        VisibilityProcessor processor = new VisibilityProcessor();
        processor.process(doc, config);

        assertEquals(1, root.getElementsByTagName("rect").getLength(), "Only the green rect should remain");
        assertEquals("#00ff00", ((Element) root.getElementsByTagName("rect").item(0)).getAttribute("stroke"));
    }

    @Test
    void testNoChangeWhenHiddenLayersEmpty() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element rect = doc.createElement("rect");
        rect.setAttribute("stroke", "#ff0000");
        root.appendChild(rect);

        Config config = new Config(
                "in", "out", 1.0f, Collections.emptyList(), false,
                HatchStyle.of(45, 5), Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyList(), // empty hidden layers
                Collections.emptyList(), 0.0,
                1.0, "linear", 45.0, 5.0, 0.0, false, null, false, false, 0.378, false, 1.89, false, false, false
        );

        VisibilityProcessor processor = new VisibilityProcessor();
        processor.process(doc, config);

        assertEquals(1, root.getElementsByTagName("rect").getLength(), "Rect should remain when no hidden layers");
    }

    @Test
    void testHiddenLayersByFillColor() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element rect = doc.createElement("rect");
        rect.setAttribute("fill", "#ff0000"); // fill only, no stroke
        root.appendChild(rect);

        Config config = new Config(
                "in", "out", 1.0f, Collections.emptyList(), false,
                HatchStyle.of(45, 5), Collections.emptyMap(), Collections.emptyMap(),
                List.of("#ff0000"), // hide red
                Collections.emptyList(), 0.0,
                1.0, "linear", 45.0, 5.0, 0.0, false, null, false, false, 0.378, false, 1.89, false, false, false
        );

        VisibilityProcessor processor = new VisibilityProcessor();
        processor.process(doc, config);

        assertEquals(0, root.getElementsByTagName("rect").getLength(),
                "Rect with matching fill color should be removed");
    }

    @Test
    void testRecursivelyRemovesNestedShapes() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        group.setAttribute("stroke", "#ff0000");
        root.appendChild(group);

        Element rect1 = doc.createElement("rect");
        group.appendChild(rect1);

        Element rect2 = doc.createElement("rect");
        group.appendChild(rect2);

        Config config = new Config(
                "in", "out", 1.0f, Collections.emptyList(), false,
                HatchStyle.of(45, 5), Collections.emptyMap(), Collections.emptyMap(),
                List.of("#ff0000"),
                Collections.emptyList(), 0.0,
                1.0, "linear", 45.0, 5.0, 0.0, false, null, false, false, 0.378, false, 1.89, false, false, false
        );

        VisibilityProcessor processor = new VisibilityProcessor();
        processor.process(doc, config);

        // The group with matching color should be removed (along with its children)
        assertEquals(0, root.getElementsByTagName("g").getLength(),
                "Group with matching color should be removed");
        assertEquals(0, root.getElementsByTagName("rect").getLength(),
                "Children of removed group should also be gone");
    }
}
