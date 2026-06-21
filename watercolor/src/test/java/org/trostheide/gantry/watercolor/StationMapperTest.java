package org.trostheide.gantry.watercolor;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.RefillCommand;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StationMapperTest {

    private static final List<PaintStation> STATIONS = List.of(
            new PaintStation("redPot", "#ff0000"),
            new PaintStation("greenPot", "#00ff00"),
            new PaintStation("bluePot", "#0000ff"));

    @Test
    void nearestStationPicksClosestColour() {
        assertEquals("redPot", StationMapper.nearestStation("#fe0202", STATIONS));
        assertEquals("greenPot", StationMapper.nearestStation("#10e010", STATIONS));
        assertEquals("bluePot", StationMapper.nearestStation("#0a0ad0", STATIONS));
    }

    @Test
    void nearestStationReturnsNullWhenColourUnknownOrNoStations() {
        assertNull(StationMapper.nearestStation(null, STATIONS));
        assertNull(StationMapper.nearestStation("#ff0000", List.of()));
    }

    @Test
    void assignByColourRewritesLayerAndRefillStationIds() {
        Command refill = new RefillCommand(1, "Layer1");
        Layer layer = new Layer("L1", "Layer1", "#ee0000", List.of(refill));
        ProcessorOutput output = new ProcessorOutput(
                new Metadata("t", Instant.EPOCH, "s", "mm", 1, Bounds.empty()), List.of(layer));

        ProcessorOutput mapped = StationMapper.assignByColor(output, STATIONS);

        Layer result = mapped.layers().get(0);
        assertEquals("redPot", result.stationId());
        assertEquals("redPot", ((RefillCommand) result.commands().get(0)).stationId);
        // Original is untouched (immutability).
        assertEquals("Layer1", layer.stationId());
    }

    @Test
    void assignByColourKeepsOriginalStationWhenColourUnmatchable() {
        Layer layer = new Layer("L1", "Layer1", null, List.of());
        ProcessorOutput output = new ProcessorOutput(
                new Metadata("t", Instant.EPOCH, "s", "mm", 0, Bounds.empty()), List.of(layer));

        ProcessorOutput mapped = StationMapper.assignByColor(output, STATIONS);

        assertEquals("Layer1", mapped.layers().get(0).stationId());
    }
}
