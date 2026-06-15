package org.trostheide.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.HatchStyle;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class HatchProcessorTest {

    @Test
    void testSquareHatching() throws Exception {
        // Setup
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element rect = doc.createElement("rect");
        rect.setAttribute("x", "0");
        rect.setAttribute("y", "0");
        rect.setAttribute("width", "100");
        rect.setAttribute("height", "100");
        rect.setAttribute("fill", "#FF0000"); // Red
        root.appendChild(rect);

        Config config = new Config(
                "in", "out", 1.0f, Collections.emptyList(), true,
                new HatchStyle(45.0, 10.0, "linear"),
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), 1.0, 0.0, "linear", 45.0, 5.0, 0.0, false, null, false, false, 0.378, false, 1.89, false, false, false);

        HatchProcessor processor = new HatchProcessor();
        processor.process(doc, config);

        // Verification
        // The original rect should be removed/replaced or at least hidden?
        // HatchProcessor removes the target element:
        // target.getParentNode().removeChild(target);

        NodeList remainingRects = doc.getElementsByTagName("rect");
        assertEquals(0, remainingRects.getLength(), "Original rect should be removed");

        NodeList groups = doc.getElementsByTagName("g");
        assertEquals(1, groups.getLength(), "Should create a group for hatches");

        Element group = (Element) groups.item(0);
        NodeList lines = group.getElementsByTagName("line");
        assertTrue(lines.getLength() > 0, "Should generate hatch lines");

        // With 45 degrees and gap 10 on a 100x100 square, we expect roughly 10-14
        // lines.
        // Diagonal is 141. 141/10 ~ 14.
        assertTrue(lines.getLength() >= 5 && lines.getLength() <= 20,
                "Should have reasonable number of lines: " + lines.getLength());
    }

    @Test
    void testIgnoreSmallArea() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element rect = doc.createElement("rect");
        rect.setAttribute("x", "0");
        rect.setAttribute("y", "0");
        rect.setAttribute("width", "5");
        rect.setAttribute("height", "5");
        rect.setAttribute("fill", "#00FF00");
        root.appendChild(rect);

        // Min area 100. Shape is 25. Should be ignored.
        Config config = new Config(
                "in", "out", 1.0f, Collections.emptyList(), true,
                new HatchStyle(0.0, 2.0, "linear"),
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), 100.0, 0.0, "linear", 45.0, 5.0, 0.0, false, null, false, false, 0.378, false, 1.89, false, false, false);

        HatchProcessor processor = new HatchProcessor();
        processor.process(doc, config);

        // Verification
        NodeList lines = doc.getElementsByTagName("line");
        assertEquals(0, lines.getLength(), "Should not hatch small shape");

        // Should simple skip it? Code says: if (shouldSkip...) continue.
        // Wait, area check is inside hatchShape.
        // logic:
        // if ((bounds.getWidth() * bounds.getHeight()) < config.minHatchArea()) return
        // false;
        // if hatchShape returns false, it returns false inside the loop.
        // count increments only if true.
        // But the element is NOT removed if hatchShape returns false?
        // Let's check HatchProcessor logic.
        // boolean hatchShape(...) { ... return false; }
        // if (hatchShape(...)) { count++; }

        // So original element remains touched?
        // Yes.
        NodeList rects = doc.getElementsByTagName("rect");
        assertEquals(1, rects.getLength(), "Small rect should remain untouched");
    }

    @Test
    void testEmptyPattern() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element rect = doc.createElement("rect");
        rect.setAttribute("x", "0");
        rect.setAttribute("y", "0");
        rect.setAttribute("width", "100");
        rect.setAttribute("height", "100");
        rect.setAttribute("fill", "#0000FF"); // Blue
        root.appendChild(rect);

        Config config = new Config(
                "in", "out", 2.0f, Collections.emptyList(), true,
                new HatchStyle(45.0, 10.0, "empty"),
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), 1.0, 0.0, "empty", 45.0, 5.0, 0.0, false, null, false, false, 0.378, false, 1.89, false, false, false);

        HatchProcessor processor = new HatchProcessor();
        processor.process(doc, config);

        // Verification
        NodeList remainingRects = doc.getElementsByTagName("rect");
        assertEquals(0, remainingRects.getLength(), "Original rect should be removed");

        NodeList paths = doc.getElementsByTagName("path");
        assertTrue(paths.getLength() > 0, "Should generate an outline path");
        
        NodeList lines = doc.getElementsByTagName("line");
        assertEquals(0, lines.getLength(), "Should NOT generate hatch lines for 'empty'");
    }

    @Test
    void testNonePattern() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element rect = doc.createElement("rect");
        rect.setAttribute("x", "0");
        rect.setAttribute("y", "0");
        rect.setAttribute("width", "100");
        rect.setAttribute("height", "100");
        rect.setAttribute("fill", "#FFFF00"); // Yellow
        root.appendChild(rect);

        Config config = new Config(
                "in", "out", 1.0f, Collections.emptyList(), true,
                new HatchStyle(45.0, 10.0, "none"),
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), 1.0, 0.0, "none", 45.0, 5.0, 0.0, false, null, false, false, 0.378, false, 1.89, false, false, false);

        HatchProcessor processor = new HatchProcessor();
        processor.process(doc, config);

        // Verification
        NodeList remainingRects = doc.getElementsByTagName("rect");
        assertEquals(1, remainingRects.getLength(), "Original rect should NOT be removed");

        Element remainingRect = (Element) remainingRects.item(0);
        assertEquals("#FFFF00", remainingRect.getAttribute("fill"), "Fill should be preserved");

        NodeList paths = doc.getElementsByTagName("path");
        assertEquals(0, paths.getLength(), "Should NOT generate an outline path");
        
        NodeList lines = doc.getElementsByTagName("line");
        assertEquals(0, lines.getLength(), "Should NOT generate hatch lines for 'none'");
    }
}
