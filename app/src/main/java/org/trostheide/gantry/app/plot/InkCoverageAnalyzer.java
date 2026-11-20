package org.trostheide.gantry.app.plot;

import org.trostheide.gantry.model.ProcessorOutput;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

/** Advisory, bounded-memory raster of the prepared machine-space pen footprints. */
public final class InkCoverageAnalyzer {
    private static final int MAX_TOTAL_CELLS = 12_000_000;
    private static final int MAX_GRID_CELLS = 1_000_000;

    public record PenSpec(String id, String name, double widthMm) {
        public PenSpec {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Pen id is required");
            name = name == null || name.isBlank() ? id : name;
            if (!Double.isFinite(widthMm) || widthMm <= 0) widthMm = .3;
        }
    }

    public record Result(double cellMm, int columns, int rows, List<String> penNames,
                         byte[] aggregate, List<byte[]> perPen, boolean clipped,
                         double[] artworkBounds, MachinePageProjection page) {
        public byte[] counts(int penIndex) { return penIndex < 0 ? aggregate : perPen.get(penIndex); }
        public int maximum(int penIndex) {
            int max = 0;
            for (byte count : counts(penIndex)) max = Math.max(max, Byte.toUnsignedInt(count));
            return max;
        }
        public int cellsAtLeast(int penIndex, int threshold) {
            int cells = 0;
            for (byte count : counts(penIndex)) if (Byte.toUnsignedInt(count) >= threshold) cells++;
            return cells;
        }
    }

    private InkCoverageAnalyzer() { }

    public static Result analyze(ProcessorOutput output, PlotSettings settings, List<PenSpec> layerPens) {
        if (output.layers().size() != layerPens.size())
            throw new IllegalArgumentException("Every prepared layer needs a pen");
        Map<String, Integer> penById = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();
        int[] layerPen = new int[layerPens.size()];
        for (int i = 0; i < layerPens.size(); i++) {
            PenSpec pen = layerPens.get(i);
            Integer index = penById.get(pen.id());
            if (index == null) {
                index = names.size();
                penById.put(pen.id(), index);
                names.add(pen.name());
            }
            layerPen[i] = index;
        }
        double bedW = settings.resolveMachineWidth();
        double bedH = settings.resolveMachineHeight();
        if (!Double.isFinite(bedW) || !Double.isFinite(bedH) || bedW <= 0 || bedH <= 0)
            throw new IllegalArgumentException("Machine bed dimensions must be positive");
        int budget = Math.max(1, Math.min(MAX_GRID_CELLS, MAX_TOTAL_CELLS / (names.size() + 1)));
        double cell = Math.max(.5, Math.sqrt(bedW * bedH / budget));
        int columns = Math.max(1, (int) Math.ceil(bedW / cell));
        int rows = Math.max(1, (int) Math.ceil(bedH / cell));
        while ((long) columns * rows > budget) {
            cell *= 1.05;
            columns = Math.max(1, (int) Math.ceil(bedW / cell));
            rows = Math.max(1, (int) Math.ceil(bedH / cell));
        }
        int cells = columns * rows;
        byte[] aggregate = new byte[cells];
        List<byte[]> perPen = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) perPen.add(new byte[cells]);
        PlotService service = new PlotService(null, settings);
        double[] artworkBounds = service.computeArtworkBounds(output);
        boolean[] clipped = {artworkBounds != null && (artworkBounds[0] < 0 || artworkBounds[1] > bedW
                || artworkBounds[2] < 0 || artworkBounds[3] > bedH)};

        BitSet footprint = new BitSet(cells);
        final int gridColumns = columns, gridRows = rows;
        final double gridCell = cell;
        final int[] previousStroke = {-1}, previousPen = {-1};
        Runnable flush = () -> {
            if (previousPen[0] < 0) return;
            byte[] selected = perPen.get(previousPen[0]);
            for (int index = footprint.nextSetBit(0); index >= 0; index = footprint.nextSetBit(index + 1)) {
                if (Byte.toUnsignedInt(selected[index]) < 255) selected[index]++;
                if (Byte.toUnsignedInt(aggregate[index]) < 255) aggregate[index]++;
            }
            footprint.clear();
        };
        service.forEachDrawSegment(output, segment -> {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            clipped[0] |= segment.clamped();
            int pen = layerPen[segment.layerIndex()];
            if (segment.strokeOrdinal() != previousStroke[0]) {
                flush.run();
                previousStroke[0] = segment.strokeOrdinal();
                previousPen[0] = pen;
            }
            stamp(segment, layerPens.get(segment.layerIndex()).widthMm() / 2,
                    gridCell, gridColumns, gridRows, footprint);
        });
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
        flush.run();
        return new Result(cell, columns, rows, List.copyOf(names), aggregate, List.copyOf(perPen),
                clipped[0], artworkBounds, MachinePageProjection.from(settings));
    }

    private static void stamp(PlotService.DrawSegment segment, double radius, double cell,
                              int columns, int rows, BitSet footprint) {
        // A cell touched by the circular nib counts once per stroke, including polyline joints.
        double reach = radius + cell * Math.sqrt(2) / 2;
        double dx = segment.x2() - segment.x1(), dy = segment.y2() - segment.y1();
        double lengthSquared = dx * dx + dy * dy;
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(lengthSquared) / (cell * .5)));
        for (int step = 0; step <= steps; step++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            double sx = segment.x1() + dx * step / steps;
            double sy = segment.y1() + dy * step / steps;
            int minX = Math.max(0, (int) Math.floor((sx - reach) / cell));
            int maxX = Math.min(columns - 1, (int) Math.ceil((sx + reach) / cell));
            int minY = Math.max(0, (int) Math.floor((sy - reach) / cell));
            int maxY = Math.min(rows - 1, (int) Math.ceil((sy + reach) / cell));
            for (int y = minY; y <= maxY; y++) {
                double cy = (y + .5) * cell;
                for (int x = minX; x <= maxX; x++) {
                    int index = y * columns + x;
                    if (footprint.get(index)) continue;
                    double cx = (x + .5) * cell;
                    double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1,
                            ((cx - segment.x1()) * dx + (cy - segment.y1()) * dy) / lengthSquared));
                    double px = segment.x1() + t * dx, py = segment.y1() + t * dy;
                    if (Math.hypot(cx - px, cy - py) <= reach) footprint.set(index);
                }
            }
        }
    }
}
