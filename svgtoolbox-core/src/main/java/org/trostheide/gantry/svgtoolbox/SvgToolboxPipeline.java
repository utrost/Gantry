package org.trostheide.gantry.svgtoolbox;

import org.trostheide.gantry.svgtoolbox.core.SvgStatistics;
import org.trostheide.gantry.svgtoolbox.processors.*;
import org.w3c.dom.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the SVGToolBox SVG-to-SVG processor pipeline against a Batik
 * {@link Document}, mutating it in place. Mirrors the processor order of
 * the legacy {@code SvgToolboxRunner}.
 */
public final class SvgToolboxPipeline {

    private SvgToolboxPipeline() {
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int step, int total, String processorName);
    }

    /** Builds the processor pipeline in execution order for the given config. */
    public static List<Processor> buildPipeline(Config config) {
        List<Processor> pipeline = new ArrayList<>();
        pipeline.add(new VisibilityProcessor());
        pipeline.add(new StyleNormalizerProcessor());
        pipeline.add(new RotateProcessor());
        pipeline.add(new StrokeWidthProcessor());
        pipeline.add(new PaletteProcessor());

        // 1. Simplify paths to remove noise
        pipeline.add(new SimplifyProcessor());

        // 2. Generate hatched lines (inside groups with transforms)
        pipeline.add(new HatchProcessor());

        // 2b. Give the (outline + hatch) geometry a hand-drawn waver. Must run before
        // the linesimplify/linemerge/linesort optimizers, which would straighten it out.
        pipeline.add(new HandDrawnProcessor());

        // 3. Plotter path optimization
        pipeline.add(new LinesimplifyProcessor());
        pipeline.add(new LinemergeProcessor());
        pipeline.add(new LinesortProcessor());
        pipeline.add(new ReloopProcessor());

        // 4. Organize into layers
        pipeline.add(new LayerProcessor());

        // 5. Crop
        pipeline.add(new CropProcessor());

        if (config.optimizePaths()) {
            pipeline.add(new PathOptimizeProcessor());
        }

        return pipeline;
    }

    /** Runs the pipeline against {@code doc}, mutating it in place. */
    public static void process(Document doc, Config config) {
        process(doc, config, null);
    }

    /** Runs the pipeline against {@code doc}, mutating it in place, reporting progress. */
    public static void process(Document doc, Config config, ProgressCallback progress) {
        List<Processor> pipeline = buildPipeline(config);

        int total = pipeline.size();
        int step = 0;
        for (Processor p : pipeline) {
            step++;
            if (progress != null) {
                progress.onProgress(step, total, p.getClass().getSimpleName().replace("Processor", ""));
            }
            p.process(doc, config);
        }

        if (config.printStats()) {
            SvgStatistics.Stats stats = SvgStatistics.analyze(doc);
            System.out.println("--- Statistics ---");
            System.out.println("Elements: " + stats.elementCount());
            System.out.printf("Total Length: %.2f meters%n", stats.totalLengthMeters());
        }
    }
}
