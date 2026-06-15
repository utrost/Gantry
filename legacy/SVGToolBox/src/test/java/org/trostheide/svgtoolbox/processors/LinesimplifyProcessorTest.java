package org.trostheide.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.trostheide.svgtoolbox.Config;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

class LinesimplifyProcessorTest {

    @Test
    void testRemovesCollinearPoints() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element path = doc.createElement("path");
        // Three collinear points: (0,0) -> (5,5) -> (10,10). Middle is redundant.
        path.setAttribute("d", "M 0 0 L 5 5 L 10 10");
        root.appendChild(path);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .linesimplify(true)
                .linesimplifyTolerance(1.0)
                .build();

        new LinesimplifyProcessor().process(doc, config);

        String d = path.getAttribute("d");
        assertNotNull(d);
        // Should only have start and end point, no middle
        assertFalse(d.contains("5.0000,5.0000") || d.contains("5,5"),
                "Middle collinear point should be removed. Result: " + d);
        assertTrue(d.contains("0.0000,0.0000"), "Start point should remain");
        assertTrue(d.contains("10.0000,10.0000"), "End point should remain");
    }

    @Test
    void testPreservesSignificantPoints() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element path = doc.createElement("path");
        // Sharp spike: (0,0) -> (5,50) -> (10,0). Should be preserved.
        path.setAttribute("d", "M 0 0 L 5 50 L 10 0");
        root.appendChild(path);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .linesimplify(true)
                .linesimplifyTolerance(1.0)
                .build();

        new LinesimplifyProcessor().process(doc, config);

        String d = path.getAttribute("d");
        // All three points should be preserved
        assertTrue(d.contains("50"), "Spike point should be preserved. Result: " + d);
    }

    @Test
    void testSkipsWhenDisabled() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element path = doc.createElement("path");
        String original = "M 0 0 L 5 5 L 10 10";
        path.setAttribute("d", original);
        root.appendChild(path);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .linesimplify(false)
                .build();

        new LinesimplifyProcessor().process(doc, config);

        assertEquals(original, path.getAttribute("d"), "Path should be unchanged when disabled");
    }

    @Test
    void testHandlesEmptyDocument() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .linesimplify(true)
                .linesimplifyTolerance(1.0)
                .build();

        // Should not throw
        new LinesimplifyProcessor().process(doc, config);
        assertEquals(0, root.getElementsByTagName("path").getLength());
    }

    @Test
    void testHandlesSinglePath() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element path = doc.createElement("path");
        path.setAttribute("d", "M 0 0 L 10 10");
        root.appendChild(path);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .linesimplify(true)
                .linesimplifyTolerance(1.0)
                .build();

        // Should not throw with only 2 points (can't simplify)
        new LinesimplifyProcessor().process(doc, config);
        assertNotNull(path.getAttribute("d"));
    }

    @Test
    void testPreservesClosedPaths() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element path = doc.createElement("path");
        path.setAttribute("d", "M 0 0 L 50 0 L 50 50 L 0 50 Z");
        root.appendChild(path);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .linesimplify(true)
                .linesimplifyTolerance(0.5)
                .build();

        new LinesimplifyProcessor().process(doc, config);

        String d = path.getAttribute("d");
        assertTrue(d.contains("Z"), "Closed path should remain closed");
    }
}
