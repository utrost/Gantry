package org.trostheide.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

class StyleNormalizerProcessorTest {

    @Test
    void testExtractsFill() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element rect = doc.createElement("rect");
        rect.setAttribute("style", "fill: #ff0000; stroke: none; stroke-width: 2px");
        doc.appendChild(rect);

        StyleNormalizerProcessor p = new StyleNormalizerProcessor();
        p.process(doc, null); // Config not needed

        assertEquals("#ff0000", rect.getAttribute("fill"));
        assertEquals("none", rect.getAttribute("stroke"));
        assertEquals("2px", rect.getAttribute("stroke-width"));
    }

    @Test
    void testIgnoresIrrelevantStyles() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element rect = doc.createElement("rect");
        rect.setAttribute("style", "cursor: pointer; display: block");
        doc.appendChild(rect);

        StyleNormalizerProcessor p = new StyleNormalizerProcessor();
        p.process(doc, null);

        assertFalse(rect.hasAttribute("cursor"));
        assertFalse(rect.hasAttribute("display"));
    }
}
