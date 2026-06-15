package org.trostheide.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.trostheide.svgtoolbox.Config;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

class PathOptimizeProcessorTest {

    @Test
    void testReordersShapesToMinimizeTravel() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        // Place 3 lines: far, near origin, medium distance
        // Line far from origin: (500,500) -> (600,600)
        Element lineFar = doc.createElement("line");
        lineFar.setAttribute("x1", "500");
        lineFar.setAttribute("y1", "500");
        lineFar.setAttribute("x2", "600");
        lineFar.setAttribute("y2", "600");
        lineFar.setAttribute("id", "far");
        group.appendChild(lineFar);

        // Line near origin: (1,1) -> (2,2)
        Element lineNear = doc.createElement("line");
        lineNear.setAttribute("x1", "1");
        lineNear.setAttribute("y1", "1");
        lineNear.setAttribute("x2", "2");
        lineNear.setAttribute("y2", "2");
        lineNear.setAttribute("id", "near");
        group.appendChild(lineNear);

        // Line medium: (10,10) -> (20,20)
        Element lineMed = doc.createElement("line");
        lineMed.setAttribute("x1", "10");
        lineMed.setAttribute("y1", "10");
        lineMed.setAttribute("x2", "20");
        lineMed.setAttribute("y2", "20");
        lineMed.setAttribute("id", "med");
        group.appendChild(lineMed);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .optimizePaths(true)
                .build();

        new PathOptimizeProcessor().process(doc, config);

        // After optimization, nearest-neighbor from (0,0) should pick near first
        NodeList children = group.getElementsByTagName("line");
        assertEquals(3, children.getLength());

        Element first = (Element) children.item(0);
        assertEquals("near", first.getAttribute("id"),
                "Nearest line to origin should be first");
    }

    @Test
    void testSkipsSingleShape() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        Element line = doc.createElement("line");
        line.setAttribute("x1", "10");
        line.setAttribute("y1", "10");
        line.setAttribute("x2", "20");
        line.setAttribute("y2", "20");
        group.appendChild(line);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .optimizePaths(true)
                .build();

        // Should not throw with single shape
        new PathOptimizeProcessor().process(doc, config);

        assertEquals(1, group.getElementsByTagName("line").getLength());
    }

    @Test
    void testOptimizesRectElements() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        // Rect far from origin
        Element rectFar = doc.createElement("rect");
        rectFar.setAttribute("x", "200");
        rectFar.setAttribute("y", "200");
        rectFar.setAttribute("width", "10");
        rectFar.setAttribute("height", "10");
        rectFar.setAttribute("id", "far");
        group.appendChild(rectFar);

        // Rect near origin
        Element rectNear = doc.createElement("rect");
        rectNear.setAttribute("x", "1");
        rectNear.setAttribute("y", "1");
        rectNear.setAttribute("width", "5");
        rectNear.setAttribute("height", "5");
        rectNear.setAttribute("id", "near");
        group.appendChild(rectNear);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .optimizePaths(true)
                .build();

        new PathOptimizeProcessor().process(doc, config);

        NodeList rects = group.getElementsByTagName("rect");
        Element first = (Element) rects.item(0);
        assertEquals("near", first.getAttribute("id"),
                "Nearest rect to origin should be first");
    }
}
