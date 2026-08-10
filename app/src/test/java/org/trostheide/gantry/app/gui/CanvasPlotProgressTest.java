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

class CanvasPlotProgressTest {

    @Test
    void strokeBecomesCompleteOnlyAfterFinalPreparedPassIsAccepted() {
        DrawCommand stroke = new DrawCommand(7, List.of(new Point(1, 2), new Point(3, 4)));
        ProcessorOutput displayed = output(new Layer("ink", "default", "#112233", List.of(stroke)));
        ProcessorOutput twoPassPlot = output(new Layer("ink", "default", "#112233", List.of(stroke, stroke)));
        VisualizationPanel panel = new VisualizationPanel();
        panel.loadFromOutput(displayed);
        panel.beginPlotProgress(twoPassPlot);

        assertEquals(VisualizationPanel.StrokeProgressState.PENDING, panel.strokeProgressState(0));
        panel.updateStrokeProgress("ink", 7, false);
        assertEquals(VisualizationPanel.StrokeProgressState.ACTIVE, panel.strokeProgressState(0));
        panel.updateStrokeProgress("ink", 7, true);
        assertEquals(VisualizationPanel.StrokeProgressState.PENDING, panel.strokeProgressState(0));
        panel.updateStrokeProgress("ink", 7, false);
        panel.updateStrokeProgress("ink", 7, true);
        assertEquals(VisualizationPanel.StrokeProgressState.COMPLETED, panel.strokeProgressState(0));
    }

    @Test
    void finishingInterruptedPlotClearsActiveHighlightButKeepsAcceptedStrokes() {
        DrawCommand accepted = new DrawCommand(1, List.of(new Point(1, 1)));
        DrawCommand active = new DrawCommand(2, List.of(new Point(2, 2)));
        ProcessorOutput output = output(new Layer("ink", "default", "#ffffff", List.of(accepted, active)));
        VisualizationPanel panel = new VisualizationPanel();
        panel.loadFromOutput(output);
        panel.beginPlotProgress(output);
        panel.updateStrokeProgress("ink", 1, false);
        panel.updateStrokeProgress("ink", 1, true);
        panel.updateStrokeProgress("ink", 2, false);

        panel.finishPlotProgress();

        assertEquals(VisualizationPanel.StrokeProgressState.COMPLETED, panel.strokeProgressState(0));
        assertEquals(VisualizationPanel.StrokeProgressState.PENDING, panel.strokeProgressState(1));
    }

    private static ProcessorOutput output(Layer layer) {
        return new ProcessorOutput(
                new Metadata("progress.svg", Instant.EPOCH, "test", "mm", 0, Bounds.empty()),
                List.of(layer));
    }
}
