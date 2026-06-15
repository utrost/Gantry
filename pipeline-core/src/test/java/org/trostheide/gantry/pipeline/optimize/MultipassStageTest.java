package org.trostheide.gantry.pipeline.optimize;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.model.command.RefillCommand;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MultipassStageTest {

    private static ProcessorOutput output(Layer... layers) {
        Metadata meta = new Metadata("test", Instant.EPOCH, "station", "mm", 0, Bounds.empty());
        return new ProcessorOutput(meta, List.of(layers));
    }

    @Test
    void onePassOrFewerReturnsInputUnchanged() {
        Layer layer = new Layer("L1", "default_station", List.of(
                new MoveCommand(1, 0, 0),
                new DrawCommand(2, List.of(new Point(0, 0), new Point(1, 1)))));
        ProcessorOutput input = output(layer);

        assertSame(input, MultipassStage.apply(input, 1));
        assertSame(input, MultipassStage.apply(input, 0));
    }

    @Test
    void repeatsEachStrokeAndPreservesOtherCommands() {
        Layer layer = new Layer("L1", "default_station", List.of(
                new RefillCommand(1, "default_station"),
                new MoveCommand(2, 0, 0),
                new DrawCommand(3, List.of(new Point(0, 0), new Point(1, 1)))));

        ProcessorOutput optimized = MultipassStage.apply(output(layer), 3);

        List<Command> commands = optimized.layers().get(0).commands();
        // RefillCommand stays once, the (Move, Draw) pair repeats 3x
        assertEquals(1 + 2 * 3, commands.size());
        assertEquals(RefillCommand.class, commands.get(0).getClass());
        for (int i = 0; i < 3; i++) {
            assertEquals(MoveCommand.class, commands.get(1 + i * 2).getClass());
            assertEquals(DrawCommand.class, commands.get(2 + i * 2).getClass());
        }
    }
}
