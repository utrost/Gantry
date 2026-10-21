package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.DrawCommand;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class VisualizationArtworkSizeTest {

    @Test
    void sizeTracksInteractiveScaleAndQuarterTurnRotation() {
        VisualizationPanel panel = new VisualizationPanel();
        panel.loadFromOutput(rectangle(80, 50));

        assertArrayEquals(new double[] {80, 50}, panel.getContentMotorSize(), 1e-9);

        panel.overlayScale = 0.5;
        assertArrayEquals(new double[] {40, 25}, panel.getContentMotorSize(), 1e-9);

        panel.rotateOverlay();
        assertArrayEquals(new double[] {25, 40}, panel.getContentMotorSize(), 1e-9);
    }

    @Test
    void suppressingAlignmentRetainsImportedPageMarginOffsets() {
        VisualizationPanel panel = new VisualizationPanel();
        panel.setMachineSize(594, 841);
        panel.setCanvasAlignment("Bottom Left");
        panel.loadFromOutput(rectangleAt(15, 32, 180, 233));

        panel.setSuppressAlignment(true);

        assertArrayEquals(new double[] {15, 32}, panel.getContentMotorMin(), 1e-9);
    }

    @Test void unlockedSizingChangesOnePhysicalDimensionAndRestoresPlacement() {
        VisualizationPanel panel=new VisualizationPanel();panel.setMachineSize(600,800);panel.loadFromOutput(rectangle(80,50));
        var initial=panel.placement();
        panel.resizeArtwork(120,50,true,false);
        assertArrayEquals(new double[]{120,50},panel.getContentMotorSize(),1e-8);
        panel.resizeArtwork(120,90,false,false);
        assertArrayEquals(new double[]{120,90},panel.getContentMotorSize(),1e-8);
        var stretched=panel.placement();panel.applyPlacement(initial);
        assertArrayEquals(new double[]{80,50},panel.getContentMotorSize(),1e-8);
        panel.applyPlacement(stretched);assertArrayEquals(new double[]{120,90},panel.getContentMotorSize(),1e-8);
    }

    @Test void importedPagePlacementIsTheFirstUndoBaseline(){
        VisualizationPanel panel=new VisualizationPanel();panel.loadFromOutput(rectangle(80,50));
        java.util.concurrent.atomic.AtomicReference<org.trostheide.gantry.app.session.GantryProject.Placement> before=new java.util.concurrent.atomic.AtomicReference<>();
        panel.setPlacementEditListener((oldValue,newValue)->before.set(oldValue));
        panel.setSuppressAlignment(true);var imported=panel.placement();panel.rotateOverlay();
        org.junit.jupiter.api.Assertions.assertEquals(imported,before.get());
    }

    private static ProcessorOutput rectangle(double width, double height) {
        return rectangleAt(0, 0, width, height);
    }

    private static ProcessorOutput rectangleAt(double x, double y, double width, double height) {
        DrawCommand outline = new DrawCommand(1, List.of(
                new Point(x, y), new Point(x + width, y), new Point(x + width, y + height),
                new Point(x, y + height), new Point(x, y)));
        return new ProcessorOutput(
                new Metadata("size.svg", Instant.EPOCH, "test", "mm", 1, Bounds.empty()),
                List.of(new Layer("artwork", "default", "#ffffff", List.of(outline))));
    }
}
