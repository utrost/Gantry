package org.trostheide.gantry.app.plot;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.model.command.RefillCommand;
import org.trostheide.gantry.plotter.GcodeOptions;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeEstimatorTest {

    private static ProcessorOutput output(Layer... layers) {
        Metadata meta = new Metadata("test", Instant.EPOCH, "station", "mm", 0, Bounds.empty());
        return new ProcessorOutput(meta, List.of(layers));
    }

    @Test
    void travelAndDrawDistancesUseTheirRespectiveFeedRates() {
        GcodeOptions gcode = new GcodeOptions();
        gcode.feedRateTravel = 6000; // 100 mm/s
        gcode.feedRateDraw = 3000;  // 50 mm/s

        Layer layer = new Layer("L1", "default_station", List.of(
                new MoveCommand(1, 100, 0),
                new DrawCommand(2, List.of(new Point(100, 0), new Point(150, 0)))));

        TimeEstimator.PlotEstimate est = TimeEstimator.estimate(output(layer), gcode, Map.of());

        assertEquals(1, est.layers().size());
        TimeEstimator.LayerEstimate le = est.layers().get(0);
        assertEquals(100.0, le.travelDistanceMm(), 1e-9);
        assertEquals(50.0, le.drawDistanceMm(), 1e-9);
        // 100mm travel @ 100mm/s = 1s, 50mm draw @ 50mm/s = 1s, +1 pen-down @ 0.15s
        assertEquals(2.15, le.estimatedSeconds(), 1e-6);
        assertEquals(2.15, est.totalSeconds(), 1e-6);
    }

    @Test
    void penDownOverheadIsChargedOncePerDrawCommand() {
        GcodeOptions gcode = new GcodeOptions();
        gcode.feedRateDraw = 6000; // 100 mm/s, so distance-driven time is negligible/zero here

        // Two separate strokes (e.g. two hatch lines), each its own DrawCommand/pen-down cycle.
        Layer layer = new Layer("L1", "default_station", List.of(
                new DrawCommand(1, List.of(new Point(0, 0), new Point(0, 0))),
                new DrawCommand(2, List.of(new Point(0, 0), new Point(0, 0)))));

        TimeEstimator.PlotEstimate est = TimeEstimator.estimate(output(layer), gcode, Map.of());

        // 2 pen-downs @ 0.15s each, zero-length strokes contribute no draw distance.
        assertEquals(0.30, est.layers().get(0).estimatedSeconds(), 1e-6);
    }

    @Test
    void refillAddsTravelToStationAndFixedOverhead() {
        GcodeOptions gcode = new GcodeOptions();
        gcode.feedRateTravel = 6000; // 100 mm/s

        Layer layer = new Layer("L1", "station_a", List.of(
                new RefillCommand(1, "station_a")));

        Map<String, StationConfig> stations = Map.of("station_a", new StationConfig(50, 0, 0, "simple_dip"));
        TimeEstimator.PlotEstimate est = TimeEstimator.estimate(output(layer), gcode, stations);

        TimeEstimator.LayerEstimate le = est.layers().get(0);
        assertEquals(50.0, le.travelDistanceMm(), 1e-9);
        assertEquals(1, le.refillCount());
        // 50mm @ 100mm/s = 0.5s travel + 0.5s dip overhead
        assertEquals(1.0, le.estimatedSeconds(), 1e-6);
    }

    @Test
    void unknownStationFallsBackToDefaultStation() {
        GcodeOptions gcode = new GcodeOptions();
        Layer layer = new Layer("L1", "missing", List.of(new RefillCommand(1, "missing")));
        Map<String, StationConfig> stations = Map.of("default_station", new StationConfig(10, 0, 0, "simple_dip"));

        TimeEstimator.PlotEstimate est = TimeEstimator.estimate(output(layer), gcode, stations);

        assertEquals(10.0, est.layers().get(0).travelDistanceMm(), 1e-9);
    }

    @Test
    void formatSwitchesBetweenMinSecAndHourMinSec() {
        assertEquals("0:05", TimeEstimator.format(5));
        assertEquals("1:05", TimeEstimator.format(65));
        assertEquals("1:00:00", TimeEstimator.format(3600));
    }

    @Test
    void multipleLayersSumIntoTotal() {
        GcodeOptions gcode = new GcodeOptions();
        gcode.feedRateTravel = 6000;

        Layer l1 = new Layer("L1", "default_station", List.of(new MoveCommand(1, 100, 0)));
        Layer l2 = new Layer("L2", "default_station", List.of(new MoveCommand(2, 200, 0)));

        TimeEstimator.PlotEstimate est = TimeEstimator.estimate(output(l1, l2), gcode, Map.of());

        assertEquals(2, est.layers().size());
        assertTrue(est.totalSeconds() > est.layers().get(0).estimatedSeconds());
    }
}
