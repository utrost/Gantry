package org.trostheide.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.HatchStyle;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Color;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaletteProcessorTest {

    @Test
    void testQuantizesStrokeToNearestPaletteColor() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element line = doc.createElement("line");
        line.setAttribute("stroke", "#FF0000"); // Red
        root.appendChild(line);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .palette(List.of(Color.BLUE, Color.GREEN))
                .build();

        new PaletteProcessor().process(doc, config);

        // Red is perceptually closer to green than to blue in CIELAB
        String result = line.getAttribute("stroke");
        assertNotEquals("#ff0000", result, "Color should be quantized");
        // Should be mapped to one of the palette colors
        assertTrue(result.equals("#00ff00") || result.equals("#0000ff"),
                "Should be mapped to a palette color, got: " + result);
    }

    @Test
    void testQuantizesFillToNearestPaletteColor() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element rect = doc.createElement("rect");
        rect.setAttribute("fill", "#000000"); // Black
        root.appendChild(rect);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .palette(List.of(new Color(10, 10, 10), Color.WHITE))
                .build();

        new PaletteProcessor().process(doc, config);

        // Black should map to near-black (10,10,10)
        assertEquals("#0a0a0a", rect.getAttribute("fill"));
    }

    @Test
    void testSkipsWhenPaletteEmpty() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element line = doc.createElement("line");
        line.setAttribute("stroke", "#FF0000");
        root.appendChild(line);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .palette(Collections.emptyList())
                .build();

        new PaletteProcessor().process(doc, config);

        assertEquals("#FF0000", line.getAttribute("stroke"), "Color should not change with empty palette");
    }

    @Test
    void testSkipsNoneFill() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element rect = doc.createElement("rect");
        rect.setAttribute("fill", "none");
        root.appendChild(rect);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .palette(List.of(Color.RED))
                .build();

        new PaletteProcessor().process(doc, config);

        assertEquals("none", rect.getAttribute("fill"), "'none' fill should remain unchanged");
    }

    @Test
    void testQuantizesBothStrokeAndFill() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element rect = doc.createElement("rect");
        rect.setAttribute("stroke", "#FF0000");
        rect.setAttribute("fill", "#0000FF");
        root.appendChild(rect);

        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .palette(List.of(Color.BLACK))
                .build();

        new PaletteProcessor().process(doc, config);

        assertEquals("#000000", rect.getAttribute("stroke"), "Stroke should be quantized to black");
        assertEquals("#000000", rect.getAttribute("fill"), "Fill should be quantized to black");
    }
}
