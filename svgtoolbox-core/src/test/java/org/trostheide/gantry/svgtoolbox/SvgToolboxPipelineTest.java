package org.trostheide.gantry.svgtoolbox;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.svgtoolbox.processors.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SvgToolboxPipelineTest {

    @Test
    void testPipelineOrder() {
        Config config = new Config.Builder().inputPath("in").outputPath("out").build();
        List<Processor> pipeline = SvgToolboxPipeline.buildPipeline(config);

        assertEquals(13, pipeline.size(), "Base pipeline should have 13 processors");
        assertInstanceOf(VisibilityProcessor.class, pipeline.get(0));
        assertInstanceOf(StyleNormalizerProcessor.class, pipeline.get(1));
        assertInstanceOf(RotateProcessor.class, pipeline.get(2));
        assertInstanceOf(StrokeWidthProcessor.class, pipeline.get(3));
        assertInstanceOf(PaletteProcessor.class, pipeline.get(4));
        assertInstanceOf(SimplifyProcessor.class, pipeline.get(5));
        assertInstanceOf(HatchProcessor.class, pipeline.get(6));
        assertInstanceOf(LinesimplifyProcessor.class, pipeline.get(7));
        assertInstanceOf(LinemergeProcessor.class, pipeline.get(8));
        assertInstanceOf(LinesortProcessor.class, pipeline.get(9));
        assertInstanceOf(ReloopProcessor.class, pipeline.get(10));
        assertInstanceOf(LayerProcessor.class, pipeline.get(11));
        assertInstanceOf(CropProcessor.class, pipeline.get(12));
    }

    @Test
    void testPathOptimizeAddedConditionally() {
        Config config = new Config.Builder().inputPath("in").outputPath("out")
                .optimizePaths(true)
                .build();
        List<Processor> pipeline = SvgToolboxPipeline.buildPipeline(config);

        assertEquals(14, pipeline.size());
        assertInstanceOf(PathOptimizeProcessor.class, pipeline.get(13), "PathOptimizeProcessor should be last");
    }

    @Test
    void testProcessRunsAllStagesAndOrganizesIntoLayers() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElementNS("http://www.w3.org/2000/svg", "svg");
        root.setAttribute("width", "100");
        root.setAttribute("height", "100");
        doc.appendChild(root);

        Element line = doc.createElement("line");
        line.setAttribute("stroke", "#ff0000");
        line.setAttribute("x1", "0");
        line.setAttribute("y1", "0");
        line.setAttribute("x2", "10");
        line.setAttribute("y2", "10");
        root.appendChild(line);

        Config config = new Config.Builder().inputPath("in").outputPath("out").build();

        SvgToolboxPipeline.process(doc, config);

        NodeList groups = root.getElementsByTagName("g");
        boolean hasLayer = false;
        for (int i = 0; i < groups.getLength(); i++) {
            Element g = (Element) groups.item(i);
            if ("layer".equals(g.getAttributeNS("http://www.inkscape.org/namespaces/inkscape", "groupmode"))) {
                hasLayer = true;
            }
        }
        assertTrue(hasLayer, "Pipeline should organize shapes into an Inkscape layer group");
    }

    @Test
    void testProgressCallbackInvokedForEachStage() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElementNS("http://www.w3.org/2000/svg", "svg");
        doc.appendChild(root);

        Config config = new Config.Builder().inputPath("in").outputPath("out").build();

        int[] calls = {0};
        SvgToolboxPipeline.process(doc, config, (step, total, name) -> {
            calls[0]++;
            assertEquals(13, total);
            assertTrue(step >= 1 && step <= total);
            assertNotNull(name);
        });

        assertEquals(13, calls[0]);
    }
}
