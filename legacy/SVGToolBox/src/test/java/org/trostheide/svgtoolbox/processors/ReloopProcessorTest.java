package org.trostheide.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.trostheide.svgtoolbox.Config;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

class ReloopProcessorTest {

    @Test
    void testClosedPathGetsRelooped() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        // Previous path ends at (100, 100)
        Element prev = doc.createElement("path");
        prev.setAttribute("d", "M 0 0 L 100 100");
        prev.setAttribute("id", "prev");
        group.appendChild(prev);

        // Closed path: triangle with vertex (90,90) closest to pen position (100,100)
        // Vertices: (0,0), (200,0), (90,90)
        Element closed = doc.createElement("path");
        closed.setAttribute("d", "M 0 0 L 200 0 L 90 90 Z");
        closed.setAttribute("id", "closed");
        group.appendChild(closed);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .reloop(true)
                .build();

        new ReloopProcessor().process(doc, config);

        String newD = closed.getAttribute("d");
        // Should now start near (90, 90) — the closest vertex to pen at (100,100)
        assertTrue(newD.startsWith("M 90."), "Path should start at closest vertex (90,90), got: " + newD);
        assertTrue(newD.endsWith("Z"), "Path should still be closed");
    }

    @Test
    void testOpenPathStaysUnchanged() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        Element openPath = doc.createElement("path");
        String originalD = "M 0 0 L 100 100 L 200 0";
        openPath.setAttribute("d", originalD);
        openPath.setAttribute("id", "open");
        group.appendChild(openPath);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .reloop(true)
                .build();

        new ReloopProcessor().process(doc, config);

        assertEquals(originalD, openPath.getAttribute("d"),
                "Open path should not be modified");
    }

    @Test
    void testDisabledConfigSkipsProcessing() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        Element closed = doc.createElement("path");
        String originalD = "M 0 0 L 200 0 L 100 100 Z";
        closed.setAttribute("d", originalD);
        closed.setAttribute("id", "closed");
        group.appendChild(closed);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .reloop(false)
                .build();

        new ReloopProcessor().process(doc, config);

        assertEquals(originalD, closed.getAttribute("d"),
                "Path should not be modified when reloop is disabled");
    }

    @Test
    void testHandlesEmptyGroup() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .reloop(true)
                .build();

        // Should not throw
        new ReloopProcessor().process(doc, config);
    }

    @Test
    void testAlreadyOptimalStartPointUnchanged() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        // Previous path ends at origin (0,0)
        Element prev = doc.createElement("path");
        prev.setAttribute("d", "M 50 50 L 0 0");
        prev.setAttribute("id", "prev");
        group.appendChild(prev);

        // Closed path already starts at (0,0) — closest to pen at (0,0)
        Element closed = doc.createElement("path");
        closed.setAttribute("d", "M 0 0 L 100 0 L 100 100 Z");
        closed.setAttribute("id", "closed");
        group.appendChild(closed);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .reloop(true)
                .build();

        new ReloopProcessor().process(doc, config);

        String newD = closed.getAttribute("d");
        // Start point should still be near (0,0) since it was already optimal
        // When already optimal (index 0), the path is not modified
        assertTrue(newD.startsWith("M 0 0") || newD.startsWith("M 0.0"),
                "Already-optimal path should keep start near (0,0), got: " + newD);
    }
}
