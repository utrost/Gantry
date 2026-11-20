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
import org.trostheide.gantry.app.session.DocumentSession;

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
        settings.originBottom = true; // This fixture supplies canonical Y-up motor coordinates.
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

    @Test void preparedSelectionKeepsEmptyLayersAndExcludesUnselectedGeometry() throws Exception {
        DocumentSession session = new DocumentSession();
        session.replace(new ProcessorOutput(
                new Metadata("selection", Instant.EPOCH, "station", "mm", 0, Bounds.empty()),
                List.of(
                        new Layer("Excluded", "station", "#ff0000", List.of(
                                new MoveCommand(1, 90, 90),
                                new DrawCommand(2, List.of(new Point(99, 99))))),
                        new Layer("Empty / pen", "station", "#00ff00", List.of()),
                        new Layer("Included", "station", "#0000ff", List.of(
                                new MoveCommand(3, 10, 20),
                                new DrawCommand(4, List.of(new Point(30, 40))))))));
        session.selectLayers(List.of(2, 1)); // Preparation retains document order, not click order.
        ProcessorOutput prepared = session.prepareOutput(output -> output, 2);
        assertEquals(List.of("Empty / pen", "Included"), prepared.layers().stream().map(Layer::id).toList());
        PlotSettings settings = new PlotSettings();
        settings.alignmentOffsetOverride = new double[] {0, 0};
        var projected = new PlotService(null, settings).projectArtwork(prepared);
        assertTrue(projected.layers().get(0).strokes().isEmpty());
        assertEquals(2, projected.layers().get(1).strokes().size(), "both prepared passes exported");
        Path destination = temporary.resolve("selected.zip");
        SvgJobExporter.writePackage(destination.toFile(), projected, List.of(.2, .7),
                List.of("Empty pen", "Drawing pen"));
        try (ZipFile zip = new ZipFile(destination.toFile())) {
            assertEquals(List.of("combined.svg", "layers/01-empty-pen.svg", "layers/02-included.svg", "README.txt"),
                    zip.stream().map(entry -> entry.getName()).toList());
            String combined = new String(zip.getInputStream(zip.getEntry("combined.svg")).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(combined.contains("Excluded"));
            assertFalse(combined.contains("99"));
            assertTrue(combined.indexOf("Empty / pen") < combined.indexOf("Included"));
            assertEquals(2, combined.split("<path ", -1).length - 1);
            String empty = new String(zip.getInputStream(zip.getEntry("layers/01-empty-pen.svg")).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(empty.contains("<path "));
            assertTrue(empty.contains("data-gantry-order=\"1\""));
            assertTrue(empty.contains("data-gantry-pen-name=\"Empty pen\""));
            String drawing = new String(zip.getInputStream(zip.getEntry("layers/02-included.svg")).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(drawing.contains("data-gantry-order=\"2\""));
            assertTrue(drawing.contains("data-gantry-pen-width-mm=\"0.7\""));
        }
        session.selectLayers(List.of());
        assertTrue(session.prepareOutput(output -> output, 2).layers().isEmpty(),
                "empty selection cannot fall back to all layers");
        session.selectLayers(List.of(1));
        assertTrue(new PlotService(null, settings).projectArtwork(session.prepareOutput(output -> output, 1))
                .layers().get(0).strokes().isEmpty(), "empty-only selection has no drawing paths");
    }

    @Test void failedOverwritePreservesBothFormatsAndRemovesStagingFiles() throws Exception {
        var invalid = new PlotService.ProjectedArtwork(100, 80,
                List.of(new PlotService.ProjectedLayer(new Layer("bad", "station", List.of()),
                        List.of(List.of(new Point(Double.NaN, 10))))));
        for (String extension : List.of("svg", "zip")) {
            Path destination = temporary.resolve("existing." + extension);
            Files.writeString(destination, "original contents");
            assertThrows(IllegalArgumentException.class, () -> {
                if (extension.equals("svg")) SvgJobExporter.writeCombined(destination.toFile(), invalid, List.of());
                else SvgJobExporter.writePackage(destination.toFile(), invalid, List.of());
            });
            assertEquals("original contents", Files.readString(destination));
        }
        try (var files = Files.list(temporary)) {
            assertEquals(List.of("existing.svg", "existing.zip"),
                    files.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

    @Test void storedJobExportUsesItsPenSnapshotAfterReload() throws Exception {
        ProcessorOutput output = new ProcessorOutput(
                new Metadata("test", Instant.EPOCH, "station", "mm", 0, Bounds.empty()),
                List.of(new Layer("artwork", "station", "#ff0000", List.of(
                        new MoveCommand(1, 10, 20), new DrawCommand(2, List.of(new Point(20, 30)))))));
        var historyFile = temporary.resolve("history.json").toFile();
        var history = new PlotJobHistory(historyFile);
        history.add(new PlotJobHistory.Job(Instant.EPOCH, output, new PlotSettings(),
                List.of(new PlotJobHistory.PenSnapshot("pen-1", "Fine & red", "#ff0000", .7))));
        var stored = new PlotJobHistory(historyFile).latest();
        var destination = temporary.resolve("stored.svg");
        SvgJobExporter.writeCombined(destination.toFile(),
                new PlotService(null, stored.settings()).projectArtwork(stored.output()),
                stored.pens().stream().map(PlotJobHistory.PenSnapshot::widthMm).toList(),
                stored.pens().stream().map(PlotJobHistory.PenSnapshot::name).toList());
        String svg = Files.readString(destination);
        assertTrue(svg.contains("data-gantry-pen-name=\"Fine &amp; red\""));
        assertTrue(svg.contains("data-gantry-pen-width-mm=\"0.7\""));
        assertFalse(svg.contains("data-gantry-pen-width-assumed"));
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
    @Test void metadataRetainsUnicodeWhitespaceAndXmlEscapingInBothFormats() throws Exception {
        String name = "Fine & \"red\" <pen> '  café \uD83D\uDD8A\tline\nnext\rrow";
        var artwork = new PlotService.ProjectedArtwork(20, 12,
                List.of(new PlotService.ProjectedLayer(new Layer(name, "station", List.of()), List.of())));
        Path combined = temporary.resolve("names.svg"), packaged = temporary.resolve("names.zip");
        SvgJobExporter.writeCombined(combined.toFile(), artwork, List.of(.3), List.of(name));
        SvgJobExporter.writePackage(packaged.toFile(), artwork, List.of(.3), List.of(name));
        List<byte[]> documents = new java.util.ArrayList<>();
        documents.add(Files.readAllBytes(combined));
        try (ZipFile zip = new ZipFile(packaged.toFile())) {
            for (var entry : java.util.Collections.list(zip.entries()))
                if (entry.getName().endsWith(".svg")) documents.add(zip.getInputStream(entry).readAllBytes());
        }
        assertEquals(3, documents.size());
        var parser = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        parser.setNamespaceAware(true);
        for (byte[] bytes : documents) {
            var xml = parser.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(bytes));
            var group = (org.w3c.dom.Element) xml.getElementsByTagNameNS("http://www.w3.org/2000/svg", "g").item(0);
            assertEquals(name, group.getAttributeNS("http://www.inkscape.org/namespaces/inkscape", "label"));
            assertEquals(name, group.getAttribute("data-gantry-pen-name"));
        }
    }

    @Test void invalidMetadataCannotReplaceEitherExportAndLeavesNoStagingFiles() throws Exception {
        for (String name : List.of("bad\u0001name", "bad\uD800name", "bad\uDC00name", "bad\uFFFFname")) {
            for (boolean invalidPen : List.of(false, true)) {
                var artwork = new PlotService.ProjectedArtwork(20, 12,
                        List.of(new PlotService.ProjectedLayer(
                                new Layer(invalidPen ? "valid" : name, "station", List.of()), List.of())));
                for (String extension : List.of("svg", "zip")) {
                    Path destination = temporary.resolve("existing." + extension);
                    Files.writeString(destination, "keep valid previous output");
                    var error = assertThrows(IllegalArgumentException.class, () -> {
                        if (extension.equals("svg")) SvgJobExporter.writeCombined(destination.toFile(), artwork,
                                List.of(.3), List.of(invalidPen ? name : "valid"));
                        else SvgJobExporter.writePackage(destination.toFile(), artwork,
                                List.of(.3), List.of(invalidPen ? name : "valid"));
                    });
                    assertTrue(error.getMessage().contains("Rename the layer or pen"));
                    assertEquals("keep valid previous output", Files.readString(destination));
                }
            }
        }
        try (var paths = Files.list(temporary)) {
            assertEquals(List.of("existing.svg", "existing.zip"),
                    paths.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

}
