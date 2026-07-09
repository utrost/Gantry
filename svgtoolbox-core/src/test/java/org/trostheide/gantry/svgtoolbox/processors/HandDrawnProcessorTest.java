package org.trostheide.gantry.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.svgtoolbox.Config;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

class HandDrawnProcessorTest {

    private static Document emptyDoc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    private static Config.Builder enabled() {
        return new Config.Builder().inputPath("in").outputPath("out")
                .handdrawn(true)
                .handdrawnMagnitude(3.0)
                .handdrawnSegment(5.0)
                .handdrawnWavelength(25.0)
                .handdrawnSeed(42L);
    }

    @Test
    void testSkipsWhenDisabled() throws Exception {
        Document doc = emptyDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);
        Element path = doc.createElement("path");
        String original = "M 0 0 L 100 0";
        path.setAttribute("d", original);
        root.appendChild(path);

        Config config = new Config.Builder().inputPath("in").outputPath("out")
                .handdrawn(false).build();
        new HandDrawnProcessor().process(doc, config);

        assertEquals(original, path.getAttribute("d"), "Disabled processor must not touch paths");
    }

    @Test
    void testResamplesAndJittersStraightLine() throws Exception {
        Document doc = emptyDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);
        Element path = doc.createElement("path");
        path.setAttribute("d", "M 0 0 L 100 0");
        root.appendChild(path);

        new HandDrawnProcessor().process(doc, enabled().build());

        String d = path.getAttribute("d");
        // A 100px line resampled at 5px spacing -> many samples, so many L commands.
        long lineCount = d.chars().filter(c -> c == 'L').count();
        assertTrue(lineCount > 5, "Line should be resampled into many segments. Result: " + d);
        assertNotEquals("M 0 0 L 100 0", d, "Geometry should have changed");
    }

    @Test
    void testEndpointsArePinned() throws Exception {
        Document doc = emptyDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);
        Element path = doc.createElement("path");
        path.setAttribute("d", "M 10 20 L 210 20");
        root.appendChild(path);

        new HandDrawnProcessor().process(doc, enabled().build());

        String d = path.getAttribute("d");
        // First point must be unchanged (offset tapered to zero).
        assertTrue(d.startsWith("M10.0000,20.0000"), "Start point must stay pinned. Result: " + d);
        // Last point must land back on the original endpoint.
        assertTrue(d.trim().endsWith("L210.0000,20.0000"), "End point must stay pinned. Result: " + d);
    }

    @Test
    void testInteriorActuallyMoves() throws Exception {
        Document doc = emptyDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);
        Element path = doc.createElement("path");
        // Horizontal line: any y != 0 in the output proves normal-direction jitter.
        path.setAttribute("d", "M 0 0 L 300 0");
        root.appendChild(path);

        new HandDrawnProcessor().process(doc, enabled().build());

        String d = path.getAttribute("d");
        boolean movedOffAxis = false;
        for (String token : d.replace("M", "L").split("L")) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            String[] xy = t.split(",");
            if (xy.length == 2 && Math.abs(Double.parseDouble(xy[1])) > 0.01) {
                movedOffAxis = true;
                break;
            }
        }
        assertTrue(movedOffAxis, "Interior samples should wobble off the original axis. Result: " + d);
    }

    @Test
    void testDeterministicForSameSeed() throws Exception {
        String result1 = runOnce(42L);
        String result2 = runOnce(42L);
        String result3 = runOnce(99L);
        assertEquals(result1, result2, "Same seed must produce identical output");
        assertNotEquals(result1, result3, "Different seed should produce different output");
    }

    private String runOnce(long seed) throws Exception {
        Document doc = emptyDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);
        Element path = doc.createElement("path");
        path.setAttribute("d", "M 0 0 L 100 40 L 200 0");
        root.appendChild(path);
        new HandDrawnProcessor().process(doc, enabled().handdrawnSeed(seed).build());
        return path.getAttribute("d");
    }

    @Test
    void testClosedPathStaysClosed() throws Exception {
        Document doc = emptyDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);
        Element path = doc.createElement("path");
        path.setAttribute("d", "M 0 0 L 100 0 L 100 100 L 0 100 Z");
        root.appendChild(path);

        new HandDrawnProcessor().process(doc, enabled().build());

        assertTrue(path.getAttribute("d").contains("Z"), "Closed path must remain closed");
    }

    @Test
    void testHandlesEmptyDocument() throws Exception {
        Document doc = emptyDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        // Must not throw.
        new HandDrawnProcessor().process(doc, enabled().build());
        assertEquals(0, root.getElementsByTagName("path").getLength());
    }
}
