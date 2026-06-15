package org.trostheide.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.trostheide.svgtoolbox.Config;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

class LinemergeProcessorTest {

    @Test
    void testMergesAdjacentPaths() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        // Path A: (0,0) -> (10,10)
        Element pathA = doc.createElement("path");
        pathA.setAttribute("d", "M 0 0 L 10 10");
        group.appendChild(pathA);

        // Path B: (10,10) -> (20,20) — starts where A ends
        Element pathB = doc.createElement("path");
        pathB.setAttribute("d", "M 10 10 L 20 20");
        group.appendChild(pathB);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .linemerge(true)
                .linemergeTolerance(1.0)
                .build();

        new LinemergeProcessor().process(doc, config);

        NodeList paths = group.getElementsByTagName("path");
        assertEquals(1, paths.getLength(), "Two adjacent paths should merge into one");

        String d = ((Element) paths.item(0)).getAttribute("d");
        assertTrue(d.contains("20"), "Merged path should reach endpoint of second path");
    }

    @Test
    void testMergesReversedPaths() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        // Path A: (0,0) -> (10,10)
        Element pathA = doc.createElement("path");
        pathA.setAttribute("d", "M 0 0 L 10 10");
        group.appendChild(pathA);

        // Path B: (20,20) -> (10,10) — end matches A's end (needs reversal)
        Element pathB = doc.createElement("path");
        pathB.setAttribute("d", "M 20 20 L 10 10");
        group.appendChild(pathB);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .linemerge(true)
                .linemergeTolerance(1.0)
                .build();

        new LinemergeProcessor().process(doc, config);

        NodeList paths = group.getElementsByTagName("path");
        assertEquals(1, paths.getLength(), "Paths with reversed endpoints should merge");
    }

    @Test
    void testSkipsClosedPaths() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        // Closed path
        Element closedPath = doc.createElement("path");
        closedPath.setAttribute("d", "M 0 0 L 10 0 L 10 10 Z");
        group.appendChild(closedPath);

        // Open path nearby
        Element openPath = doc.createElement("path");
        openPath.setAttribute("d", "M 0 0 L 20 20");
        group.appendChild(openPath);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .linemerge(true)
                .linemergeTolerance(1.0)
                .build();

        new LinemergeProcessor().process(doc, config);

        NodeList paths = group.getElementsByTagName("path");
        assertEquals(2, paths.getLength(), "Closed path should not be merged");
    }

    @Test
    void testSkipsWhenDisabled() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        Element pathA = doc.createElement("path");
        pathA.setAttribute("d", "M 0 0 L 10 10");
        group.appendChild(pathA);

        Element pathB = doc.createElement("path");
        pathB.setAttribute("d", "M 10 10 L 20 20");
        group.appendChild(pathB);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .linemerge(false)
                .build();

        new LinemergeProcessor().process(doc, config);

        NodeList paths = group.getElementsByTagName("path");
        assertEquals(2, paths.getLength(), "Paths should not merge when disabled");
    }

    @Test
    void testHandlesEmptyDocument() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .linemerge(true)
                .linemergeTolerance(1.0)
                .build();

        // Should not throw
        new LinemergeProcessor().process(doc, config);
    }

    @Test
    void testDoesNotMergeFarApartPaths() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        // Path A: (0,0) -> (10,10)
        Element pathA = doc.createElement("path");
        pathA.setAttribute("d", "M 0 0 L 10 10");
        group.appendChild(pathA);

        // Path B: (100,100) -> (200,200) — far from A
        Element pathB = doc.createElement("path");
        pathB.setAttribute("d", "M 100 100 L 200 200");
        group.appendChild(pathB);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .linemerge(true)
                .linemergeTolerance(1.0)
                .build();

        new LinemergeProcessor().process(doc, config);

        NodeList paths = group.getElementsByTagName("path");
        assertEquals(2, paths.getLength(), "Far apart paths should not merge");
    }
}
