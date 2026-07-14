package org.trostheide.gantry.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.svgtoolbox.Config;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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

        String d = path.getAttribute("d");
        assertTrue(d.contains("Z"), "Closed path must remain closed");
        long lineCount = d.chars().filter(c -> c == 'L').count();
        assertTrue(lineCount >= 75, "The closing edge must be resampled and roughened too. Result: " + d);
    }

    @Test
    void testFlattensBezierCurveBeforeJittering() throws Exception {
        Document doc = emptyDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);
        Element path = doc.createElement("path");
        path.setAttribute("d", "M 0 0 C 0 100 100 100 100 0");
        root.appendChild(path);

        new HandDrawnProcessor().process(doc, enabled().build());

        double maxY = 0;
        String coordinates = path.getAttribute("d").replace("M", "").replace("L", " ").replace("Z", "");
        for (String token : coordinates.trim().split("\\s+")) {
            if (token.isBlank()) continue;
            String[] xy = token.split(",");
            maxY = Math.max(maxY, Double.parseDouble(xy[1]));
        }
        assertTrue(maxY > 60, "Curve control geometry must survive flattening; max y was " + maxY);
    }

    @Test
    void testConvertsAllSvgPrimitivesAndPreservesNonGeometryAttributes() throws Exception {
        Document doc = emptyDoc();
        Element root = doc.createElementNS("http://www.w3.org/2000/svg", "svg");
        doc.appendChild(root);

        Element line = append(root, "line", "x1", "0", "y1", "0", "x2", "100", "y2", "0");
        line.setAttribute("id", "kept-id");
        line.setAttribute("class", "kept-class");
        line.setAttribute("transform", "translate(5 7)");
        append(root, "rect", "width", "80", "height", "40", "rx", "8");
        append(root, "circle", "cx", "50", "cy", "50", "r", "25");
        append(root, "ellipse", "cx", "50", "cy", "50", "rx", "30", "ry", "15");
        append(root, "polyline", "points", "0,0 50,40 100,0");
        append(root, "polygon", "points", "0,0 100,0 50,80");

        new HandDrawnProcessor().process(doc, enabled().build());

        for (String primitive : new String[]{"line", "rect", "circle", "ellipse", "polyline", "polygon"}) {
            assertEquals(0, doc.getElementsByTagName(primitive).getLength(), primitive + " should become a path");
        }
        NodeList paths = doc.getElementsByTagName("path");
        assertEquals(6, paths.getLength());
        Element convertedLine = (Element) paths.item(0);
        assertEquals("kept-id", convertedLine.getAttribute("id"));
        assertEquals("kept-class", convertedLine.getAttribute("class"));
        assertEquals("translate(5 7)", convertedLine.getAttribute("transform"));
        assertFalse(convertedLine.hasAttribute("x1"), "Primitive geometry attributes must not leak onto paths");
        assertTrue(convertedLine.getAttribute("d").chars().filter(c -> c == 'L').count() > 5);
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

    private static Element append(Element parent, String name, String... attributes) {
        Document doc = parent.getOwnerDocument();
        Element element = doc.createElementNS("http://www.w3.org/2000/svg", name);
        for (int i = 0; i < attributes.length; i += 2) {
            element.setAttribute(attributes[i], attributes[i + 1]);
        }
        parent.appendChild(element);
        return element;
    }
}
