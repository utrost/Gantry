package org.trostheide.gantry.app.plot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;
import org.trostheide.gantry.pipeline.svgimport.SvgImportStage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class SvgJobExporterTest {
    @TempDir Path temporary;

    @Test void combinedAndPackagePreserveMachineGeometryLayerOrderAndWidths() throws Exception {
        PlotSettings settings = new PlotSettings();
        settings.machineWidth = 100.0;
        settings.machineHeight = 80.0;
        settings.flipY = true;
        settings.alignmentOffsetOverride = new double[] {5, 10};
        ProcessorOutput output = new ProcessorOutput(
                new Metadata("test", Instant.EPOCH, "station", "mm", 0, Bounds.empty()),
                List.of(
                        new Layer("Red & first", "station", "#ff0000", List.of(
                                new MoveCommand(1, 10, 20),
                                new DrawCommand(2, List.of(new Point(10, 20), new Point(20, 30))))),
                        new Layer("Blue/second", "station", "#0000ff", List.of(
                                new MoveCommand(3, 30, 10),
                                new DrawCommand(4, List.of(new Point(30, 10), new Point(40, 10)))))));
        PlotService.ProjectedArtwork projected = new PlotService(null, settings).projectArtwork(output);
        Path combined = temporary.resolve("job.svg");
        SvgJobExporter.writeCombined(combined.toFile(), projected, List.of(0.5, 0.35));
        String svg = Files.readString(combined);
        assertTrue(svg.contains("width=\"100mm\" height=\"80mm\" viewBox=\"0 0 100 80\""));
        assertTrue(svg.contains("Red &amp; first"));
        assertTrue(svg.contains("stroke=\"#ff0000\" stroke-width=\"0.5\""));
        assertTrue(svg.contains("M 15 50 L 25 40"));
        assertTrue(svg.contains("M 35 60 L 45 60"));
        assertTrue(svg.indexOf("Red &amp; first") < svg.indexOf("Blue/second"));

        SvgImportOptions options = new SvgImportOptions(0, "station", 0.1,
                0, 0, true, 0, 0, false, true);
        ProcessorOutput imported = SvgImportStage.importSvg(combined.toFile(), options);
        assertEquals(2, imported.layers().size());
        assertEquals(List.of("#ff0000", "#0000ff"),
                imported.layers().stream().map(Layer::color).toList());
        List<Point> firstStroke = imported.layers().get(0).commands().stream()
                .filter(DrawCommand.class::isInstance).map(DrawCommand.class::cast)
                .findFirst().orElseThrow().points;
        assertEquals(15, firstStroke.get(0).x(), 1e-6);
        assertEquals(50, firstStroke.get(0).y(), 1e-6);
        assertEquals(25, firstStroke.get(1).x(), 1e-6);
        assertEquals(40, firstStroke.get(1).y(), 1e-6);

        Path packageFile = temporary.resolve("job.zip");
        SvgJobExporter.writePackage(packageFile.toFile(), projected, List.of(0.5, 0.35));
        try (ZipFile zip = new ZipFile(packageFile.toFile())) {
            assertNotNull(zip.getEntry("combined.svg"));
            assertNotNull(zip.getEntry("layers/01-red-first.svg"));
            assertNotNull(zip.getEntry("layers/02-blue-second.svg"));
            assertNotNull(zip.getEntry("README.txt"));
            assertEquals(4, zip.size());
        }
    }

    @Test void failedPackageLeavesNoPartialDestination() {
        Layer layer = new Layer("bad", "station", List.of());
        PlotService.ProjectedArtwork invalid = new PlotService.ProjectedArtwork(100, 80,
                List.of(new PlotService.ProjectedLayer(layer,
                        List.of(List.of(new Point(Double.NaN, 10))))));
        Path destination = temporary.resolve("bad.zip");
        assertThrows(IllegalArgumentException.class,
                () -> SvgJobExporter.writePackage(destination.toFile(), invalid, List.of()));
        assertFalse(Files.exists(destination));
    }

    @Test void projectedExportClampsLikePlotAndMarksUnknownWidth() {
        PlotSettings settings = new PlotSettings();
        settings.machineWidth = 100.0;
        settings.machineHeight = 80.0;
        settings.alignmentOffsetOverride = new double[] {0, 0};
        ProcessorOutput output = new ProcessorOutput(
                new Metadata("test", Instant.EPOCH, "station", "mm", 0, Bounds.empty()),
                List.of(new Layer("edge", "station", "#000000", List.of(
                        new MoveCommand(1, -10, 5),
                        new DrawCommand(2, List.of(new Point(-10, 5), new Point(150, 5)))))));
        PlotService.ProjectedArtwork projected = new PlotService(null, settings).projectArtwork(output);
        String svg = SvgJobExporter.combined(projected, List.of());
        assertTrue(svg.contains("M 0 5 L 100 5"));
        assertTrue(svg.contains("data-gantry-pen-width-assumed=\"true\""));
        assertFalse(svg.contains("data-gantry-pen-width-mm="));
    }
}
