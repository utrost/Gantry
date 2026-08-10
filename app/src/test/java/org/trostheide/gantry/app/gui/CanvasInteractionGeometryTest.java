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

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanvasInteractionGeometryTest {
    private static final double EPS = 1e-8;

    @Test
    void drawingDragFollowsMouseDirectionsRegardlessOfOverlayRotation() {
        for (boolean mirror : new boolean[] {false, true}) {
            for (int rotation : new int[] {0, 90, 180, 270}) {
                VisualizationPanel panel = panelWithArtwork();
                panel.overlayRotation = rotation;
                panel.overlayMirror = mirror;
                panel.paintScale = 2.0;

                VisualizationPanel.Point2D center = new VisualizationPanel.Point2D(50, 45);
                double[] before = panel.transformPoint(center);
                double[] offsetDelta = panel.interaction.screenDeltaToMm(24, -14);
                panel.overlayOffsetX += offsetDelta[0];
                panel.overlayOffsetY += offsetDelta[1];
                double[] after = panel.transformPoint(center);

                String transform = "rotation " + rotation + ", mirror " + mirror;
                assertEquals(24, (after[0] - before[0]) * panel.paintScale, EPS,
                        "horizontal drag at " + transform);
                assertEquals(-14, (after[1] - before[1]) * panel.paintScale, EPS,
                        "vertical drag at " + transform);
            }
        }
    }

    private static VisualizationPanel panelWithArtwork() {
        VisualizationPanel panel = new VisualizationPanel();
        panel.setMachineSize(300, 200);
        panel.setCanvasAlignment(null);
        DrawCommand rectangle = new DrawCommand(1, List.of(
                new Point(10, 20), new Point(90, 20), new Point(90, 70),
                new Point(10, 70), new Point(10, 20)));
        panel.loadFromOutput(new ProcessorOutput(
                new Metadata("drag.svg", Instant.EPOCH, "test", "mm", 1, Bounds.empty()),
                List.of(new Layer("artwork", "default", "#ffffff", List.of(rectangle)))));
        return panel;
    }
}
