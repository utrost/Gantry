package org.trostheide.gantry.app.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.app.plot.PlotService;
import org.trostheide.gantry.app.plot.PlotSettings;
import org.trostheide.gantry.plotter.GcodeFileBackend;
import org.trostheide.gantry.plotter.GcodeOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuickArtworkTest {
    @TempDir Path temp;

    @Test void primitivesUsePhysicalMillimetresAndClosedShapes() {
        var line = QuickArtworkGenerator.generate(new QuickArtwork(QuickArtwork.Kind.LINE, "", 20, 10));
        assertEquals(new Bounds(0, 0, 20, 10), line.metadata().bounds());
        assertEquals(new Point(20, 10), ((DrawCommand) line.layers().get(0).commands().get(1)).points.get(0));

        var rectangle = QuickArtworkGenerator.generate(new QuickArtwork(QuickArtwork.Kind.RECTANGLE, "", 30, 12));
        var rectDraw = (DrawCommand) rectangle.layers().get(0).commands().get(1);
        assertEquals(new Point(0, 0), rectDraw.points.get(rectDraw.points.size() - 1));

        var ellipse = QuickArtworkGenerator.generate(new QuickArtwork(QuickArtwork.Kind.ELLIPSE, "", 30, 12));
        var ellipseDraw = (DrawCommand) ellipse.layers().get(0).commands().get(1);
        assertEquals(64, ellipseDraw.points.size());
        assertEquals(new Bounds(0, 0, 30, 12), ellipse.metadata().bounds());
    }

    @Test void bundledTextIsStrokeOnlyAndRejectsMissingGlyphs() {
        QuickArtwork source = new QuickArtwork(QuickArtwork.Kind.TEXT, "A 42", 0, 7);
        var text = QuickArtworkGenerator.generate(source);
        assertEquals(new Bounds(0, 0, 23, 7), text.metadata().bounds());
        assertTrue(text.layers().get(0).commands().stream().allMatch(c -> c instanceof MoveCommand || c instanceof DrawCommand));
        assertThrows(IllegalArgumentException.class, () -> QuickArtworkGenerator.generate(
                new QuickArtwork(QuickArtwork.Kind.TEXT, "A♥", 0, 7)));
    }

    @Test void labelCanBeReopenedEditedAndExportedWithoutAnInstalledFont() throws Exception {
        DocumentSession created = new DocumentSession();
        QuickArtwork first = new QuickArtwork(QuickArtwork.Kind.TEXT, "Max", 0, 10);
        created.appendQuickArtwork(first);
        assertEquals(1, created.artworks().size());
        assertEquals(first, created.artworks().get(0).quickArtwork());
        assertEquals(170.0 / 7, created.currentOutput().metadata().bounds().maxX(), 1e-9);
        assertEquals(10, created.currentOutput().metadata().bounds().maxY());

        GantryProject project = new GantryProject(1, created.currentOutput(), created.selectedLayerIndices(),
                GantryProject.Placement.identity(), 1, GantryProject.Source.empty(), created.artworks(),
                created.penWidthsMm(), created.penLibrary());
        var file = temp.resolve("label.gantry").toFile();
        GantryProjectIO.save(project, file);
        GantryProject reopened = GantryProjectIO.load(file);
        DocumentSession editing = new DocumentSession();
        editing.restore(reopened.output(), reopened.selectedLayers(), reopened.artworks());
        assertEquals(first, editing.artworks().get(0).quickArtwork());

        QuickArtwork edited = new QuickArtwork(QuickArtwork.Kind.TEXT, "Max Mustermann", 0, 10);
        editing.editQuickArtwork(editing.artworks().get(0).id(), edited);
        assertEquals(edited, editing.artworks().get(0).quickArtwork());
        assertEquals(830.0 / 7, editing.currentOutput().metadata().bounds().maxX(), 1e-9);
        assertEquals(10, editing.currentOutput().metadata().bounds().maxY());

        var gcode = temp.resolve("label.gcode").toFile();
        GcodeOptions options = new GcodeOptions();
        options.penDownDelayMillis = 0;
        GcodeFileBackend backend = new GcodeFileBackend(options, gcode);
        assertTrue(backend.connect());
        new PlotService(backend, new PlotSettings()).plot(editing.currentOutput());
        backend.disconnect();
        String exported = Files.readString(gcode.toPath());
        assertTrue(exported.contains("G1 X3.571 Y5.000 F1000"), exported);
        assertTrue(exported.lines().filter(line -> line.startsWith("G1 ")).count() > 10);
    }

    @Test void editSaveOpenAndUndoPreserveQuickSourceAndPenAssignment() throws Exception {
        DocumentSession session = new DocumentSession();
        session.replace(new ProcessorOutput(new Metadata("base", Instant.EPOCH, "station", "mm", 0, Bounds.empty()),
                List.of(new Layer("base", "station", List.of()))));
        QuickArtwork first = new QuickArtwork(QuickArtwork.Kind.RECTANGLE, "", 20, 10);
        session.appendQuickArtwork(first);
        String artworkId = session.artworks().get(1).id();
        String penId = session.penLibrary().penForLayer(1).id();
        session.transformArtwork(artworkId, new CompositionArtwork.Transform(50, 40, 1, false));
        QuickArtwork edited = new QuickArtwork(QuickArtwork.Kind.RECTANGLE, "", 35, 15);
        session.editQuickArtwork(artworkId, edited);
        assertEquals(edited, session.artworks().get(1).quickArtwork());
        assertEquals(penId, session.penLibrary().penForLayer(1).id());
        assertEquals(new Bounds(50, 40, 85, 55), session.artworks().get(1).bounds());

        GantryProject project = new GantryProject(1, session.currentOutput(), session.selectedLayerIndices(),
                GantryProject.Placement.identity(), 1, GantryProject.Source.empty(), session.artworks(),
                session.penWidthsMm(), session.penLibrary());
        var file = temp.resolve("quick.gantry").toFile();
        GantryProjectIO.save(project, file);
        GantryProject reopened = GantryProjectIO.load(file);
        assertEquals(edited, reopened.artworks().get(1).quickArtwork());
        assertEquals(penId, reopened.penLibrary().penForLayer(1).id());
        assertEquals(new Bounds(50, 40, 85, 55), reopened.artworks().get(1).bounds());
        MoveCommand beforeMove = (MoveCommand) session.currentOutput().layers().get(1).commands().get(0);
        MoveCommand afterMove = (MoveCommand) reopened.output().layers().get(1).commands().get(0);
        assertEquals(new Point(beforeMove.x, beforeMove.y), new Point(afterMove.x, afterMove.y));
        assertEquals(((DrawCommand) session.currentOutput().layers().get(1).commands().get(1)).points,
                ((DrawCommand) reopened.output().layers().get(1).commands().get(1)).points);

        var gcode = temp.resolve("quick.gcode").toFile();
        GcodeOptions options = new GcodeOptions();
        options.penDownDelayMillis = 0;
        GcodeFileBackend backend = new GcodeFileBackend(options, gcode);
        assertTrue(backend.connect());
        new PlotService(backend, new PlotSettings()).plot(reopened.output());
        backend.disconnect();
        assertTrue(Files.readString(gcode.toPath()).contains("G1 X85.000 Y55.000 F1000"));

        session.undo();
        assertEquals(first, session.artworks().get(1).quickArtwork());
        session.redo();
        assertEquals(edited, session.artworks().get(1).quickArtwork());
    }
}
