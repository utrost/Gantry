package org.trostheide.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.HatchStyle;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class SimplifyProcessorTest {

    @Test
    void testSimplifyCollinear() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element polygon = doc.createElement("polygon");
        // (0,0) -> (5,5) -> (10,10). Middle point is redundant.
        polygon.setAttribute("points", "0,0 5,5 10,10");
        doc.appendChild(polygon);

        Config config = new Config(
                "in", "out", 1.0f, Collections.emptyList(), false,
                HatchStyle.of(45, 5), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), 0.0,
                1.0, "linear", 45.0, 5.0, 0.0, false, null, false, false, 0.378, false, 1.89, false, false, false
        );

        SimplifyProcessor processor = new SimplifyProcessor();
        processor.process(doc, config);

        String points = polygon.getAttribute("points");
        // Should be just start and end.
        // Expecting formatted string. The format is "%.2f,%.2f ".

        // Simple assertion: count spaces or check contents
        assertNotNull(points);
        // "0.00,0.00 10.00,10.00"

        // Check that 5,5 is NOT present
        assertFalse(points.contains("5.00,5.00") || points.contains("5.0,5.0") || points.contains("5,5"),
                "Middle point should be removed. Result: " + points);

        // Check that endpoints are present
        assertTrue(points.contains("0.00,0.00"), "Start point should be present");
        assertTrue(points.contains("10.00,10.00"), "End point should be present");
    }

    @Test
    void testNoChangeIfSignificant() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element polygon = doc.createElement("polygon");
        // (0,0) -> (5,50) -> (10,0). Very tall spike. Should be kept.
        polygon.setAttribute("points", "0,0 5,50 10,0");
        doc.appendChild(polygon);

        Config config = new Config(
                "in", "out", 1.0f, Collections.emptyList(), false,
                HatchStyle.of(45, 5), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), 0.0,
                1.0, "linear", 45.0, 5.0, 0.0, false, null, false, false, 0.378, false, 1.89, false, false, false
        );

        SimplifyProcessor processor = new SimplifyProcessor();
        processor.process(doc, config);

        String points = polygon.getAttribute("points");

        // Should contain all three
        assertTrue(points.contains("50.00"), "Spike point should be preserved");
    }
}
