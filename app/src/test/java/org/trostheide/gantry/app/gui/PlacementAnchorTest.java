package org.trostheide.gantry.app.gui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.app.session.*;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.command.*;

import java.time.Instant;
import java.util.List;

class PlacementAnchorTest {
    private VisualizationPanel view() {
        var view = new VisualizationPanel();
        view.setMachineSize(300, 300);
        view.setMachineOrigin("Bottom-Left");
        view.setCanvasAlignment(null);
        view.loadFromOutput(
                new ProcessorOutput(
                        new Metadata(
                                "anchor", Instant.EPOCH, "pen", "mm", 2, new Bounds(0, 0, 20, 10)),
                        List.of(
                                new Layer(
                                        "one",
                                        "pen",
                                        List.of(
                                                new MoveCommand(1, 0, 0),
                                                new DrawCommand(2, List.of(new Point(20, 10))))))));
        view.setContentMotorMin(100, 100);
        return view;
    }

    @Test
    void allNineReferencesStayFixedAcrossSizeRotationMirrorAndReferenceSelection() {
        for (int anchor = 0; anchor < 9; anchor++) {
            var view = view();
            double[] min = view.getContentMotorMin();
            view.setPlacementAnchor(anchor);
            assertArrayEquals(
                    min,
                    view.getContentMotorMin(),
                    1e-7,
                    "Changing reference cannot move geometry");
            var fixed = view.getContentMotorAnchor();
            view.resizeArtwork(40, 20, true);
            assertArrayEquals(fixed, view.getContentMotorAnchor(), 1e-7, "resize anchor " + anchor);
            view.rotateOverlay();
            assertArrayEquals(fixed, view.getContentMotorAnchor(), 1e-7, "rotate anchor " + anchor);
            view.toggleMirror();
            assertArrayEquals(fixed, view.getContentMotorAnchor(), 1e-7);
            var saved = view.placement();
            var other = view();
            other.applyPlacement(saved);
            assertEquals(saved, other.placement());
            assertArrayEquals(view.getContentMotorAnchor(), other.getContentMotorAnchor(), 1e-7);
        }
    }

    @Test
    void impossibleTransformIsRejectedWithoutDriftingReference() {
        var view = view();
        view.setPlacementAnchor(8);
        var before = view.placement();
        view.resizeArtwork(2000, 1000, true);
        assertEquals(before, view.placement());
    }

    @Test
    void oldPlacementConstructorDefaultsToOriginalCornerAndNumericEditsUseSelectedPoint() {
        assertEquals(0, new GantryProject.Placement(1, 2, 1, 0, false, false).anchor());
        var view = view();
        view.setPlacementAnchor(4);
        view.setContentMotorAnchor(150, 150);
        assertArrayEquals(new double[] {150, 150}, view.getContentMotorAnchor(), 1e-7);
    }

    @Test
    void preservesAllReferencesAcrossAxisFlipSwapAndDataRotationMatrix() {
        for (int flags = 0; flags < 16; flags++)
            for (int rotation : new int[] {0, 90, 180, 270})
                for (int anchor = 0; anchor < 9; anchor++) {
                    var view = view();
                    view.setEffectiveAxes((flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
                    view.setFlipY((flags & 8) != 0);
                    view.setDataRotation(rotation);
                    view.setContentMotorMin(100, 100);
                    view.setPlacementAnchor(anchor);
                    double[] fixed = view.getContentMotorAnchor();
                    view.resizeArtwork(
                            view.getContentMotorSize()[0] * .8,
                            view.getContentMotorSize()[1] * .8,
                            true);
                    assertArrayEquals(
                            fixed,
                            view.getContentMotorAnchor(),
                            1e-6,
                            "resize mapping " + flags + "/" + rotation + "/" + anchor);
                    view.rotateOverlay();
                    assertArrayEquals(
                            fixed,
                            view.getContentMotorAnchor(),
                            1e-6,
                            "rotation mapping " + flags + "/" + rotation + "/" + anchor);
                }
    }

    @Test
    void olderSerializedPlacementDefaultsToReferenceZeroWithoutChangingValues() throws Exception {
        var old =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(
                                "{\"offsetX\":12,\"offsetY\":13,\"scale\":2,\"rotation\":90,\"mirror\":true,\"suppressAlignment\":true,\"stretchX\":1,\"stretchY\":1}",
                                GantryProject.Placement.class);
        assertEquals(new GantryProject.Placement(12, 13, 2, 90, true, true, 1, 1), old);
        var view = view();
        view.applyPlacement(old);
        var before = view.getContentMotorAnchor();
        view.setPlacementAnchor(0);
        assertArrayEquals(before, view.getContentMotorAnchor(), 1e-7);
    }
}
