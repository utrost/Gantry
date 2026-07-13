package org.trostheide.gantry.pipeline.optimize;

import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * Multipass / pigment buildup: repeats each MOVE/DRAW stroke {@code passes} times in place, so
 * the pen retraces the same stroke for bolder lines (pen) or more pigment/rewetting (watercolor).
 */
public final class MultipassStage {

    private MultipassStage() {
    }

    /** Returns a new {@link ProcessorOutput} with every stroke repeated {@code passes} times. {@code passes <= 1} returns {@code input} unchanged. */
    public static ProcessorOutput apply(ProcessorOutput input, int passes) {
        if (passes <= 1) {
            return input;
        }
        List<Layer> layers = new ArrayList<>();
        for (Layer layer : input.layers()) {
            layers.add(applyToLayer(layer, passes));
        }
        Metadata metadata = input.metadata();
        if (metadata != null) {
            int totalCommands = layers.stream().mapToInt(layer -> layer.commands().size()).sum();
            metadata = new Metadata(metadata.source(), metadata.generatedAt(), metadata.stationId(),
                    metadata.units(), totalCommands, metadata.bounds());
        }
        return new ProcessorOutput(metadata, layers);
    }

    private static Layer applyToLayer(Layer layer, int passes) {
        List<Command> source = layer.commands();
        List<Command> result = new ArrayList<>();

        int i = 0;
        while (i < source.size()) {
            Command cmd = source.get(i);
            if (cmd instanceof MoveCommand move && i + 1 < source.size() && source.get(i + 1) instanceof DrawCommand draw) {
                for (int pass = 0; pass < passes; pass++) {
                    result.add(new MoveCommand(move.id, move.x, move.y));
                    result.add(new DrawCommand(draw.id, draw.points));
                }
                i += 2;
            } else {
                result.add(cmd);
                i++;
            }
        }
        return new Layer(layer.id(), layer.stationId(), layer.color(), result);
    }
}
