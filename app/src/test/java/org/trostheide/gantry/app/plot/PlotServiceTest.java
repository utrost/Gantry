package org.trostheide.gantry.app.plot;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotServiceTest {

    private static ProcessorOutput output(Layer... layers) {
        Metadata meta = new Metadata("test", Instant.EPOCH, "station", "mm", 0, Bounds.empty());
        return new ProcessorOutput(meta, List.of(layers));
    }

    @Test
    void movetoAndLinetoApplyTransformAndOffset() {
        FakePlotterBackend backend = new FakePlotterBackend();
        PlotSettings settings = new PlotSettings();
        settings.machineWidth = 100.0;
        settings.machineHeight = 100.0;
        settings.invertX = true; // x' = machineWidth - x
        PlotService service = new PlotService(backend, settings);

        Layer layer = new Layer("L1", "default_station", List.of(
                new MoveCommand(1, 10, 20),
                new DrawCommand(2, List.of(new Point(5, 5)))));

        service.plot(output(layer));

        assertEquals(List.of("MOVETO 90.000 20.000", "LINETO 95.000 5.000",
                "PENUP", "MOVETO 0.000 0.000"), backend.calls);
    }

    @Test
    void softClampWarnsAndClampsOutOfBoundsPoints() {
        FakePlotterBackend backend = new FakePlotterBackend();
        PlotSettings settings = new PlotSettings();
        settings.machineWidth = 100.0;
        settings.machineHeight = 100.0;
        PlotService service = new PlotService(backend, settings);

        List<String> logs = new ArrayList<>();
        service.setLogCallback(logs::add);

        Layer layer = new Layer("L1", "default_station", List.of(
                new MoveCommand(1, 150, -5)));

        service.plot(output(layer));

        assertEquals(List.of("MOVETO 100.000 0.000", "PENUP", "MOVETO 0.000 0.000"), backend.calls);
        assertTrue(logs.stream().anyMatch(l -> l.contains("SOFT LIMIT")), "expected a soft-limit warning, got: " + logs);
    }

    @Test
    void canvasAlignCenterOffsetsContentToMachineCenter() {
        FakePlotterBackend backend = new FakePlotterBackend();
        PlotSettings settings = new PlotSettings();
        settings.machineWidth = 100.0;
        settings.machineHeight = 100.0;
        settings.canvasAlign = "center";
        PlotService service = new PlotService(backend, settings);

        // Content spans x:[0,10], y:[0,20] -> centered offset is ((100-10)/2, (100-20)/2) = (45, 40)
        Layer layer = new Layer("L1", "default_station", List.of(
                new DrawCommand(1, List.of(new Point(0, 0), new Point(10, 20)))));

        service.plot(output(layer));

        assertEquals(List.of("LINETO 45.000 40.000", "LINETO 55.000 60.000",
                "PENUP", "MOVETO 0.000 0.000"), backend.calls);
    }

    @Test
    void bottomOriginAlignmentUsesVisibleCanvasCornerAndStaysInBounds() {
        FakePlotterBackend backend = new FakePlotterBackend();
        PlotSettings settings = new PlotSettings();
        settings.machineWidth = 100.0;
        settings.machineHeight = 80.0;
        settings.invertY = true;
        settings.originBottom = true;
        settings.canvasAlign = "bottom-left";
        settings.paddingX = 5;
        settings.paddingY = 7;
        PlotService service = new PlotService(backend, settings);

        Layer layer = new Layer("L1", "default_station", List.of(
                new DrawCommand(1, List.of(new Point(10, 20), new Point(30, 50)))));

        service.plot(output(layer));

        assertEquals(List.of("LINETO 5.000 37.000", "LINETO 25.000 7.000",
                "PENUP", "MOVETO 0.000 0.000"), backend.calls);
    }

    @Test
    void refillSimpleDipMovesDipsAndLifts() {
        FakePlotterBackend backend = new FakePlotterBackend();
        PlotSettings settings = new PlotSettings();
        settings.machineWidth = 100.0;
        settings.machineHeight = 100.0;
        settings.stations.put("red", new StationConfig(10, 20, 30, "simple_dip"));
        PlotService service = new PlotService(backend, settings);

        Layer layer = new Layer("L1", "red", List.of(new RefillCommand(1, "red")));

        service.plot(output(layer));

        assertEquals(List.of("MOVETO 10.000 20.000", "PENDOWN", "PENUP",
                "PENUP", "MOVETO 0.000 0.000"), backend.calls);
    }

    @Test
    void refillDipSwirlSwirlsBrushInACircleAfterDip() {
        FakePlotterBackend backend = new FakePlotterBackend();
        PlotSettings settings = new PlotSettings();
        settings.machineWidth = 100.0;
        settings.machineHeight = 100.0;
        settings.stations.put("red", new StationConfig(10, 20, 30, "dip_swirl"));
        PlotService service = new PlotService(backend, settings);

        Layer layer = new Layer("L1", "red", List.of(new RefillCommand(1, "red")));

        service.plot(output(layer));

        List<String> calls = backend.calls;
        // Dip (move, pen down for the dwell, up), then a second pen-down for the swirl.
        assertEquals(List.of("MOVETO 10.000 20.000", "PENDOWN", "PENUP", "PENDOWN"), calls.subList(0, 4));
        // The swirl traces a full circle of the default 2mm radius (17 points = 16 segments closed).
        assertEquals("LINETO 12.000 20.000", calls.get(4)); // angle 0 -> centre + (r, 0)
        assertEquals(17, calls.stream().filter(c -> c.startsWith("LINETO")).count());
        // Swirl returns to centre and lifts, then the layer parks at the origin.
        assertEquals(List.of("MOVETO 10.000 20.000", "PENUP", "PENUP", "MOVETO 0.000 0.000"),
                calls.subList(calls.size() - 4, calls.size()));
    }

    @Test
    void dipUsesStationZDepthWhenConfigured() {
        double[] depth = {Double.NaN};
        FakePlotterBackend backend = new FakePlotterBackend() {
            @Override
            public void pendown(double z) {
                depth[0] = z;
                super.pendown();
            }
        };
        PlotSettings settings = new PlotSettings();
        settings.machineWidth = 100.0;
        settings.machineHeight = 100.0;
        settings.stations.put("red", new StationConfig(10, 20, 7, "simple_dip"));
        PlotService service = new PlotService(backend, settings);

        service.plot(output(new Layer("L1", "red", List.of(new RefillCommand(1, "red")))));

        assertEquals(7.0, depth[0]); // the station's zDown drives the dip depth
    }

    @Test
    void rinseStationCleansBrushBetweenColorLayers() {
        FakePlotterBackend backend = new FakePlotterBackend();
        PlotSettings settings = new PlotSettings();
        settings.machineWidth = 100.0;
        settings.machineHeight = 100.0;
        settings.stations.put("rinse", new StationConfig(50, 50, 30, "rinse"));
        PlotService service = new PlotService(backend, settings);
        List<String> logs = new ArrayList<>();
        service.setLogCallback(logs::add);

        Layer l1 = new Layer("L1", "s", "#ff0000", List.of(new DrawCommand(1, List.of(new Point(5, 5)))));
        Layer l2 = new Layer("L2", "s", "#00ff00", List.of(new DrawCommand(2, List.of(new Point(6, 6)))));

        service.plot(output(l1, l2));

        // The brush is rinsed exactly once: before the second (new-colour) layer, not the first.
        long rinses = logs.stream().filter(l -> l.startsWith("--- Rinsing brush")).count();
        assertEquals(1, rinses);
        assertTrue(backend.calls.contains("MOVETO 50.000 50.000"));
    }

    @Test
    void refillFallsBackToDefaultStationForUnknownId() {
        FakePlotterBackend backend = new FakePlotterBackend();
        PlotSettings settings = new PlotSettings();
        settings.machineWidth = 100.0;
        settings.machineHeight = 100.0;
        settings.stations.put("default_station", new StationConfig(1, 2, 20, "simple_dip"));
        PlotService service = new PlotService(backend, settings);

        List<String> logs = new ArrayList<>();
        service.setLogCallback(logs::add);

        Layer layer = new Layer("L1", "unknown", List.of(new RefillCommand(1, "unknown")));

        service.plot(output(layer));

        assertEquals(List.of("MOVETO 1.000 2.000", "PENDOWN", "PENUP",
                "PENUP", "MOVETO 0.000 0.000"), backend.calls);
        assertTrue(logs.stream().anyMatch(l -> l.contains("Unknown station")));
    }

    @Test
    void layerGateIsAwaitedBeforeEachLayer() throws InterruptedException {
        FakePlotterBackend backend = new FakePlotterBackend();
        PlotSettings settings = new PlotSettings();
        settings.machineWidth = 100.0;
        settings.machineHeight = 100.0;
        PlotService service = new PlotService(backend, settings);

        AtomicInteger gateCount = new AtomicInteger();
        service.setLayerGate(layer -> gateCount.incrementAndGet());

        Layer l1 = new Layer("L1", "default_station", List.of(new MoveCommand(1, 1, 1)));
        Layer l2 = new Layer("L2", "default_station", List.of(new MoveCommand(2, 2, 2)));

        service.plot(output(l1, l2));

        assertEquals(2, gateCount.get());
    }

    @Test
    void cancelStopsExecutionBeforeNextCommand() {
        FakePlotterBackend backend = new FakePlotterBackend();
        PlotSettings settings = new PlotSettings();
        settings.machineWidth = 100.0;
        settings.machineHeight = 100.0;
        PlotService service = new PlotService(backend, settings);

        List<Command> commands = new ArrayList<>();
        commands.add(new MoveCommand(1, 1, 1));
        commands.add(new MoveCommand(2, 2, 2));
        Layer layer = new Layer("L1", "default_station", commands);

        service.setLayerGate(l -> service.cancel());

        service.plot(output(layer));

        // Cancelling before any command still lifts the pen as a safe-state guarantee.
        assertEquals(List.of("PENUP"), backend.calls);
    }

    @Test
    void cancelMidDrawLiftsThePen() {
        FakePlotterBackend backend = new FakePlotterBackend();
        PlotSettings settings = new PlotSettings();
        settings.machineWidth = 100.0;
        settings.machineHeight = 100.0;
        PlotService service = new PlotService(backend, settings);

        // Cancel as soon as the layer starts drawing, simulating a Stop mid-plot.
        service.setLayerStartedCallback(l -> service.cancel());

        List<Command> commands = new ArrayList<>();
        commands.add(new DrawCommand(1, List.of(new Point(5, 5), new Point(6, 6))));
        Layer layer = new Layer("L1", "default_station", commands);

        service.plot(output(layer));

        // No draw should have happened, and the pen must end up lifted (not left on the paper).
        assertEquals(List.of("PENUP"), backend.calls);
    }
}
