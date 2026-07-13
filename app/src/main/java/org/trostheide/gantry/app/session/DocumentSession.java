package org.trostheide.gantry.app.session;

import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.pipeline.optimize.MultipassStage;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Swing-free state for the drawing currently open in Gantry.
 *
 * <p>The session is the authoritative owner of the command model, layer selection, source
 * provenance, and the single-level undo snapshot. It deliberately performs no file I/O and knows
 * nothing about preview widgets or plotter backends.</p>
 */
public final class DocumentSession {

    private ProcessorOutput currentOutput;
    private List<Integer> selectedLayerIndices = List.of();
    private ProcessorOutput undoSnapshot;
    private File sourceSvg;
    private SvgImportOptions sourceSvgOptions;
    private File sourceImage;
    private List<String> vectorizeArgs = List.of();

    public ProcessorOutput currentOutput() {
        return currentOutput;
    }

    /** Replaces the document, selects every layer, and starts a new undo history. */
    public void replace(ProcessorOutput output) {
        currentOutput = output;
        undoSnapshot = null;
        selectedLayerIndices = allLayerIndices(output);
    }

    /** Replaces the model while retaining the existing source and undo state. */
    public void update(ProcessorOutput output) {
        currentOutput = Objects.requireNonNull(output, "output");
        selectedLayerIndices = selectedLayerIndices.stream()
                .filter(index -> index >= 0 && index < output.layers().size())
                .toList();
    }

    public void clear() {
        currentOutput = null;
        selectedLayerIndices = List.of();
        undoSnapshot = null;
        clearSource();
    }

    public List<Integer> selectedLayerIndices() {
        return selectedLayerIndices;
    }

    public void selectLayers(Collection<Integer> indices) {
        if (currentOutput == null) {
            selectedLayerIndices = List.of();
            return;
        }
        selectedLayerIndices = indices.stream()
                .distinct()
                .filter(index -> index != null && index >= 0 && index < currentOutput.layers().size())
                .sorted()
                .toList();
    }

    public ProcessorOutput selectedOutput() {
        if (currentOutput == null) {
            return null;
        }
        if (selectedLayerIndices.size() == currentOutput.layers().size()) {
            return currentOutput;
        }
        List<Layer> kept = new ArrayList<>(selectedLayerIndices.size());
        for (int index : selectedLayerIndices) {
            kept.add(currentOutput.layers().get(index));
        }
        return new ProcessorOutput(currentOutput.metadata(), kept);
    }

    /** Applies the same selection → overlay → multipass sequence used for plot and export. */
    public ProcessorOutput prepareOutput(UnaryOperator<ProcessorOutput> overlayBaker, int passes) {
        ProcessorOutput selected = selectedOutput();
        if (selected == null) {
            return null;
        }
        ProcessorOutput baked = Objects.requireNonNull(overlayBaker, "overlayBaker").apply(selected);
        return MultipassStage.apply(Objects.requireNonNull(baked, "overlayBaker result"), passes);
    }

    public void snapshotForUndo() {
        undoSnapshot = currentOutput;
    }

    public boolean canUndo() {
        return undoSnapshot != null;
    }

    /** Restores and consumes the undo snapshot, or returns {@code null} when none exists. */
    public ProcessorOutput undo() {
        if (undoSnapshot == null) {
            return null;
        }
        ProcessorOutput restored = undoSnapshot;
        undoSnapshot = null;
        currentOutput = restored;
        selectedLayerIndices = allLayerIndices(restored);
        return restored;
    }

    public void recordSvgSource(File file, SvgImportOptions options) {
        sourceSvg = Objects.requireNonNull(file, "file");
        sourceSvgOptions = Objects.requireNonNull(options, "options");
    }

    public void recordImageSource(File file, List<String> args) {
        sourceImage = Objects.requireNonNull(file, "file");
        vectorizeArgs = List.copyOf(args);
    }

    public void clearSource() {
        sourceSvg = null;
        sourceSvgOptions = null;
        sourceImage = null;
        vectorizeArgs = List.of();
    }

    public File sourceSvg() {
        return sourceSvg;
    }

    public SvgImportOptions sourceSvgOptions() {
        return sourceSvgOptions;
    }

    public File sourceImage() {
        return sourceImage;
    }

    public List<String> vectorizeArgs() {
        return vectorizeArgs;
    }

    private static List<Integer> allLayerIndices(ProcessorOutput output) {
        if (output == null) {
            return List.of();
        }
        List<Integer> indices = new ArrayList<>(output.layers().size());
        for (int i = 0; i < output.layers().size(); i++) {
            indices.add(i);
        }
        return List.copyOf(indices);
    }
}
