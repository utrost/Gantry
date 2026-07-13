package org.trostheide.gantry.app.session;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentSessionTest {

    @Test
    void replacementSelectsEveryLayerAndClearsUndo() {
        DocumentSession session = new DocumentSession();
        session.replace(output("old"));
        session.snapshotForUndo();

        ProcessorOutput replacement = output("one", "two", "three");
        session.replace(replacement);

        assertSame(replacement, session.currentOutput());
        assertEquals(List.of(0, 1, 2), session.selectedLayerIndices());
        assertFalse(session.canUndo());
        assertFalse(session.canRedo());
    }

    @Test
    void selectedOutputKeepsMetadataAndRequestedLayerOrder() {
        DocumentSession session = new DocumentSession();
        ProcessorOutput original = output("one", "two", "three");
        session.replace(original);
        session.selectLayers(List.of(2, 0, 2, -1, 99));

        ProcessorOutput selected = session.selectedOutput();

        assertSame(original.metadata(), selected.metadata());
        assertEquals(List.of("one", "three"), selected.layers().stream().map(Layer::id).toList());
        assertEquals(List.of(0, 2), session.selectedLayerIndices());
    }

    @Test
    void preparationAppliesSelectionThenOverlayThenMultipass() {
        DocumentSession session = new DocumentSession();
        session.replace(output("one", "two"));
        session.selectLayers(List.of(1));

        List<String> observedLayers = new ArrayList<>();
        ProcessorOutput prepared = session.prepareOutput(selected -> {
            observedLayers.addAll(selected.layers().stream().map(Layer::id).toList());
            return selected;
        }, 2);

        assertEquals(List.of("two"), observedLayers);
        assertEquals(4, prepared.layers().get(0).commands().size());
    }

    @Test
    void undoAndRedoWalkMultiLevelHistory() {
        DocumentSession session = new DocumentSession();
        ProcessorOutput original = output("original");
        session.replace(original);
        session.snapshotForUndo();
        session.update(output("edited"));

        ProcessorOutput firstEdit = output("edited");
        session.update(firstEdit);
        session.snapshotForUndo();
        ProcessorOutput secondEdit = output("edited-again");
        session.update(secondEdit);

        assertSame(firstEdit, session.undo());
        assertSame(original, session.undo());
        assertSame(original, session.currentOutput());
        assertFalse(session.canUndo());
        assertNull(session.undo());
        assertSame(firstEdit, session.redo());
        assertSame(secondEdit, session.redo());
        assertFalse(session.canRedo());
    }

    @Test
    void editRetainsSourceAndOnlyDropsSelectionsMissingFromNewModel() {
        DocumentSession session = new DocumentSession();
        File source = new File("drawing.svg");
        session.replace(output("one", "two", "three"));
        session.recordSvgSource(source, SvgImportOptions.defaults());
        session.selectLayers(List.of(1, 2));

        session.update(output("one", "two"));

        assertEquals(List.of(1), session.selectedLayerIndices());
        assertEquals(source, session.sourceSvg());
    }

    @Test
    void sourceProvenanceIsDefensivelyStoredAndClearedWithDocument() {
        DocumentSession session = new DocumentSession();
        File svg = new File("drawing.svg");
        File image = new File("drawing.png");
        List<String> args = new ArrayList<>(List.of("--strategy", "bezier"));
        SvgImportOptions options = SvgImportOptions.defaults();

        session.recordSvgSource(svg, options);
        session.recordImageSource(image, args);
        args.add("--changed-after-recording");

        assertEquals(svg, session.sourceSvg());
        assertEquals(options, session.sourceSvgOptions());
        assertEquals(image, session.sourceImage());
        assertEquals(List.of("--strategy", "bezier"), session.vectorizeArgs());

        session.clear();
        assertNull(session.currentOutput());
        assertNull(session.sourceSvg());
        assertNull(session.sourceImage());
        assertTrue(session.vectorizeArgs().isEmpty());
    }

    private static ProcessorOutput output(String... layerIds) {
        Metadata metadata = new Metadata("test", Instant.EPOCH, "station", "mm", 0, Bounds.empty());
        List<Layer> layers = new ArrayList<>();
        int id = 1;
        for (String layerId : layerIds) {
            layers.add(new Layer(layerId, "station", List.of(
                    new MoveCommand(id++, 1, 1),
                    new DrawCommand(id++, List.of(new Point(2, 2))))));
        }
        return new ProcessorOutput(metadata, layers);
    }
}
