package org.trostheide.gantry.app.plot;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.command.*;
import org.trostheide.gantry.pipeline.optimize.MultipassStage;
import org.trostheide.gantry.plotter.*;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;

class ReviewedRestartTest {
    @TempDir Path directory;

    private PlotJobHistory.Job job() {
        var output =
                new ProcessorOutput(
                        new Metadata(
                                "test", Instant.EPOCH, "pen", "mm", 4, new Bounds(10, 10, 40, 40)),
                        List.of(
                                new Layer(
                                        "one",
                                        "pen",
                                        List.of(
                                                new MoveCommand(1, 10, 10),
                                                new DrawCommand(
                                                        2,
                                                        List.of(
                                                                new Point(20, 10),
                                                                new Point(20, 20))))),
                                new Layer(
                                        "two",
                                        "pen",
                                        List.of(
                                                new MoveCommand(3, 30, 30),
                                                new DrawCommand(
                                                        4,
                                                        List.of(
                                                                new Point(40, 30),
                                                                new Point(40, 40)))))));
        var settings = new PlotSettings();
        settings.machineWidth = 100.0;
        settings.machineHeight = 100.0;
        settings.originBottom = true;
        settings.alignmentOffsetOverride = new double[] {0, 0};
        return new PlotJobHistory.Job(
                Instant.EPOCH, MultipassStage.apply(output, 2), settings, List.of());
    }

    @Test
    void preservesImplicitStartsPassIdentityAndExplicitPenUpPositioningInBothDirections() {
        var store = new ReviewedRestart.Store(directory.resolve("restart.json").toFile());
        store.capture(job(), new GantryConfig());
        var strokes = store.snapshot().strokes();
        assertEquals(4, strokes.size());
        assertEquals(1, strokes.get(0).occurrence());
        assertEquals(2, strokes.get(1).occurrence());
        assertNotEquals(strokes.get(0).commandIndex(), strokes.get(1).commandIndex());
        store.select(2, false);
        var execution = ReviewedRestart.execution(store.snapshot());
        assertEquals(1, execution.output().layers().size());
        assertInstanceOf(MoveCommand.class, execution.output().layers().get(0).commands().get(0));
        var first = (MoveCommand) execution.output().layers().get(0).commands().get(0);
        assertEquals(30, first.x);
        assertEquals(30, first.y);
        store.select(3, true);
        execution = ReviewedRestart.execution(store.snapshot());
        assertEquals(2, execution.output().layers().size());
        first = (MoveCommand) execution.output().layers().get(0).commands().get(0);
        assertEquals(40, first.x);
        assertEquals(40, first.y);
        var draw = (DrawCommand) execution.output().layers().get(0).commands().get(1);
        assertEquals(new Point(30, 30), draw.points.get(draw.points.size() - 1));
        assertTrue(execution.settings().stations.isEmpty());
        assertEquals(0, execution.settings().dataRotation);
    }

    @Test
    void persistsUncertainSubmissionAndOnlyConfirmsCompletedLayerBarriers() throws Exception {
        Path file = directory.resolve("restart.json");
        var config = new GantryConfig();
        var store = new ReviewedRestart.Store(file.toFile());
        store.capture(job(), config);
        String immutableSource = Files.readString(Path.of(file + ".job.json"));
        store.begin();
        store.progress(
                new PlotService.StrokeProgress("one", 2, PlotService.StrokeProgressPhase.STARTED));
        store.progress(
                new PlotService.StrokeProgress("one", 2, PlotService.StrokeProgressPhase.ACCEPTED));
        assertTrue(store.snapshot().confirmed().isEmpty());
        assertEquals(immutableSource, Files.readString(Path.of(file + ".job.json")));
        assertFalse(Files.readString(file).contains("points"));
        var restored = new ReviewedRestart.Store(file.toFile());
        assertNull(restored.loadError());
        assertEquals("RUNNING", restored.snapshot().state());
        assertEquals(1, restored.snapshot().acknowledged());
        restored.layerCompleted();
        assertEquals(List.of(0), restored.snapshot().confirmed());
        restored.finish(false);
        restored = new ReviewedRestart.Store(file.toFile());
        assertEquals("STOPPED_UNCERTAIN", restored.snapshot().state());
        restored.compatible(config, job());
        config.gcode.feedRateDraw++;
        var stale = restored;
        assertThrows(IllegalArgumentException.class, () -> stale.compatible(config, null));
        Path sourceFile = Path.of(file + ".job.json");
        String corrupt = Files.readString(sourceFile).replace("test", "modified");
        Files.writeString(sourceFile, corrupt);
        var invalid = new ReviewedRestart.Store(file.toFile());
        assertNull(invalid.snapshot());
        assertNotNull(invalid.loadError());
    }

    @Test
    void blocksChangedGeometryPensOrderAndWatercolor() {
        var config = new GantryConfig();
        var store = new ReviewedRestart.Store(directory.resolve("restart.json").toFile());
        store.capture(job(), config);
        var original = job();
        var changed =
                new PlotJobHistory.Job(
                        Instant.EPOCH,
                        new ProcessorOutput(
                                original.output().metadata(),
                                List.of(
                                        original.output().layers().get(1),
                                        original.output().layers().get(0))),
                        original.settings());
        assertThrows(IllegalArgumentException.class, () -> store.compatible(config, changed));
        var wet = original.settings();
        wet.stations.put("rinse", new StationConfig(50, 50, 0, "rinse", null, 20, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ReviewedRestart.strokes(
                                new PlotJobHistory.Job(Instant.EPOCH, original.output(), wet)));
    }

    @Test
    void partialCoordinatesRemainIdenticalAcrossAllAxisMappings() {
        for (int flags = 0; flags < 16; flags++)
            for (int rotation : new int[] {0, 90, 180, 270}) {
                var original = job();
                var settings = original.settings();
                settings.swapXY = (flags & 1) != 0;
                settings.invertX = (flags & 2) != 0;
                settings.invertY = (flags & 4) != 0;
                settings.flipY = (flags & 8) != 0;
                settings.dataRotation = rotation;
                var source = new PlotJobHistory.Job(Instant.EPOCH, original.output(), settings);
                var store = new ReviewedRestart.Store(directory.resolve("matrix.json").toFile());
                store.capture(source, new GantryConfig());
                store.select(2, false);
                var execution = ReviewedRestart.execution(store.snapshot());
                var projected =
                        new PlotService(null, execution.settings())
                                .projectArtwork(execution.output());
                assertEquals(
                        store.snapshot().strokes().get(2).points(),
                        projected.layers().get(0).strokes().get(0));
            }
    }

    @Test
    void serialAlarmLeavesSubmittedStrokeUncertainAndDoesNotConfirmItsLayer() {
        var options = new GcodeOptions();
        options.bootDelayMillis = 0;
        options.penDownDelayMillis = 0;
        options.positionPollIntervalSeconds = .01;
        options.responseTimeoutSeconds = 1;
        var transport = new SerialSimulatorTransport(options);
        var backend = new GcodeBackend(options, (port, baud) -> transport);
        var source = job();
        var store = new ReviewedRestart.Store(directory.resolve("alarm.json").toFile());
        store.capture(source, new GantryConfig());
        store.begin();
        var service = new PlotService(backend, source.settings());
        service.setLayerCompletedCallback(layer -> store.layerCompleted());
        service.setStrokeProgressCallback(
                progress -> {
                    store.progress(progress);
                    if (progress.phase() == PlotService.StrokeProgressPhase.STARTED)
                        transport.armFault(SerialSimulatorTransport.Fault.ALARM);
                });
        try {
            assertTrue(backend.connect());
            assertThrows(GcodeBackendException.class, () -> service.plot(source.output()));
            store.finish(false);
            assertEquals(1, store.snapshot().started());
            assertTrue(store.snapshot().confirmed().isEmpty());
            var restored = new ReviewedRestart.Store(directory.resolve("alarm.json").toFile());
            assertEquals("STOPPED_UNCERTAIN", restored.snapshot().state());
            assertTrue(restored.snapshot().confirmed().isEmpty());
        } finally {
            backend.disconnect();
        }
    }

    @Test
    void fullIdentityRejectsInternalGeometryHiddenLayersPassesPensAndMappingChanges() {
        var config = new GantryConfig();
        var source = job();
        var store = new ReviewedRestart.Store(directory.resolve("identity.json").toFile());
        store.capture(source, config);
        var layers = new java.util.ArrayList<>(source.output().layers());
        var commands = new java.util.ArrayList<>(layers.get(0).commands());
        var old = (DrawCommand) commands.get(1);
        var points = new java.util.ArrayList<>(old.points);
        points.set(0, new Point(points.get(0).x() + .1, points.get(0).y()));
        commands.set(1, new DrawCommand(old.id, points));
        layers.set(0, layers.get(0).withCommands(commands));
        var geometry =
                new PlotJobHistory.Job(
                        Instant.EPOCH,
                        new ProcessorOutput(source.output().metadata(), layers),
                        source.settings());
        assertThrows(
                IllegalArgumentException.class,
                () -> store.compatible(config, geometry),
                "Same count and final endpoints still require full geometry identity");
        var hidden =
                new PlotJobHistory.Job(
                        Instant.EPOCH,
                        new ProcessorOutput(
                                source.output().metadata(),
                                List.of(source.output().layers().get(0))),
                        source.settings());
        assertThrows(IllegalArgumentException.class, () -> store.compatible(config, hidden));
        var passes =
                new PlotJobHistory.Job(
                        Instant.EPOCH, MultipassStage.apply(source.output(), 2), source.settings());
        assertThrows(IllegalArgumentException.class, () -> store.compatible(config, passes));
        var pens =
                new PlotJobHistory.Job(
                        Instant.EPOCH,
                        source.output(),
                        source.settings(),
                        List.of(new PlotJobHistory.PenSnapshot("pen", "different", "#112233", .4)));
        assertThrows(IllegalArgumentException.class, () -> store.compatible(config, pens));
        var mapping = source.settings();
        mapping.invertX = !mapping.invertX;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        store.compatible(
                                config,
                                new PlotJobHistory.Job(Instant.EPOCH, source.output(), mapping)));
    }

    @Test
    void cancelledThreadStillPersistsFinalUncertaintyAndRetainsItsInterrupt() {
        var file = directory.resolve("cancelled.json");
        var store = new ReviewedRestart.Store(file.toFile());
        store.capture(job(), new GantryConfig());
        store.begin();
        try {
            Thread.currentThread().interrupt();
            assertDoesNotThrow(() -> store.finish(false));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        var restored = new ReviewedRestart.Store(file.toFile());
        assertEquals("STOPPED_UNCERTAIN", restored.snapshot().state());
        assertTrue(restored.snapshot().confirmed().isEmpty());
    }
}
