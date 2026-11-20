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
import org.trostheide.gantry.pipeline.optimize.MultipassStage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InkCoverageAnalyzerTest {
    private static PlotSettings settings() {
        PlotSettings settings = new PlotSettings();
        settings.machineWidth = 20.0;
        settings.machineHeight = 20.0;
        return settings;
    }

    private static ProcessorOutput output(Layer... layers) {
        return new ProcessorOutput(new Metadata("test", Instant.EPOCH, "station", "mm", 0, Bounds.empty()),
                List.of(layers));
    }

    private static Layer line(String id, int firstCommand, double x1, double y1, double x2, double y2) {
        return new Layer(id, "station", List.of(new MoveCommand(firstCommand, x1, y1),
                new DrawCommand(firstCommand + 1, List.of(new Point(x2, y2)))));
    }

    @Test void crossingsAndMultipassIncreaseOverlapWithoutDoubleCountingOneStroke() {
        ProcessorOutput crossing = output(line("horizontal", 1, 2, 10, 18, 10),
                line("vertical", 3, 10, 2, 10, 18));
        List<InkCoverageAnalyzer.PenSpec> pens = List.of(new InkCoverageAnalyzer.PenSpec("a", "Blue", 1),
                new InkCoverageAnalyzer.PenSpec("b", "Red", 1));
        var result = InkCoverageAnalyzer.analyze(crossing, settings(), pens);
        assertEquals(2, result.maximum(-1));
        assertEquals(1, result.maximum(0));
        assertEquals(1, result.maximum(1));
        assertTrue(result.cellsAtLeast(-1, 2) > 0);

        var repeated = InkCoverageAnalyzer.analyze(MultipassStage.apply(output(line("line", 1, 2, 10, 18, 10)), 3),
                settings(), List.of(new InkCoverageAnalyzer.PenSpec("a", "Blue", 1)));
        assertEquals(3, repeated.maximum(-1));
        assertEquals(3, repeated.maximum(0));
    }

    @Test void widthAndPreparedScalingChangeFootprintButNotPhysicalNib() {
        ProcessorOutput source = output(line("line", 1, 2, 10, 8, 10));
        var thin = InkCoverageAnalyzer.analyze(source, settings(),
                List.of(new InkCoverageAnalyzer.PenSpec("a", "Thin", .2)));
        var wide = InkCoverageAnalyzer.analyze(source, settings(),
                List.of(new InkCoverageAnalyzer.PenSpec("a", "Wide", 2)));
        assertTrue(wide.cellsAtLeast(-1, 1) > thin.cellsAtLeast(-1, 1));

        ProcessorOutput scaled = output(line("line", 1, 4, 10, 16, 10));
        var scaledResult = InkCoverageAnalyzer.analyze(scaled, settings(),
                List.of(new InkCoverageAnalyzer.PenSpec("a", "Thin", .2)));
        assertTrue(scaledResult.cellsAtLeast(-1, 1) > thin.cellsAtLeast(-1, 1));
        assertEquals(thin.cellMm(), scaledResult.cellMm());
    }

    @Test void clippedBoundsAgreeWithPlotServiceAndEmptyJobStaysEmpty() {
        ProcessorOutput outside = output(line("outside", 1, -5, 10, 25, 10));
        PlotSettings settings = settings();
        var result = InkCoverageAnalyzer.analyze(outside, settings,
                List.of(new InkCoverageAnalyzer.PenSpec("a", "Black", 1)));
        assertTrue(result.clipped());
        assertArrayEquals(new PlotService(null, settings).computeArtworkBounds(outside), result.artworkBounds());
        assertTrue(result.cellsAtLeast(-1, 1) > 0);

        var empty = InkCoverageAnalyzer.analyze(output(new Layer("empty", "station", List.of())), settings,
                List.of(new InkCoverageAnalyzer.PenSpec("a", "Black", 1)));
        assertEquals(0, empty.maximum(-1));
        assertNull(empty.artworkBounds());
    }

    @Test void segmentStreamUsesSameTransformedClampedCoordinatesAsPlot() {
        ProcessorOutput job = output(line("line", 1, -3, 4, 10, 7));
        PlotSettings settings = settings();
        settings.invertX = true;
        FakePlotterBackend backend = new FakePlotterBackend();
        PlotService service = new PlotService(backend, settings);
        List<PlotService.DrawSegment> segments = new ArrayList<>();
        service.forEachDrawSegment(job, segments::add);
        service.plot(job);
        assertEquals(1, segments.size());
        assertEquals(String.format(java.util.Locale.ROOT, "MOVETO %.3f %.3f",
                segments.get(0).x1(), segments.get(0).y1()), backend.calls.get(0));
        assertEquals(String.format(java.util.Locale.ROOT, "LINETO %.3f %.3f",
                segments.get(0).x2(), segments.get(0).y2()), backend.calls.get(1));
    }

    @Test void exportAndCoverageTrackTheExecutedHeadAfterRefills() {
        PlotSettings settings = settings();
        settings.stations.put("paint", new StationConfig(3, 4, 0, "simple_dip"));
        ProcessorOutput job = output(new Layer("paint", "paint", List.of(
                new MoveCommand(1, 5, 6),
                new DrawCommand(2, List.of(new Point(7, 8))),
                new RefillCommand(3, "paint"),
                new DrawCommand(4, List.of(new Point(11, 12))))));
        List<PlotService.DrawSegment> segments = new ArrayList<>();
        List<PlotService.DrawSegment> executed = new ArrayList<>();
        FakePlotterBackend backend = new FakePlotterBackend() {
            double x, y;
            @Override public void moveto(double nx, double ny) { x = nx; y = ny; }
            @Override public void lineto(double nx, double ny) {
                executed.add(new PlotService.DrawSegment(0, executed.size() + 1, x, y, nx, ny, false));
                x = nx; y = ny;
            }
        };
        PlotService service = new PlotService(backend, settings);
        service.forEachDrawSegment(job, segments::add);
        service.plot(job);
        assertEquals(executed, segments);
        assertEquals(new Point(3, 4), service.projectArtwork(job).layers().get(0).strokes().get(1).get(0));
    }

    @Test void nextLayerStartsAtRinseStationWhenNoMovePrecedesDrawing() {
        PlotSettings settings = settings();
        settings.stations.put("rinse", new StationConfig(3, 4, 0, "rinse"));
        ProcessorOutput job = output(line("first", 1, 5, 6, 7, 8),
                new Layer("second", "paint", List.of(new DrawCommand(3, List.of(new Point(11, 12))))));
        List<PlotService.DrawSegment> segments = new ArrayList<>();
        PlotService service = new PlotService(new FakePlotterBackend(), settings);
        service.forEachDrawSegment(job, segments::add);
        assertEquals(3, segments.get(1).x1());
        assertEquals(4, segments.get(1).y1());
        assertEquals(List.of(new Point(3, 4), new Point(11, 12)),
                service.projectArtwork(job).layers().get(1).strokes().get(0));
    }

    @Test void largeMultiPenBedKeepsRasterAllocationBounded() {
        PlotSettings large = settings();
        large.machineWidth = 2000.0;
        large.machineHeight = 2000.0;
        List<Layer> layers = new ArrayList<>();
        List<InkCoverageAnalyzer.PenSpec> pens = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            layers.add(line("layer-" + i, i * 2 + 1, 100, i * 5 + 10, 120, i * 5 + 10));
            pens.add(new InkCoverageAnalyzer.PenSpec("pen-" + i, "Pen " + i, .5));
        }
        var result = InkCoverageAnalyzer.analyze(output(layers.toArray(Layer[]::new)), large, pens);
        assertTrue((long) result.columns() * result.rows() * (result.perPen().size() + 1) <= 12_000_000);
        assertEquals(12, result.perPen().size());
    }
}
