package org.trostheide.gantry.app.session;

import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;

import java.util.List;

/** Complete editable Gantry session persisted independently from flattened command JSON. */
public record GantryProject(int formatVersion, ProcessorOutput output,
                            List<Integer> selectedLayers, Placement placement, int passes,
                            Source source) {
    public static final int CURRENT_VERSION = 1;

    public GantryProject {
        selectedLayers = selectedLayers == null ? List.of() : List.copyOf(selectedLayers);
        placement = placement == null ? Placement.identity() : placement;
        passes = Math.max(1, passes);
        source = source == null ? Source.empty() : source;
    }

    public record Placement(double offsetX, double offsetY, double scale,
                            int rotation, boolean mirror, boolean suppressAlignment) {
        public static Placement identity() { return new Placement(0, 0, 1, 0, false, false); }
    }

    public record Source(String svgPath, SvgImportOptions importOptions,
                         String imagePath, List<String> vectorizeArgs) {
        public Source { vectorizeArgs = vectorizeArgs == null ? List.of() : List.copyOf(vectorizeArgs); }
        public static Source empty() { return new Source(null, null, null, List.of()); }
    }
}
