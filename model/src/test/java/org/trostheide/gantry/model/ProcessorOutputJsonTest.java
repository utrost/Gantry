package org.trostheide.gantry.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.model.command.RefillCommand;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessorOutputJsonTest {

    @Test
    void roundTripsThroughJsonPreservingCommandTypes() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        List<Command> commands = List.of(
                new MoveCommand(0, 1.0, 2.0),
                new DrawCommand(1, List.of(new Point(1.0, 2.0), new Point(3.0, 4.0))),
                new RefillCommand(2, "station-1")
        );
        Layer layer = new Layer("layer-1", "station-1", commands);
        Metadata metadata = new Metadata("test.svg", Instant.parse("2024-01-01T00:00:00Z"),
                "station-1", "mm", commands.size(), new Bounds(0, 0, 100, 100));
        ProcessorOutput output = new ProcessorOutput(metadata, List.of(layer));

        String json = mapper.writeValueAsString(output);
        ProcessorOutput parsed = mapper.readValue(json, ProcessorOutput.class);

        assertEquals(output.metadata(), parsed.metadata());
        assertEquals(1, parsed.layers().size());

        List<Command> parsedCommands = parsed.layers().get(0).commands();
        assertEquals(3, parsedCommands.size());
        assertInstanceOf(MoveCommand.class, parsedCommands.get(0));
        assertInstanceOf(DrawCommand.class, parsedCommands.get(1));
        assertInstanceOf(RefillCommand.class, parsedCommands.get(2));

        DrawCommand drawCommand = (DrawCommand) parsedCommands.get(1);
        assertEquals(List.of(new Point(1.0, 2.0), new Point(3.0, 4.0)), drawCommand.points);
    }

    @Test
    void commandModelDefensivelyCopiesAllCollectionBoundaries() {
        List<Point> points = new ArrayList<>(List.of(new Point(1, 2), new Point(3, 4)));
        DrawCommand draw = new DrawCommand(1, points);
        List<Command> commands = new ArrayList<>(List.of(draw));
        Layer layer = new Layer("ink", null, commands);
        List<Layer> layers = new ArrayList<>(List.of(layer));
        ProcessorOutput output = new ProcessorOutput(null, layers);

        points.clear();
        commands.clear();
        layers.clear();

        assertEquals(2, draw.points.size());
        assertEquals(1, layer.commands().size());
        assertEquals(1, output.layers().size());
        assertThrows(UnsupportedOperationException.class,
                () -> draw.points.add(new Point(5, 6)));
        assertThrows(UnsupportedOperationException.class,
                () -> layer.commands().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> output.layers().clear());
    }

    @Test
    void commandModelRejectsNullCollectionsAndElements() {
        assertThrows(NullPointerException.class, () -> new DrawCommand(1, null));
        assertThrows(NullPointerException.class,
                () -> new DrawCommand(1, java.util.Arrays.asList(new Point(1, 2), null)));
        assertThrows(NullPointerException.class, () -> new Layer("ink", null, null));
        assertThrows(NullPointerException.class, () -> new ProcessorOutput(null, null));
    }
}
