package org.trostheide.gantry.app.session;

import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;

import java.util.List;

/**
 * Addressable artwork group inside a composed Gantry document.
 *
 * <p>The command model remains the plot/export source of truth. This record keeps the
 * grouping/provenance/transform metadata needed to manipulate appended SVGs as whole artworks.</p>
 */
public record CompositionArtwork(String id, String label, String sourcePath,
                                 List<Integer> layerIndices, Bounds originalBounds,
                                 Bounds bounds, Transform transform,
                                 SvgImportOptions importOptions,
                                 ProcessingRecipe processingRecipe) {
    public CompositionArtwork {
        layerIndices = layerIndices == null ? List.of() : List.copyOf(layerIndices);
        transform = transform == null ? Transform.identity() : transform;
    }

    public CompositionArtwork withLayerIndices(List<Integer> indices) {
        return new CompositionArtwork(id, label, sourcePath, indices, originalBounds, bounds, transform,
                importOptions, processingRecipe);
    }

    public CompositionArtwork withBoundsAndTransform(Bounds nextBounds, Transform nextTransform) {
        return new CompositionArtwork(id, label, sourcePath, layerIndices, originalBounds, nextBounds, nextTransform,
                importOptions, processingRecipe);
    }

    public CompositionArtwork withSource(String nextSourcePath, SvgImportOptions nextImportOptions,
            ProcessingRecipe nextRecipe) {
        return new CompositionArtwork(id, label, nextSourcePath, layerIndices, originalBounds, bounds, transform,
                nextImportOptions, nextRecipe);
    }

    public record Transform(double x, double y, double scale, boolean mirror) {
        public Transform {
            scale = scale <= 0 || !Double.isFinite(scale) ? 1.0 : scale;
            x = Double.isFinite(x) ? x : 0.0;
            y = Double.isFinite(y) ? y : 0.0;
        }
        public static Transform identity() { return new Transform(0, 0, 1.0, false); }
    }
}
