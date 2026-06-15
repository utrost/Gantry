package org.trostheide.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.trostheide.svgtoolbox.Config;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

class LayerProcessorTest {

    private static final String INKSCAPE_NS = "http://www.inkscape.org/namespaces/inkscape";

    @Test
    void testOrganizesShapesByStrokeColor() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        root.setAttribute("width", "100");
        root.setAttribute("height", "100");
        doc.appendChild(root);

        Element line1 = doc.createElement("line");
        line1.setAttribute("stroke", "#ff0000");
        line1.setAttribute("x1", "0");
        line1.setAttribute("y1", "0");
        line1.setAttribute("x2", "10");
        line1.setAttribute("y2", "10");
        root.appendChild(line1);

        Element line2 = doc.createElement("line");
        line2.setAttribute("stroke", "#00ff00");
        line2.setAttribute("x1", "20");
        line2.setAttribute("y1", "20");
        line2.setAttribute("x2", "30");
        line2.setAttribute("y2", "30");
        root.appendChild(line2);

        Element line3 = doc.createElement("line");
        line3.setAttribute("stroke", "#ff0000");
        line3.setAttribute("x1", "40");
        line3.setAttribute("y1", "40");
        line3.setAttribute("x2", "50");
        line3.setAttribute("y2", "50");
        root.appendChild(line3);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .build();

        new LayerProcessor().process(doc, config);

        // Should have 2 layer groups (red and green)
        NodeList groups = root.getElementsByTagName("g");
        int layerCount = 0;
        for (int i = 0; i < groups.getLength(); i++) {
            Element g = (Element) groups.item(i);
            if ("layer".equals(g.getAttributeNS(INKSCAPE_NS, "groupmode"))) {
                layerCount++;
            }
        }
        assertEquals(2, layerCount, "Should create 2 layers for 2 colors");
    }

    @Test
    void testSetsInkscapeLayerAttributes() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        root.setAttribute("width", "100");
        root.setAttribute("height", "100");
        doc.appendChild(root);

        Element line = doc.createElement("line");
        line.setAttribute("stroke", "#ff0000");
        line.setAttribute("x1", "0");
        line.setAttribute("y1", "0");
        line.setAttribute("x2", "10");
        line.setAttribute("y2", "10");
        root.appendChild(line);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .build();

        new LayerProcessor().process(doc, config);

        NodeList groups = root.getElementsByTagName("g");
        assertTrue(groups.getLength() > 0, "Should have at least one group");

        Element layer = (Element) groups.item(0);
        assertEquals("layer", layer.getAttributeNS(INKSCAPE_NS, "groupmode"));
        assertTrue(layer.getAttributeNS(INKSCAPE_NS, "label").contains("#ff0000"));
        assertEquals("layer_ff0000", layer.getAttribute("id"));
    }

    @Test
    void testRemovesEmptyNonLayerGroups() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        root.setAttribute("width", "100");
        root.setAttribute("height", "100");
        doc.appendChild(root);

        Element emptyGroup = doc.createElement("g");
        root.appendChild(emptyGroup);

        Element line = doc.createElement("line");
        line.setAttribute("stroke", "#ff0000");
        line.setAttribute("x1", "0");
        line.setAttribute("y1", "0");
        line.setAttribute("x2", "10");
        line.setAttribute("y2", "10");
        emptyGroup.appendChild(line);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .build();

        new LayerProcessor().process(doc, config);

        // The original empty group (after its child was moved) should be removed
        NodeList groups = root.getElementsByTagName("g");
        for (int i = 0; i < groups.getLength(); i++) {
            Element g = (Element) groups.item(i);
            // All remaining groups should be layer groups
            if (!g.getAttributeNS(INKSCAPE_NS, "groupmode").equals("layer")) {
                fail("Non-layer empty group should have been removed");
            }
        }
    }

    @Test
    void testSkipsShapesWithNoColor() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        root.setAttribute("width", "100");
        root.setAttribute("height", "100");
        doc.appendChild(root);

        // Line with stroke="none" and no fill
        Element line = doc.createElement("line");
        line.setAttribute("stroke", "none");
        line.setAttribute("x1", "0");
        line.setAttribute("y1", "0");
        line.setAttribute("x2", "10");
        line.setAttribute("y2", "10");
        root.appendChild(line);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .build();

        new LayerProcessor().process(doc, config);

        // No layers should be created for colorless shapes
        NodeList groups = root.getElementsByTagName("g");
        int layerCount = 0;
        for (int i = 0; i < groups.getLength(); i++) {
            Element g = (Element) groups.item(i);
            if ("layer".equals(g.getAttributeNS(INKSCAPE_NS, "groupmode"))) {
                layerCount++;
            }
        }
        assertEquals(0, layerCount, "No layers should be created for shapes with no color");
    }
}
