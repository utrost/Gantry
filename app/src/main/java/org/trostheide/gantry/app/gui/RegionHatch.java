package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.svgtoolbox.Config;
import org.trostheide.gantry.svgtoolbox.HatchStyle;
import org.trostheide.gantry.svgtoolbox.patterns.CrossHatchPattern;
import org.trostheide.gantry.svgtoolbox.patterns.DotHatchPattern;
import org.trostheide.gantry.svgtoolbox.patterns.HatchPattern;
import org.trostheide.gantry.svgtoolbox.patterns.LinearHatchPattern;
import org.trostheide.gantry.svgtoolbox.patterns.WaveHatchPattern;
import org.trostheide.gantry.svgtoolbox.patterns.ZigZagHatchPattern;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry/model helpers for click-to-hatch (Phase 10 Tier 3): turn a clicked region into
 * plottable hatch strokes and splice them into the command model. Kept free of Swing so it can be
 * unit-tested headlessly; {@link VisualizationPanel} supplies the clicked region and
 * {@code PlotterPanel} applies the result.
 */
final class RegionHatch {

    private RegionHatch() {
    }

    /** The hatch patterns offered by the click-to-hatch style picker, in menu order. */
    static final List<String> PATTERNS = List.of("linear", "cross", "zigzag", "wave", "dot");

    /** A shared, default toolbox config; the patterns derive their geometry from {@link HatchStyle}. */
    private static final Config DEFAULT_CONFIG = new Config.Builder().build();

    /** Flatness (mm) for converting curved/closed pattern shapes (waves, dots) to polylines. */
    private static final double FLATNESS = 0.25;

    /**
     * Hatch fill for {@code region} (closed, in model mm space) as plottable polyline strokes,
     * numbered from {@code startId}. Every pattern's output — straight lines, zig-zag/wave paths,
     * or dots — is flattened into polylines. Empty if the region is too small for the spacing.
     *
     * @param pattern one of {@link #PATTERNS}; unknown values fall back to linear
     */
    static List<DrawCommand> hatchCommands(java.awt.geom.Path2D region, String pattern,
                                           double angleDeg, double gapMm, int startId) {
        HatchPattern hp = patternFor(pattern);
        HatchStyle style = new HatchStyle(angleDeg, gapMm, pattern == null ? "linear" : pattern);
        List<Shape> shapes = hp.generate(region, DEFAULT_CONFIG, style);

        List<DrawCommand> out = new ArrayList<>();
        int[] id = {startId};
        for (Shape s : shapes) {
            appendFlattened(out, s, id);
        }
        return out;
    }

    private static HatchPattern patternFor(String name) {
        return switch (name == null ? "linear" : name.toLowerCase()) {
            case "cross" -> new CrossHatchPattern();
            case "zigzag" -> new ZigZagHatchPattern();
            case "wave" -> new WaveHatchPattern();
            case "dot" -> new DotHatchPattern();
            default -> new LinearHatchPattern();
        };
    }

    /** Flattens any {@link Shape} into one or more polyline draw strokes (split on subpath moves). */
    private static void appendFlattened(List<DrawCommand> out, Shape s, int[] id) {
        PathIterator pi = s.getPathIterator(null, FLATNESS);
        double[] c = new double[6];
        List<Point> cur = new ArrayList<>();
        while (!pi.isDone()) {
            switch (pi.currentSegment(c)) {
                case PathIterator.SEG_MOVETO -> {
                    flush(out, cur, id);
                    cur = new ArrayList<>();
                    cur.add(new Point(c[0], c[1]));
                }
                case PathIterator.SEG_LINETO -> cur.add(new Point(c[0], c[1]));
                case PathIterator.SEG_CLOSE -> {
                    if (!cur.isEmpty()) {
                        cur.add(new Point(cur.get(0).x(), cur.get(0).y()));
                    }
                    flush(out, cur, id);
                    cur = new ArrayList<>();
                }
                default -> { /* flattened iterator yields no QUAD/CUBIC */ }
            }
            pi.next();
        }
        flush(out, cur, id);
    }

    private static void flush(List<DrawCommand> out, List<Point> pts, int[] id) {
        if (pts.size() >= 2) {
            out.add(new DrawCommand(id[0]++, List.copyOf(pts)));
        }
    }

    /** Outcome of {@link #removeHatchInRegion}: the new output, count removed, and the ids removed. */
    record RemoveResult(ProcessorOutput output, int removed, java.util.Set<Integer> removedIds) {
    }

    /**
     * Removes previously-added hatch strokes that fall inside {@code region}: a command is removed
     * when its id is in {@code hatchIds} and its centroid lies within the region. Returns the new
     * output (with {@code metadata.totalCommands} decremented) and what was removed; if nothing
     * matched, the original output is returned with {@code removed == 0}.
     */
    static RemoveResult removeHatchInRegion(ProcessorOutput out, java.util.Set<Integer> hatchIds,
                                            java.awt.geom.Path2D region) {
        java.util.Set<Integer> removed = new java.util.HashSet<>();
        List<Layer> layers = new ArrayList<>();
        for (Layer layer : out.layers()) {
            List<Command> kept = new ArrayList<>();
            for (Command c : layer.commands()) {
                if (c instanceof DrawCommand d && hatchIds.contains(d.id) && centroidInside(d, region)) {
                    removed.add(d.id);
                } else {
                    kept.add(c);
                }
            }
            layers.add(new Layer(layer.id(), layer.stationId(), layer.color(), kept));
        }
        if (removed.isEmpty()) {
            return new RemoveResult(out, 0, removed);
        }
        Metadata m = out.metadata();
        Metadata m2 = new Metadata(m.source(), m.generatedAt(), m.stationId(), m.units(),
                Math.max(0, m.totalCommands() - removed.size()), m.bounds());
        return new RemoveResult(new ProcessorOutput(m2, layers), removed.size(), removed);
    }

    private static boolean centroidInside(DrawCommand d, java.awt.geom.Path2D region) {
        if (d.points.isEmpty()) {
            return false;
        }
        double sx = 0, sy = 0;
        for (Point p : d.points) {
            sx += p.x();
            sy += p.y();
        }
        return region.contains(sx / d.points.size(), sy / d.points.size());
    }

    /** The highest command id across all layers, or 0 if there are none. */
    static int maxCommandId(ProcessorOutput out) {
        int max = 0;
        for (Layer layer : out.layers()) {
            for (Command c : layer.commands()) {
                max = Math.max(max, c.getId());
            }
        }
        return max;
    }

    /**
     * A copy of {@code out} with {@code extra} appended to layer {@code layerIndex}'s commands and
     * {@code metadata.totalCommands} bumped accordingly. Other layers are shared unchanged. If
     * {@code layerIndex} is out of range or {@code extra} is empty, {@code out} is returned as-is.
     */
    static ProcessorOutput appendToLayer(ProcessorOutput out, int layerIndex, List<? extends Command> extra) {
        if (extra.isEmpty() || layerIndex < 0 || layerIndex >= out.layers().size()) {
            return out;
        }
        List<Layer> layers = new ArrayList<>(out.layers());
        Layer target = layers.get(layerIndex);
        List<Command> merged = new ArrayList<>(target.commands());
        merged.addAll(extra);
        layers.set(layerIndex, new Layer(target.id(), target.stationId(), target.color(), merged));

        Metadata m = out.metadata();
        Metadata m2 = new Metadata(m.source(), m.generatedAt(), m.stationId(), m.units(),
                m.totalCommands() + extra.size(), m.bounds());
        return new ProcessorOutput(m2, layers);
    }
}
