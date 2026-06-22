package org.trostheide.gantry.pipeline.svgimport;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.RefillCommand;

import java.awt.geom.AffineTransform;
import java.io.File;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SvgImportStageTest {

    private static File resource(String name) {
        URL url = SvgImportStageTest.class.getClassLoader().getResource(name);
        assertNotNull(url, "Test resource not found: " + name);
        return new File(url.getFile());
    }

    @Nested
    class DistanceAndInterpolate {
        @Test
        void distanceDiagonal345() {
            assertEquals(5.0, SvgImportStage.distance(new Point(0, 0), new Point(3, 4)), 1e-9);
        }

        @Test
        void interpolateAtZeroReturnsStart() {
            Point result = SvgImportStage.interpolate(new Point(0, 0), new Point(10, 0), 0, 10);
            assertEquals(0.0, result.x(), 1e-9);
        }

        @Test
        void interpolateAtFullDistReturnsEnd() {
            Point result = SvgImportStage.interpolate(new Point(0, 0), new Point(10, 0), 10, 10);
            assertEquals(10.0, result.x(), 1e-9);
        }

        @Test
        void interpolateWithZeroTotalDistReturnsStart() {
            Point result = SvgImportStage.interpolate(new Point(3, 4), new Point(3, 4), 0, 0);
            assertEquals(3.0, result.x(), 1e-9);
            assertEquals(4.0, result.y(), 1e-9);
        }
    }

    @Nested
    class PaperFormatTests {
        @Test
        void fromStringA4() {
            PaperFormat format = PaperFormat.fromString("A4");
            assertNotNull(format);
            assertEquals(210, format.width());
            assertEquals(297, format.height());
        }

        @Test
        void fromStringCustom() {
            PaperFormat format = PaperFormat.fromString("300x400");
            assertNotNull(format);
            assertEquals(300, format.width());
            assertEquals(400, format.height());
        }

        @Test
        void fromStringCaseInsensitive() {
            assertNotNull(PaperFormat.fromString("a4"));
            assertNotNull(PaperFormat.fromString("xl"));
        }

        @Test
        void fromStringNullOrUnknown() {
            assertNull(PaperFormat.fromString(null));
            assertNull(PaperFormat.fromString("B5"));
        }
    }

    @Nested
    class FitToPageTransform {
        @Test
        void identityWhenBoundsAreEmpty() {
            AffineTransform tx = SvgImportStage.calculateFitToPageTransform(Bounds.empty(), PaperFormat.A4, 10.0);
            assertTrue(tx.isIdentity());
        }

        @Test
        void fitToPageRespectsAspectRatio() {
            Bounds content = new Bounds(0, 0, 200, 50);
            AffineTransform tx = SvgImportStage.calculateFitToPageTransform(content, PaperFormat.A4, 10.0);

            double[] dstTL = new double[2];
            double[] dstBR = new double[2];
            tx.transform(new double[] {0, 0}, 0, dstTL, 0, 1);
            tx.transform(new double[] {200, 50}, 0, dstBR, 0, 1);

            double w = dstBR[0] - dstTL[0];
            double h = dstBR[1] - dstTL[1];
            assertEquals(4.0, w / h, 0.01);
        }

        @Test
        void excessivePaddingReturnsIdentity() {
            Bounds content = new Bounds(0, 0, 100, 100);
            AffineTransform tx = SvgImportStage.calculateFitToPageTransform(content, PaperFormat.A5, 200.0);
            assertTrue(tx.isIdentity());
        }
    }

    @Nested
    class ImportSvgTests {
        @Test
        void simpleSvgProducesValidOutput() throws Exception {
            ProcessorOutput result = SvgImportStage.importSvg(resource("test_simple.svg"),
                    new SvgImportOptions(500, "default_station", 0.5, 0, 0, true, 0, 0, false));

            assertNotNull(result.metadata());
            assertEquals("mm", result.metadata().units());
            assertEquals("test_simple.svg", result.metadata().source());
            assertFalse(result.layers().isEmpty());
            assertTrue(result.metadata().totalCommands() > 0);
        }

        @Test
        void layeredSvgProducesMultipleLayersWithGenericStationIds() throws Exception {
            ProcessorOutput result = SvgImportStage.importSvg(resource("test_layers.svg"),
                    new SvgImportOptions(500, "default_station", 0.5, 0, 0, true, 0, 0, false));

            assertEquals(2, result.layers().size());
            assertEquals("Layer1", result.layers().get(0).stationId());
            assertEquals("Layer2", result.layers().get(1).stationId());
        }

        @Test
        void fullPageBackgroundRectIsDropped() throws Exception {
            String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"100\" "
                    + "viewBox=\"0 0 100 100\">"
                    + "<rect x=\"0\" y=\"0\" width=\"100\" height=\"100\"/>"
                    + "<path d=\"M40 40 L60 40\"/>"
                    + "</svg>";
            File temp = File.createTempFile("pagerect", ".svg");
            temp.deleteOnExit();
            java.nio.file.Files.writeString(temp.toPath(), svg);

            ProcessorOutput result = SvgImportStage.importSvg(temp,
                    new SvgImportOptions(0, "default_station", 0.5, 0, 0, true, 0, 0, false));

            long drawCount = result.layers().stream()
                    .flatMap(l -> l.commands().stream())
                    .filter(c -> c instanceof org.trostheide.gantry.model.command.DrawCommand)
                    .count();
            assertEquals(1, drawCount, "the full-page rect should be dropped, leaving only the inner stroke");

            // Bounds should hug the inner stroke (x in [40,60]), not the dropped 0..100 page rect.
            Bounds bounds = result.metadata().bounds();
            assertTrue(bounds.minX() >= 40 - 1e-6, "minX should reflect inner content, got " + bounds.minX());
            assertTrue(bounds.maxX() <= 60 + 1e-6, "maxX should reflect inner content, got " + bounds.maxX());
        }

        @Test
        void singleShapeSvgIsNotDroppedAsPageBorder() throws Exception {
            // A document whose only drawable is one rect must keep that rect: the
            // page-border heuristic only makes sense when real content coexists
            // with a separate background/frame rect, not when the rect IS the content.
            String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"100\" "
                    + "viewBox=\"0 0 100 100\">"
                    + "<rect x=\"0\" y=\"0\" width=\"100\" height=\"100\" fill=\"#ff0000\"/>"
                    + "</svg>";
            File temp = File.createTempFile("singlerect", ".svg");
            temp.deleteOnExit();
            java.nio.file.Files.writeString(temp.toPath(), svg);

            ProcessorOutput result = SvgImportStage.importSvg(temp,
                    new SvgImportOptions(0, "default_station", 0.5, 0, 0, true, 0, 0, false));

            long drawCount = result.layers().stream()
                    .flatMap(l -> l.commands().stream())
                    .filter(c -> c instanceof org.trostheide.gantry.model.command.DrawCommand)
                    .count();
            assertTrue(drawCount > 0, "the rect is the only content and must not be dropped");
        }

        @Test
        void layerColourIsReadFromStrokeStyleAndAttribute() throws Exception {
            String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" "
                    + "xmlns:inkscape=\"http://www.inkscape.org/namespaces/inkscape\" "
                    + "width=\"100\" height=\"100\" viewBox=\"0 0 100 100\">"
                    + "<g inkscape:groupmode=\"layer\" inkscape:label=\"Reds\">"
                    + "<path d=\"M10 10 L20 20\" style=\"stroke:#ff0000;fill:none\"/></g>"
                    + "<g inkscape:groupmode=\"layer\" inkscape:label=\"Blues\">"
                    + "<path d=\"M30 30 L40 40\" stroke=\"rgb(0,0,255)\"/></g>"
                    + "</svg>";
            File temp = File.createTempFile("colours", ".svg");
            temp.deleteOnExit();
            java.nio.file.Files.writeString(temp.toPath(), svg);

            ProcessorOutput result = SvgImportStage.importSvg(temp,
                    new SvgImportOptions(0, "default_station", 0.5, 0, 0, true, 0, 0, false));

            assertEquals(2, result.layers().size());
            assertEquals("#ff0000", result.layers().get(0).color());
            assertEquals("#0000ff", result.layers().get(1).color());
        }

        @Test
        void fitToFormatScalesAgainstContentNotPageBorderRect() throws Exception {
            // A full-page border rect (dropped on output) must not be counted as "content" when
            // sizing the autoscale-to-format transform, or real content gets shrunk as if it
            // already filled the page.
            String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"100\" "
                    + "viewBox=\"0 0 100 100\">"
                    + "<rect x=\"0\" y=\"0\" width=\"100\" height=\"100\"/>"
                    + "<path d=\"M40 40 L60 40 L60 60 L40 60 Z\"/>"
                    + "</svg>";
            File temp = File.createTempFile("pageborder", ".svg");
            temp.deleteOnExit();
            java.nio.file.Files.writeString(temp.toPath(), svg);

            ProcessorOutput result = SvgImportStage.importSvg(temp,
                    SvgImportOptions.fitToFormat(0, "default_station", 0.5, PaperFormat.A4, 0.0, false));

            Bounds bounds = result.metadata().bounds();
            double width = bounds.maxX() - bounds.minX();
            // The 20x20 inner square should be scaled up to fill the A4 width (210mm), not shrunk
            // down as if it were already page-sized.
            assertTrue(width > 200, "expected content scaled to fill the page, width was " + width);
        }

        @Test
        void fitToA4KeepsBoundsWithinPage() throws Exception {
            ProcessorOutput result = SvgImportStage.importSvg(resource("test_simple.svg"),
                    SvgImportOptions.fitToFormat(500, "default_station", 0.5, PaperFormat.A4, 10.0, false));

            Bounds bounds = result.metadata().bounds();
            assertTrue(bounds.minX() >= -1e-6);
            assertTrue(bounds.minY() >= -1e-6);
            assertTrue(bounds.maxX() <= 210 + 1e-6);
            assertTrue(bounds.maxY() <= 297 + 1e-6);
        }

        @Test
        void noRefillModeOmitsRefillCommands() throws Exception {
            ProcessorOutput result = SvgImportStage.importSvg(resource("test_simple.svg"),
                    new SvgImportOptions(0, "default_station", 0.5, 0, 0, true, 0, 0, false));

            long refillCount = result.layers().stream()
                    .flatMap(l -> l.commands().stream())
                    .filter(c -> c instanceof RefillCommand)
                    .count();
            assertEquals(0, refillCount);
        }

        @Test
        void eachLayerStartsWithRefillWhenMaxDistancePositive() throws Exception {
            ProcessorOutput result = SvgImportStage.importSvg(resource("test_layers.svg"),
                    new SvgImportOptions(500, "default_station", 0.5, 0, 0, true, 0, 0, false));

            for (var layer : result.layers()) {
                assertFalse(layer.commands().isEmpty());
                assertInstanceOf(RefillCommand.class, layer.commands().get(0));
            }
        }

        @Test
        void refillInsertedWhenExceedingMaxDistance() throws Exception {
            ProcessorOutput result = SvgImportStage.importSvg(resource("test_simple.svg"),
                    new SvgImportOptions(5.0, "default_station", 0.5, 0, 0, true, 0, 0, false));

            long refillCount = result.layers().get(0).commands().stream()
                    .filter(c -> c instanceof RefillCommand)
                    .count();
            assertTrue(refillCount > 1, "Expected multiple refills, got " + refillCount);
        }

        @Test
        void commandIdsAreSequential() throws Exception {
            ProcessorOutput result = SvgImportStage.importSvg(resource("test_simple.svg"),
                    new SvgImportOptions(500, "default_station", 0.5, 0, 0, true, 0, 0, false));

            int lastId = 0;
            for (var layer : result.layers()) {
                for (Command cmd : layer.commands()) {
                    assertTrue(cmd.getId() > lastId);
                    lastId = cmd.getId();
                }
            }
        }

        @Test
        void transformedElementBoundsAreTranslated() throws Exception {
            ProcessorOutput result = SvgImportStage.importSvg(resource("test_transform.svg"),
                    new SvgImportOptions(500, "default_station", 0.5, 0, 0, true, 0, 0, false));

            Bounds bounds = result.metadata().bounds();
            assertTrue(bounds.minX() >= 55, "minX should be ~60, was " + bounds.minX());
            assertTrue(bounds.minY() >= 55, "minY should be ~60, was " + bounds.minY());
        }

        @Test
        void nestedTransformAccumulatesParent() throws Exception {
            ProcessorOutput result = SvgImportStage.importSvg(resource("test_nested_transform.svg"),
                    new SvgImportOptions(500, "default_station", 0.5, 0, 0, true, 0, 0, false));

            Bounds bounds = result.metadata().bounds();
            assertTrue(bounds.minX() >= 105, "minX should be ~110, was " + bounds.minX());
            assertTrue(bounds.minY() >= 105, "minY should be ~110, was " + bounds.minY());
            assertTrue(bounds.maxX() <= 125, "maxX should be ~120, was " + bounds.maxX());
            assertTrue(bounds.maxY() <= 125, "maxY should be ~120, was " + bounds.maxY());
        }

        @Test
        void mirrorPreservesLayerAndCommandCounts() throws Exception {
            ProcessorOutput normal = SvgImportStage.importSvg(resource("test_simple.svg"),
                    SvgImportOptions.fitToFormat(500, "default_station", 0.5, PaperFormat.A4, 10.0, false));
            ProcessorOutput mirrored = SvgImportStage.importSvg(resource("test_simple.svg"),
                    SvgImportOptions.fitToFormat(500, "default_station", 0.5, PaperFormat.A4, 10.0, true));

            assertEquals(normal.layers().size(), mirrored.layers().size());
            assertEquals(normal.metadata().totalCommands(), mirrored.metadata().totalCommands());
        }

        @Test
        void toolboxPipelineRunsBeforeImport() throws Exception {
            org.trostheide.gantry.svgtoolbox.Config toolboxConfig =
                    new org.trostheide.gantry.svgtoolbox.Config.Builder()
                            .inputPath("in").outputPath("out")
                            .build();

            ProcessorOutput result = SvgImportStage.importSvg(resource("test_simple.svg"), toolboxConfig,
                    new SvgImportOptions(500, "default_station", 0.5, 0, 0, true, 0, 0, false));

            assertNotNull(result.metadata());
            assertEquals("test_simple.svg", result.metadata().source());
            assertFalse(result.layers().isEmpty());
            assertTrue(result.metadata().totalCommands() > 0);
        }
    }
}
