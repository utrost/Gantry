package org.trostheide.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.trostheide.svgtoolbox.Config;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

class CropProcessorTest {

    @Test
    void testRemovesShapeOutsideBounds() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        // Rect completely outside crop bounds
        Element rect = doc.createElement("rect");
        rect.setAttribute("x", "500");
        rect.setAttribute("y", "500");
        rect.setAttribute("width", "50");
        rect.setAttribute("height", "50");
        root.appendChild(rect);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .cropBounds(new Rectangle2D.Double(0, 0, 100, 100))
                .build();

        new CropProcessor().process(doc, config);

        assertEquals(0, root.getElementsByTagName("rect").getLength(),
                "Rect outside bounds should be removed");
    }

    @Test
    void testKeepsShapeInsideBounds() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element rect = doc.createElement("rect");
        rect.setAttribute("x", "10");
        rect.setAttribute("y", "10");
        rect.setAttribute("width", "50");
        rect.setAttribute("height", "50");
        root.appendChild(rect);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .cropBounds(new Rectangle2D.Double(0, 0, 100, 100))
                .build();

        new CropProcessor().process(doc, config);

        assertEquals(1, root.getElementsByTagName("rect").getLength(),
                "Rect inside bounds should remain");
    }

    @Test
    void testNoCropWhenBoundsNull() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element rect = doc.createElement("rect");
        rect.setAttribute("x", "500");
        rect.setAttribute("y", "500");
        rect.setAttribute("width", "50");
        rect.setAttribute("height", "50");
        root.appendChild(rect);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .build(); // cropBounds defaults to null

        new CropProcessor().process(doc, config);

        assertEquals(1, root.getElementsByTagName("rect").getLength(),
                "Rect should remain when no crop bounds");
    }

    @Test
    void testKeepsPartiallyOverlappingShape() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        // Rect partially overlapping crop bounds
        Element rect = doc.createElement("rect");
        rect.setAttribute("x", "80");
        rect.setAttribute("y", "80");
        rect.setAttribute("width", "50");
        rect.setAttribute("height", "50");
        root.appendChild(rect);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .cropBounds(new Rectangle2D.Double(0, 0, 100, 100))
                .build();

        new CropProcessor().process(doc, config);

        assertEquals(1, root.getElementsByTagName("rect").getLength(),
                "Partially overlapping rect should be kept");
    }

    @Test
    void testCropsCircleOutsideBounds() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element circle = doc.createElement("circle");
        circle.setAttribute("cx", "300");
        circle.setAttribute("cy", "300");
        circle.setAttribute("r", "10");
        root.appendChild(circle);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .cropBounds(new Rectangle2D.Double(0, 0, 100, 100))
                .build();

        new CropProcessor().process(doc, config);

        assertEquals(0, root.getElementsByTagName("circle").getLength(),
                "Circle outside bounds should be removed");
    }
}
