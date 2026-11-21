package org.trostheide.gantry.app.plot;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.command.*;
import org.trostheide.gantry.plotter.GcodeOptions;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PreparedEstimateRegressionTest {
    static ProcessorOutput output(Command... commands) {
        return new ProcessorOutput(new Metadata("estimate", Instant.EPOCH, "s", "mm", commands.length, Bounds.empty()),
                List.of(new Layer("ink", "s", List.of(commands))));
    }
    @Test void firstEndpointIsDrawnFromTheCurrentPosition() {
        var options = new GcodeOptions(); options.penDownDelayMillis = 0;
        var estimate = TimeEstimator.estimate(output(new MoveCommand(1, 0, 0),
                new DrawCommand(2, List.of(new Point(3, 4)))), options, Map.of());
        assertEquals(5, estimate.layers().get(0).drawDistanceMm(), 1e-9);
    }
    @Test void refillUsesBothConfiguredDipsAndPenSettling() {
        var options = new GcodeOptions(); options.feedRateTravel = 6000; options.penDownDelayMillis = 100;
        var station = new StationConfig(50, 0, 0, "simple_dip", null, 1500, 2);
        var estimate = TimeEstimator.estimate(output(new RefillCommand(1, "s")), options, Map.of("s", station));
        // 50mm there and back @100mm/s, two 1.5s dips, two .1s settles, two .001s gaps.
        assertEquals(4.202, estimate.totalSeconds(), 1e-9);
    }
    @Test void actualTransformAndClampingDriveTheEstimate() {
        var options = new GcodeOptions(); options.penDownDelayMillis = 0;
        options.feedRateDraw = 60; options.feedRateTravel = 60;
        var settings = new PlotSettings(); settings.machineWidth = 20.; settings.machineHeight = 20.;
        settings.invertX = true; settings.alignmentOffsetOverride = new double[]{5, 0};
        var estimate = TimeEstimator.estimate(output(new MoveCommand(1, 0, 0),
                new DrawCommand(2, List.of(new Point(100, 0)))), options, settings);
        assertEquals(20, estimate.layers().get(0).drawDistanceMm(), 1e-9);
        assertEquals(20, estimate.layers().get(0).travelDistanceMm(), 1e-9);
        assertEquals(40, estimate.totalSeconds(), 1e-9);
    }
    @Test void betweenLayerRinseAndParkAreIncluded() {
        var options = new GcodeOptions(); options.feedRateTravel = 6000; options.penDownDelayMillis = 0;
        var rinse = new StationConfig(10, 0, 0, "simple_dip", null, 1000, 2);
        var settings = new PlotSettings(); settings.stations = Map.of("water", rinse);
        var empty = output().layers().get(0);
        var two = new ProcessorOutput(output().metadata(), List.of(empty, empty));
        var estimate = TimeEstimator.estimate(two, options, settings);
        assertEquals(0, estimate.layers().get(0).estimatedSeconds(), 1e-9);
        assertEquals(1, estimate.layers().get(1).refillCount());
        assertEquals(20, estimate.layers().get(1).travelDistanceMm(), 1e-9);
        assertEquals(2.202, estimate.totalSeconds(), 1e-9);
    }
    @Test void unknownRefillIsSkippedWithoutInventingADwell() {
        var estimate = TimeEstimator.estimate(output(new RefillCommand(1, "missing")), new GcodeOptions(), Map.of());
        assertEquals(0, estimate.totalSeconds(), 1e-9);
        assertEquals(0, estimate.layers().get(0).refillCount());
    }
    @Test void feedOverrideDoesNotScalePenSettlingOrDips() {
        var options = new GcodeOptions(); options.feedRateTravel = 6000; options.penDownDelayMillis = 100;
        var estimate = TimeEstimator.estimate(output(new RefillCommand(1, "s")), options,
                Map.of("s", new StationConfig(50, 0, 0, "simple_dip", null, 1500, 2)));
        assertEquals(5.202, estimate.atSpeedPercent(50), 1e-9);
        assertEquals(3.702, estimate.atSpeedPercent(200), 1e-9);
        var progress = new PlotProgressState(); progress.setEstimate(estimate); progress.start(1, 0);
        assertTrue(progress.liveText(0, 50).endsWith("Est: 0:05"));
    }
    @Test void longControllerDwellsNeverSleepOrConnectDuringEstimation() {
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), () -> {
            var estimate = TimeEstimator.estimate(output(new RefillCommand(1, "s")), new GcodeOptions(),
                    Map.of("s", new StationConfig(0, 0, 0, "simple_dip", null, 60_000, 2)));
            assertTrue(estimate.totalSeconds() > 120);
        });
    }

    @Test void swirlDistanceIncludesBothPolygonLoopsAndTheirRadialApproaches() {
        var options = new GcodeOptions(); options.feedRateDraw = 60; options.penDownDelayMillis = 100;
        var estimate = TimeEstimator.estimate(output(new RefillCommand(1, "s")), options,
                Map.of("s", new StationConfig(0, 0, 0, "dip_swirl", null, 500, 2)));
        double draw = 8 + 128 * Math.sin(Math.PI / 16);
        assertEquals(draw, estimate.layers().get(0).drawDistanceMm(), 1e-9);
        assertEquals(draw + 1.202, estimate.totalSeconds(), 1e-9);
    }

}
