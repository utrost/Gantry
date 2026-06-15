package org.trostheide.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.HatchStyle;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StrokeWidthProcessorTest {

    @Test
    void testStrokeWidthGlobalAndOverrides() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);
        
        Element rect1 = doc.createElement("rect");
        rect1.setAttribute("stroke", "#ff0000"); // will get override
        root.appendChild(rect1);
        
        Element rect2 = doc.createElement("rect");
        rect2.setAttribute("stroke", "#00ff00"); // will get global
        root.appendChild(rect2);
        
        Element rect3 = doc.createElement("rect");
        rect3.setAttribute("fill", "#0000ff"); // will get override
        root.appendChild(rect3);

        Config config = new Config(
                "in", "out", 1.0f, Collections.emptyList(), false,
                HatchStyle.of(45, 5), Collections.emptyMap(), 
                Map.of("#ff0000", 2.5f, "#0000ff", 3.0f), // overrides
                Collections.emptyList(), Collections.emptyList(), 0.0,
                1.0, "linear", 45.0, 5.0, 0.0, false, null, false, false, 0.378, false, 1.89, false, false, false
        );

        StrokeWidthProcessor processor = new StrokeWidthProcessor();
        processor.process(doc, config);

        assertEquals("2.5", rect1.getAttribute("stroke-width"));
        assertEquals("1.0", rect2.getAttribute("stroke-width"));
        assertEquals("3.0", rect3.getAttribute("stroke-width"));
    }
}
