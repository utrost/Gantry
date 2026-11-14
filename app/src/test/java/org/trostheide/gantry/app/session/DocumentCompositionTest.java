package org.trostheide.gantry.app.session;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentCompositionTest {

    @Test
    void appendPlacesSecondArtworkToTheRightWithUniqueLayersAndCommandIds() {
        DocumentSession session = new DocumentSession();
        session.replace(output("base.svg", "ink", 0, 0, 20, 10, 1));

        session.appendArtwork(output("border.svg", "ink", 0, 0, 5, 5, 1), "border.svg");

        ProcessorOutput composed = session.currentOutput();
        assertEquals(List.of("ink", "border.svg / ink"), composed.layers().stream().map(Layer::id).toList());
        assertEquals(List.of(0, 1), session.selectedLayerIndices(), "newly composed job should select all layers");
        assertEquals(4, composed.metadata().totalCommands());
        assertEquals("base.svg + border.svg", composed.metadata().source());
        assertEquals(new Bounds(0, 0, 35, 10), composed.metadata().bounds());

        List<Integer> ids = composed.layers().stream()
                .flatMap(layer -> layer.commands().stream())
                .map(Command::getId)
                .toList();
        assertEquals(List.of(1, 2, 3, 4), ids, "appended command ids should be remapped after existing ids");

        MoveCommand appendedMove = assertInstanceOf(MoveCommand.class, composed.layers().get(1).commands().get(0));
        assertEquals(30, appendedMove.x, 0.0001, "append should offset new artwork by current width plus gap");
        DrawCommand appendedDraw = assertInstanceOf(DrawCommand.class, composed.layers().get(1).commands().get(1));
        assertEquals(new Point(35, 5), appendedDraw.points.get(0));
    }

    @Test
    void appendIsUndoable() {
        DocumentSession session = new DocumentSession();
        ProcessorOutput base = output("base.svg", "ink", 0, 0, 20, 10, 1);
        session.replace(base);

        session.appendArtwork(output("border.svg", "border", 0, 0, 5, 5, 1), "border.svg");

        assertEquals(2, session.currentOutput().layers().size());
        assertTrue(session.canUndo());
        assertEquals(base, session.undo());
    }

    @Test
    void artworksAreAddressableAndTransformableAsGroups() {
        DocumentSession session = new DocumentSession();
        session.replace(output("main.svg", "main", 0, 0, 10, 10, 1));
        session.appendArtwork(output("mark.svg", "mark", 0, 0, 10, 10, 1), "mark.svg");

        assertEquals(List.of("main.svg", "mark.svg"), session.artworks().stream().map(CompositionArtwork::label).toList());
        String appendedId = session.artworks().get(1).id();

        session.transformArtwork(appendedId, new CompositionArtwork.Transform(5, -2, 2.0, true));

        CompositionArtwork transformed = session.artworks().get(1);
        assertEquals(new CompositionArtwork.Transform(5, -2, 2.0, true), transformed.transform());
        assertEquals(new Bounds(5, -2, 25, 18), transformed.bounds());
        assertEquals(new Bounds(0, -2, 25, 18), session.currentOutput().metadata().bounds());

        MoveCommand moved = assertInstanceOf(MoveCommand.class, session.currentOutput().layers().get(1).commands().get(0));
        assertEquals(25, moved.x, 0.0001, "mirrored/scaled group should keep its original left edge plus dx");
        assertEquals(-2, moved.y, 0.0001);
        DrawCommand drawn = assertInstanceOf(DrawCommand.class, session.currentOutput().layers().get(1).commands().get(1));
        assertEquals(new Point(5, 18), drawn.points.get(0));
    }

    @Test
    void artworkTransformsAreUndoable() {
        DocumentSession session = new DocumentSession();
        session.replace(output("main.svg", "main", 0, 0, 10, 10, 1));
        String id = session.artworks().get(0).id();

        session.transformArtwork(id, new CompositionArtwork.Transform(3, 4, 1.5, false));

        assertEquals(new Bounds(3, 4, 18, 19), session.artworks().get(0).bounds());
        assertTrue(session.canUndo());
        session.undo();
        assertEquals(new Bounds(0, 0, 10, 10), session.artworks().get(0).bounds());
    }

    @Test
    void replaceArtworkPreservesTransformAndOtherArtworkLayers() {
        DocumentSession session = new DocumentSession();
        session.replace(output("main.svg", "main", 0, 0, 10, 10, 1));
        session.appendArtwork(output("mark.svg", "mark", 0, 0, 10, 10, 10), "mark.svg");
        String markId = session.artworks().get(1).id();
        session.transformArtwork(markId, new CompositionArtwork.Transform(40, 5, 2.0, true));
        List<Command> mainBefore = session.currentOutput().layers().get(0).commands();

        session.replaceArtwork(markId, output("mark-v2.svg", "mark", 0, 0, 5, 5, 100),
                "/tmp/mark-v2.svg", null, null);

        assertEquals(mainBefore, session.currentOutput().layers().get(0).commands(),
                "replacing one artwork should not touch the other artwork group's commands");
        assertEquals(2, session.currentOutput().layers().size());
        assertEquals(List.of(0, 1), session.selectedLayerIndices());
        CompositionArtwork replaced = session.artworks().get(1);
        assertEquals(markId, replaced.id());
        assertEquals("mark-v2.svg", replaced.label());
        assertEquals("/tmp/mark-v2.svg", replaced.sourcePath());
        assertEquals(new CompositionArtwork.Transform(40, 5, 2.0, true), replaced.transform());
        assertEquals(new Bounds(40, 5, 50, 15), replaced.bounds());
        assertEquals(new Bounds(0, 0, 50, 15), session.currentOutput().metadata().bounds());
        MoveCommand replacedMove = assertInstanceOf(MoveCommand.class, session.currentOutput().layers().get(1).commands().get(0));
        assertEquals(50, replacedMove.x, 0.0001);
        DrawCommand replacedDraw = assertInstanceOf(DrawCommand.class, session.currentOutput().layers().get(1).commands().get(1));
        assertEquals(new Point(40, 15), replacedDraw.points.get(0));
    }

    @Test
    void renameArtworkUpdatesOnlyTheHandleAndIsUndoable() {
        DocumentSession session = new DocumentSession();
        session.replace(output("main.svg", "main", 0, 0, 10, 10, 1));
        String id = session.artworks().get(0).id();

        session.renameArtwork(id, "Front registration mark");

        assertEquals("Front registration mark", session.artworks().get(0).label());
        assertEquals("main", session.currentOutput().layers().get(0).id(), "renaming a group should not rename plotted layers");
        assertTrue(session.canUndo());
        session.undo();
        assertEquals("main.svg", session.artworks().get(0).label());
    }

    @Test
    void appendIntoEmptyDocumentRetainsSourceAndProcessingProvenance() {
        DocumentSession session = new DocumentSession();
        java.io.File source = new java.io.File("first.svg");
        var options = org.trostheide.gantry.pipeline.svgimport.SvgImportOptions.defaults();
        session.appendArtwork(output("first.svg", "ink", 0, 0, 10, 10, 1), "First drawing", source, options, null);
        assertEquals(source, session.sourceSvg());
        assertEquals(options, session.sourceSvgOptions());
        assertEquals(source.getAbsolutePath(), session.artworks().get(0).sourcePath());
        assertEquals("First drawing", session.artworks().get(0).label());
    }

    @Test
    void ambiguousIncomingLayersDoNotInheritOneIncludedLayerTwice() {
        DocumentSession session = new DocumentSession();
        ProcessorOutput first = output("first.svg", "ink", 0, 0, 10, 10, 1);
        session.replace(first);
        session.setPenWidthsMm(List.of(.8));
        Layer duplicate = first.layers().get(0);
        ProcessorOutput replacement = new ProcessorOutput(first.metadata(), List.of(duplicate, duplicate));
        session.replaceArtwork(session.artworks().get(0).id(), replacement, "first.svg", null, null);
        assertTrue(session.hasAmbiguousLayerMapping());
        assertEquals(List.of(), session.selectedLayerIndices());
        assertEquals(List.of(.3, .3), session.penWidthsMm());
        session.undo();
        assertEquals(List.of(0), session.selectedLayerIndices());
        assertEquals(List.of(.8), session.penWidthsMm());
        session.redo();
        assertEquals(List.of(), session.selectedLayerIndices());
        assertEquals(List.of(.3, .3), session.penWidthsMm());
    }

    private static ProcessorOutput output(String source, String layerId,
            double minX, double minY, double maxX, double maxY, int firstId) {
        List<Command> commands = List.of(
                new MoveCommand(firstId, minX, minY),
                new DrawCommand(firstId + 1, List.of(new Point(maxX, maxY)))
        );
        Metadata metadata = new Metadata(source, Instant.EPOCH, "station", "mm", commands.size(),
                new Bounds(minX, minY, maxX, maxY));
        return new ProcessorOutput(metadata, List.of(new Layer(layerId, "station", commands)));
    }
}
