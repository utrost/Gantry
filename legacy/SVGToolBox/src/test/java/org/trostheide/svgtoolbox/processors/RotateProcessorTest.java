package org.trostheide.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.trostheide.svgtoolbox.Config;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

class RotateProcessorTest {

    @Test
    void testAppliesRotationTransform() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElementNS("http://www.w3.org/2000/svg", "svg");
        root.setAttribute("width", "200");
        root.setAttribute("height", "100");
        doc.appendChild(root);

        Element rect = doc.createElement("rect");
        rect.setAttribute("x", "10");
        rect.setAttribute("y", "10");
        rect.setAttribute("width", "50");
        rect.setAttribute("height", "50");
        root.appendChild(rect);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .rotationDegrees(45.0)
                .build();

        new RotateProcessor().process(doc, config);

        // Should wrap content in a group with transform
        NodeList groups = root.getElementsByTagName("g");
        assertTrue(groups.getLength() > 0, "Should create a wrapper group");

        Element wrapper = (Element) groups.item(0);
        String transform = wrapper.getAttribute("transform");
        assertTrue(transform.contains("rotate"), "Should have rotate transform");
        assertTrue(transform.contains("45.00"), "Should contain 45 degree rotation");
    }

    @Test
    void testNoRotationWhenZero() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElementNS("http://www.w3.org/2000/svg", "svg");
        root.setAttribute("width", "200");
        root.setAttribute("height", "100");
        doc.appendChild(root);

        Element rect = doc.createElement("rect");
        root.appendChild(rect);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .rotationDegrees(0.0)
                .build();

        new RotateProcessor().process(doc, config);

        // No wrapper group should be created
        NodeList groups = root.getElementsByTagName("g");
        assertEquals(0, groups.getLength(), "Should not create wrapper group for 0 rotation");
    }

    @Test
    void testSwapsDimensionsFor90Degrees() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElementNS("http://www.w3.org/2000/svg", "svg");
        root.setAttribute("width", "200");
        root.setAttribute("height", "100");
        doc.appendChild(root);

        Element rect = doc.createElement("rect");
        root.appendChild(rect);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .rotationDegrees(90.0)
                .build();

        new RotateProcessor().process(doc, config);

        // Width and height should be swapped for 90 degree rotation
        String width = root.getAttribute("width");
        String height = root.getAttribute("height");
        assertEquals("100.00", width, "Width should become original height");
        assertEquals("200.00", height, "Height should become original width");
    }

    @Test
    void testRotation180DoesNotSwapDimensions() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElementNS("http://www.w3.org/2000/svg", "svg");
        root.setAttribute("width", "200");
        root.setAttribute("height", "100");
        doc.appendChild(root);

        Element rect = doc.createElement("rect");
        root.appendChild(rect);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .rotationDegrees(180.0)
                .build();

        new RotateProcessor().process(doc, config);

        // 180 rotation should NOT swap dimensions (180 % 180 == 0, not > 45)
        String width = root.getAttribute("width");
        String height = root.getAttribute("height");
        // Dimensions should stay the same
        assertFalse(width.startsWith("100"), "Width should not be swapped for 180 degree rotation");

        // But should still have a rotation transform
        NodeList groups = root.getElementsByTagName("g");
        assertTrue(groups.getLength() > 0, "Should have wrapper group for 180 rotation");
    }
}
