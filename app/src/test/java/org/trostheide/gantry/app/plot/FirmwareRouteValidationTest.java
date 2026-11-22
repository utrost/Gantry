package org.trostheide.gantry.app.plot;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.command.*;
import org.trostheide.gantry.plotter.GcodeBackendException;

import java.time.Instant;
import java.util.*;

class FirmwareRouteValidationTest {
    @Test
    void rejectsFullStationSwirlAndLateTravelBeforeAnyInkOrMotion() {
        var backend =
                new FakePlotterBackend() {
                    public void validateWorkEnvelope(
                            double minX, double minY, double maxX, double maxY) {
                        if (Math.max(minX, maxX) > 90 || Math.max(minY, maxY) > 90)
                            throw new GcodeBackendException("outside firmware");
                    }
                };
        var settings = new PlotSettings();
        settings.machineWidth = 100.0;
        settings.machineHeight = 100.0;
        settings.stations.put("paint", new StationConfig(85, 50, 0, "dip_swirl", null, 20, 10));
        var layer =
                new Layer(
                        "pen",
                        "paint",
                        List.of(
                                new MoveCommand(1, 10, 10),
                                new DrawCommand(2, List.of(new Point(20, 20))),
                                new RefillCommand(3, "paint")));
        assertThrows(
                GcodeBackendException.class,
                () -> new PlotService(backend, settings).plot(output(layer)));
        assertTrue(backend.calls.isEmpty());
        Layer late =
                layer.withCommands(
                        List.of(
                                new MoveCommand(1, 10, 10),
                                new DrawCommand(2, List.of(new Point(20, 20))),
                                new MoveCommand(3, 95, 50)));
        assertThrows(
                GcodeBackendException.class,
                () -> new PlotService(backend, settings).plot(output(late)));
        assertTrue(backend.calls.isEmpty());
    }

    private ProcessorOutput output(Layer layer) {
        return new ProcessorOutput(
                new Metadata(
                        "validation",
                        Instant.EPOCH,
                        "pen",
                        "mm",
                        layer.commands().size(),
                        new Bounds(0, 0, 100, 100)),
                List.of(layer));
    }
}
