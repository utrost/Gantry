package org.trostheide.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.trostheide.svgtoolbox.Config;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

class LinesortProcessorTest {

    @Test
    void testReordersForMinimumTravel() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

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
                .linesort(true)
                .build();

        new LinesortProcessor().process(doc, config);

        NodeList children = group.getElementsByTagName("line");
        assertEquals(3, children.getLength());

        Element first = (Element) children.item(0);
        assertEquals("near", first.getAttribute("id"),
                "Nearest line to origin should be first");
    }

    @Test
    void testConsidersBothEndpoints() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        // Line A: (100,100) -> (1,1) — end is near origin
        Element lineA = doc.createElement("line");
        lineA.setAttribute("x1", "100");
        lineA.setAttribute("y1", "100");
        lineA.setAttribute("x2", "1");
        lineA.setAttribute("y2", "1");
        lineA.setAttribute("id", "A");
        group.appendChild(lineA);

        // Line B: (200,200) -> (300,300) — both ends far
        Element lineB = doc.createElement("line");
        lineB.setAttribute("x1", "200");
        lineB.setAttribute("y1", "200");
        lineB.setAttribute("x2", "300");
        lineB.setAttribute("y2", "300");
        lineB.setAttribute("id", "B");
        group.appendChild(lineB);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .linesort(true)
                .build();

        new LinesortProcessor().process(doc, config);

        NodeList children = group.getElementsByTagName("line");
        Element first = (Element) children.item(0);
        assertEquals("A", first.getAttribute("id"),
                "Line with end near origin should be picked first (considers both endpoints)");
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
                .linesort(true)
                .build();

        new LinesortProcessor().process(doc, config);

        assertEquals(1, group.getElementsByTagName("line").getLength());
    }

    @Test
    void testSkipsWhenDisabled() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        Element lineFar = doc.createElement("line");
        lineFar.setAttribute("x1", "500");
        lineFar.setAttribute("y1", "500");
        lineFar.setAttribute("x2", "600");
        lineFar.setAttribute("y2", "600");
        lineFar.setAttribute("id", "far");
        group.appendChild(lineFar);

        Element lineNear = doc.createElement("line");
        lineNear.setAttribute("x1", "1");
        lineNear.setAttribute("y1", "1");
        lineNear.setAttribute("x2", "2");
        lineNear.setAttribute("y2", "2");
        lineNear.setAttribute("id", "near");
        group.appendChild(lineNear);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .linesort(false)
                .build();

        new LinesortProcessor().process(doc, config);

        // Order should be preserved (far first, then near)
        NodeList children = group.getElementsByTagName("line");
        Element first = (Element) children.item(0);
        assertEquals("far", first.getAttribute("id"), "Order should be unchanged when disabled");
    }

    @Test
    void testHandlesEmptyDocument() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .linesort(true)
                .build();

        // Should not throw
        new LinesortProcessor().process(doc, config);
    }

    @Test
    void testWithTwoOptImprovement() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        // Create several lines that benefit from 2-opt uncrossing
        for (int i = 0; i < 6; i++) {
            Element line = doc.createElement("line");
            double x = (i % 2 == 0) ? i * 10 : 100 - i * 10;
            double y = i * 10;
            line.setAttribute("x1", String.valueOf(x));
            line.setAttribute("y1", String.valueOf(y));
            line.setAttribute("x2", String.valueOf(x + 5));
            line.setAttribute("y2", String.valueOf(y + 5));
            line.setAttribute("id", "line" + i);
            group.appendChild(line);
        }

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .linesort(true)
                .linesortTwoOpt(true)
                .build();

        // Should not throw and should produce a valid reordering
        new LinesortProcessor().process(doc, config);

        assertEquals(6, group.getElementsByTagName("line").getLength());
    }

    @Test
    void testOptimizesPathElements() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        // Path far from origin
        Element pathFar = doc.createElement("path");
        pathFar.setAttribute("d", "M 500 500 L 600 600");
        pathFar.setAttribute("id", "far");
        group.appendChild(pathFar);

        // Path near origin
        Element pathNear = doc.createElement("path");
        pathNear.setAttribute("d", "M 1 1 L 2 2");
        pathNear.setAttribute("id", "near");
        group.appendChild(pathNear);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .linesort(true)
                .build();

        new LinesortProcessor().process(doc, config);

        NodeList paths = group.getElementsByTagName("path");
        Element first = (Element) paths.item(0);
        assertEquals("near", first.getAttribute("id"),
                "Nearest path to origin should be first");
    }
}
