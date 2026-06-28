package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.svgtoolbox.Config;
import org.trostheide.gantry.svgtoolbox.HatchStyle;
import org.trostheide.gantry.svgtoolbox.patterns.LinearHatchPattern;

import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry/model helpers for click-to-hatch (Phase 10 Tier 3): turn a clicked region into
 * plottable hatch strokes and splice them into the command model. Kept free of Swing so it can be
 * unit-tested headlessly; {@link VisualizationPanel} supplies the clicked region and
 * {@code PlotterPanel} applies the result.
 */
final class RegionHatch {

    private RegionHatch() {
    }

    /** A shared, default toolbox config; {@link LinearHatchPattern} ignores it but the API needs one. */
    private static final Config DEFAULT_CONFIG = new Config.Builder().build();

    /**
     * Linear hatch fill for {@code region} (closed, in model mm space) as 2-point draw strokes,
     * numbered from {@code startId}. Empty if the region is smaller than {@code gap}.
     */
    static List<DrawCommand> hatchCommands(Path2D region, double angleDeg, double gapMm, int startId) {
        List<Shape> lines = new LinearHatchPattern().generate(region, DEFAULT_CONFIG,
                HatchStyle.of(angleDeg, gapMm));
        List<DrawCommand> out = new ArrayList<>();
        int id = startId;
        for (Shape s : lines) {
            if (s instanceof Line2D l) {
                out.add(new DrawCommand(id++, List.of(
                        new Point(l.getX1(), l.getY1()),
                        new Point(l.getX2(), l.getY2()))));
            }
        }
        return out;
    }

    /** The highest command id across all layers, or 0 if there are none. */
    static int maxCommandId(ProcessorOutput out) {
        int max = 0;
        for (Layer layer : out.layers()) {
            for (Command c : layer.commands()) {
                max = Math.max(max, c.getId());
            }
        }
        return max;
    }

    /**
     * A copy of {@code out} with {@code extra} appended to layer {@code layerIndex}'s commands and
     * {@code metadata.totalCommands} bumped accordingly. Other layers are shared unchanged. If
     * {@code layerIndex} is out of range or {@code extra} is empty, {@code out} is returned as-is.
     */
    static ProcessorOutput appendToLayer(ProcessorOutput out, int layerIndex, List<? extends Command> extra) {
        if (extra.isEmpty() || layerIndex < 0 || layerIndex >= out.layers().size()) {
            return out;
        }
        List<Layer> layers = new ArrayList<>(out.layers());
        Layer target = layers.get(layerIndex);
        List<Command> merged = new ArrayList<>(target.commands());
        merged.addAll(extra);
        layers.set(layerIndex, new Layer(target.id(), target.stationId(), target.color(), merged));

        Metadata m = out.metadata();
        Metadata m2 = new Metadata(m.source(), m.generatedAt(), m.stationId(), m.units(),
                m.totalCommands() + extra.size(), m.bounds());
        return new ProcessorOutput(m2, layers);
    }
}
