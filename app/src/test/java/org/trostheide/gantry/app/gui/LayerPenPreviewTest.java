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

class LayerPenPreviewTest {
    @Test void penWidthIsPhysicalAndIndependentOfArtworkScale() {
        VisualizationPanel panel = new VisualizationPanel();
        DrawCommand line = new DrawCommand(1, List.of(new Point(0, 0), new Point(100, 0)));
        panel.loadFromOutput(new ProcessorOutput(
                new Metadata("two-pens.svg", Instant.EPOCH, "test", "mm", 2, Bounds.empty()),
                List.of(new Layer("fine", "pen-1", "#cc0000", List.of(line)),
                        new Layer("broad", "pen-2", "#0044cc", List.of(line)))));

        panel.setPenWidthForLayer(0, 0.3);
        panel.setPenWidthForLayer(1, 1.2);
        panel.overlayScale = 2.0;

        assertEquals(200.0, panel.getContentMotorSize()[0], 1e-9);
        assertEquals(0.3, panel.penWidthForLayer(0), 1e-9);
        assertEquals(1.2, panel.penWidthForLayer(1), 1e-9);
    }
}
