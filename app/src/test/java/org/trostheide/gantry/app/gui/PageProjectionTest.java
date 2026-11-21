package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.app.session.*;
import org.trostheide.gantry.model.*;
import org.apache.batik.transcoder.*;
import org.apache.batik.transcoder.image.PNGTranscoder;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PageProjectionTest {
    @TempDir Path temporary;

    @Test void executableGcodeIsUnchangedFromTaggedRc12Across256Configurations() throws Exception {
        List<String> expected;
        try (var stream = getClass().getResourceAsStream("/page-projection-rc12-gcode.sha256")) {
            assertNotNull(stream);
            expected = new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
        List<String> actual = new ArrayList<>();
        for (var sample : PageProjectionCases.all())
            actual.add(PageProjectionCases.gcodeHash(sample.config(), temporary.resolve("job.gcode")) + "  " + sample.id());
        assertEquals(expected, actual, "Presentation changes must preserve every executable command");
    }

    @Test void actualSvgAndHeatmapRenderingMatchWorkspacePhysicalPage() throws Exception {
        var output = QuickArtworkGenerator.generate(new QuickArtwork(QuickArtwork.Kind.TEXT, "F", 0, 7));
        for (var sample : PageProjectionCases.all()) for (int version : List.of(1, 2)) {
            var c = sample.config();
            c.coordinateVersion = version;
            var settings = c.toPlotSettings();
            var service = new PlotService(null, settings);
            var projected = service.projectArtwork(output);
            String svg = SvgJobExporter.combined(projected, List.of(.3));
            BufferedImage svgImage = renderSvg(svg, projected.page().widthMm(), projected.page().heightMm());
            var coverage = InkCoverageAnalyzer.analyze(output, settings,
                    List.of(new InkCoverageAnalyzer.PenSpec("f", "F", .3)));
            BufferedImage heatmap = InkCoverageDialog.renderMap(coverage, -1, 2);
            VisualizationPanel[] workspace = {null};
            SwingUtilities.invokeAndWait(() -> {
                var panel = new VisualizationPanel();
                panel.setMachineSize(20, 12); panel.setOrientation(c.orientation);
                panel.setMachineOrigin(c.machineOrigin);
                panel.setEffectiveAxes(settings.swapXY, settings.invertX, settings.invertY);
                panel.setCanvasAlignment(c.canvasAlignment); panel.setDataRotation(c.dataRotation);
                panel.setFlipY(c.flipY); panel.loadFromOutput(output);
                workspace[0] = panel;
            });
            assertEquals((int) Math.round(workspace[0].displayWidth() * 20), svgImage.getWidth(), sample.id());
            assertEquals((int) Math.round(workspace[0].displayHeight() * 20), svgImage.getHeight(), sample.id());
            for (var stroke : projected.layers().get(0).strokes()) {
                for (int i = 1; i < stroke.size(); i++) {
                    Point a = stroke.get(i - 1), b = stroke.get(i);
                    for (double fraction : List.of(.25, .5, .75)) {
                        double x = a.x() + fraction * (b.x() - a.x());
                        double y = a.y() + fraction * (b.y() - a.y());
                        double[] page = workspace[0].physicalToScreen(x, y);
                        assertTrue(inkNear(svgImage, (int) (page[0] * 20), (int) (page[1] * 20), 2),
                                sample.id() + " SVG differs from workspace at " + Arrays.toString(page));
                        assertTrue(inkNear(heatmap, (int) (page[0] / coverage.cellMm()),
                                (int) (page[1] / coverage.cellMm()), 1),
                                sample.id() + " heatmap differs from workspace");
                    }
                }
            }
            // Exercise the loaded document paths, not only the motor-to-page helper.
            // A MOVE followed by DRAW endpoints includes its first drawn segment in the canvas.
            for (var path : workspace[0].allPaths) {
                for (int i = 1; i < path.size(); i++) {
                    double[] a = workspace[0].transformPoint(path.get(i - 1));
                    double[] b = workspace[0].transformPoint(path.get(i));
                    assertTrue(inkNear(svgImage, (int) ((a[0] + b[0]) * 10),
                            (int) ((a[1] + b[1]) * 10), 2), sample.id() + " loaded canvas path differs from SVG");
                }
            }
            // Every occupied heatmap cell must cover rendered SVG ink nearby as well:
            // the one-way check alone would miss a duplicated/mirrored image.
            for (int y = 0; y < heatmap.getHeight(); y++) for (int x = 0; x < heatmap.getWidth(); x++) {
                if ((heatmap.getRGB(x, y) & 0xffffff) != 0xffffff)
                    assertTrue(inkNear(svgImage, (int) ((x + .5) * coverage.cellMm() * 20),
                            (int) ((y + .5) * coverage.cellMm() * 20), 15), sample.id() + " extra heatmap ink");
            }
        }
    }

    @Test void pageProjectionIsImmutableAndStoredJobsRetainTheirPageAfterReload() throws Exception {
        var c = new GantryConfig();
        c.swapXY = true;
        var settings = c.toPlotSettings();
        var output = QuickArtworkGenerator.generate(new QuickArtwork(QuickArtwork.Kind.TEXT, "F", 0, 7));
        var artwork = new PlotService(null, settings).projectArtwork(output);
        String before = SvgJobExporter.combined(artwork, List.of(.3));
        var history = new PlotJobHistory(temporary.resolve("history.json").toFile());
        history.add(new PlotJobHistory.Job(java.time.Instant.EPOCH, output, settings));
        var stored = new PlotJobHistory(temporary.resolve("history.json").toFile()).latest();
        assertEquals(before, SvgJobExporter.combined(
                new PlotService(null, stored.settings()).projectArtwork(stored.output()), List.of(.3)));
        settings.swapXY = false; settings.originRight = false; settings.machineWidth = 999.0;
        assertEquals(before, SvgJobExporter.combined(artwork, List.of(.3)));
        var old = new PlotService.ProjectedArtwork(20, 12, List.of());
        assertArrayEquals(new double[]{2, 9}, old.page().point(2, 3));
    }

    private static BufferedImage renderSvg(String svg, double width, double height) throws Exception {
        PNGTranscoder renderer = new PNGTranscoder();
        renderer.addTranscodingHint(PNGTranscoder.KEY_WIDTH, (float) (width * 20));
        renderer.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, (float) (height * 20));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        renderer.transcode(new TranscoderInput(new StringReader(svg)), new TranscoderOutput(bytes));
        return ImageIO.read(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static boolean inkNear(BufferedImage image, int x, int y, int radius) {
        for (int row = Math.max(0, y - radius); row <= Math.min(image.getHeight() - 1, y + radius); row++)
            for (int col = Math.max(0, x - radius); col <= Math.min(image.getWidth() - 1, x + radius); col++) {
                int rgb = image.getRGB(col, row);
                if ((rgb >>> 24) != 0 && (rgb & 0xffffff) != 0xffffff) return true;
            }
        return false;
    }
}
