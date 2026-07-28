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

    private static ProcessorOutput rectangle(double width, double height) {
        DrawCommand outline = new DrawCommand(1, List.of(
                new Point(0, 0), new Point(width, 0), new Point(width, height),
                new Point(0, height), new Point(0, 0)));
        return new ProcessorOutput(
                new Metadata("size.svg", Instant.EPOCH, "test", "mm", 1, Bounds.empty()),
                List.of(new Layer("artwork", "default", "#ffffff", List.of(outline))));
    }
}
