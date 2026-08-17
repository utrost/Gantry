package org.trostheide.gantry.app.session;

import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.model.command.MoveCommand;
import org.trostheide.gantry.model.command.RefillCommand;
import org.trostheide.gantry.pipeline.optimize.MultipassStage;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Swing-free state for the drawing currently open in Gantry.
 *
 * <p>The session is the authoritative owner of the command model, layer selection, source
 * provenance, and bounded undo/redo history. It deliberately performs no file I/O and knows
 * nothing about preview widgets or plotter backends.</p>
 */
public final class DocumentSession {

    private ProcessorOutput currentOutput;
    private List<Integer> selectedLayerIndices = List.of();
    private static final int HISTORY_LIMIT = 100;
    private final ArrayDeque<HistoryState> undoHistory = new ArrayDeque<>();
    private final ArrayDeque<HistoryState> redoHistory = new ArrayDeque<>();
    private boolean dirty;
    private File sourceSvg;
    private SvgImportOptions sourceSvgOptions;
    private ProcessingRecipe processingRecipe;
    private File sourceImage;
    private List<String> vectorizeArgs = List.of();

    public ProcessorOutput currentOutput() {
        return currentOutput;
    }

    /** Replaces the document, selects every layer, and starts a new undo history. */
    public void replace(ProcessorOutput output) {
        currentOutput = output;
        undoHistory.clear();
        redoHistory.clear();
        selectedLayerIndices = allLayerIndices(output);
        dirty = true;
    }

    /** Replaces the model while retaining the existing source and undo state. */
    public void update(ProcessorOutput output) {
        currentOutput = Objects.requireNonNull(output, "output");
        selectedLayerIndices = selectedLayerIndices.stream()
                .filter(index -> index >= 0 && index < output.layers().size())
                .toList();
        dirty = true;
    }

    /**
     * Appends another imported artwork to the current document as additional layers.
     *
     * <p>The appended artwork is placed to the right of the current drawing with a small gap,
     * layer names are made unique, and command ids are remapped so canvas editing remains
     * unambiguous. Appending is undoable like any other document edit.</p>
     */
    public void appendArtwork(ProcessorOutput addition, String label) {
        Objects.requireNonNull(addition, "addition");
        if (currentOutput == null) {
            replace(addition);
            return;
        }
        snapshotForUndo();
        currentOutput = compose(currentOutput, addition, label == null || label.isBlank()
                ? addition.metadata().source() : label);
        selectedLayerIndices = allLayerIndices(currentOutput);
        dirty = true;
        clearSource();
    }

    public void clear() {
        currentOutput = null;
        selectedLayerIndices = List.of();
        undoHistory.clear();
        redoHistory.clear();
        dirty = false;
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
        if (currentOutput == null) return;
        undoHistory.addLast(new HistoryState(currentOutput, selectedLayerIndices, processingRecipe));
        while (undoHistory.size() > HISTORY_LIMIT) undoHistory.removeFirst();
        redoHistory.clear();
    }

    public boolean canUndo() {
        return !undoHistory.isEmpty();
    }

    public boolean canRedo() { return !redoHistory.isEmpty(); }

    /** Restores and consumes the undo snapshot, or returns {@code null} when none exists. */
    public ProcessorOutput undo() {
        if (undoHistory.isEmpty()) {
            return null;
        }
        redoHistory.addLast(new HistoryState(currentOutput, selectedLayerIndices, processingRecipe));
        return restore(undoHistory.removeLast());
    }

    public ProcessorOutput redo() {
        if (redoHistory.isEmpty()) return null;
        undoHistory.addLast(new HistoryState(currentOutput, selectedLayerIndices, processingRecipe));
        return restore(redoHistory.removeLast());
    }

    public boolean isDirty() { return dirty; }
    public void markSaved() { dirty = false; }
    public void markDirty() { if (currentOutput != null) dirty = true; }

    /** Restores a persisted document and its selection as a clean new history root. */
    public void restore(ProcessorOutput output, Collection<Integer> selection) {
        replace(Objects.requireNonNull(output, "output"));
        selectLayers(selection == null ? allLayerIndices(output) : selection);
        dirty = false;
    }

    private ProcessorOutput restore(HistoryState state) {
        currentOutput = state.output();
        selectedLayerIndices = state.selection();
        processingRecipe = state.processingRecipe();
        dirty = true;
        return currentOutput;
    }

    private record HistoryState(ProcessorOutput output, List<Integer> selection, ProcessingRecipe processingRecipe) {
        HistoryState { selection = List.copyOf(selection); }
    }

    public void recordSvgSource(File file, SvgImportOptions options) {
        recordSvgSource(file, options, null);
    }

    public void recordSvgSource(File file, SvgImportOptions options, ProcessingRecipe recipe) {
        sourceSvg = Objects.requireNonNull(file, "file");
        sourceSvgOptions = Objects.requireNonNull(options, "options");
        processingRecipe = recipe;
    }

    public void recordImageSource(File file, List<String> args) {
        sourceImage = Objects.requireNonNull(file, "file");
        vectorizeArgs = List.copyOf(args);
    }

    public void clearSource() {
        sourceSvg = null;
        sourceSvgOptions = null;
        processingRecipe = null;
        sourceImage = null;
        vectorizeArgs = List.of();
    }

    public File sourceSvg() {
        return sourceSvg;
    }

    public SvgImportOptions sourceSvgOptions() {
        return sourceSvgOptions;
    }

    public ProcessingRecipe processingRecipe() { return processingRecipe; }

    public File sourceImage() {
        return sourceImage;
    }

    public List<String> vectorizeArgs() {
        return vectorizeArgs;
    }

    public void restoreSource(File svg, SvgImportOptions options, File image, List<String> args) {
        restoreSource(svg, options, image, args, null);
    }

    public void restoreSource(File svg, SvgImportOptions options, File image, List<String> args, ProcessingRecipe recipe) {
        sourceSvg = svg;
        sourceSvgOptions = options;
        processingRecipe = recipe;
        sourceImage = image;
        vectorizeArgs = args == null ? List.of() : List.copyOf(args);
    }

    private static ProcessorOutput compose(ProcessorOutput base, ProcessorOutput addition, String label) {
        Bounds baseBounds = base.metadata().bounds();
        Bounds addBounds = addition.metadata().bounds();
        double dx = appendOffsetX(baseBounds, addBounds);
        int nextId = maxCommandId(base) + 1;
        List<Layer> layers = new ArrayList<>(base.layers());
        String prefix = label == null || label.isBlank() ? "Added artwork" : label;
        for (Layer layer : addition.layers()) {
            List<Command> shifted = new ArrayList<>(layer.commands().size());
            for (Command command : layer.commands()) {
                shifted.add(shiftAndRenumber(command, nextId++, dx, 0));
            }
            layers.add(new Layer(uniqueLayerId(prefix + " / " + layer.id(), layers),
                    layer.stationId(), layer.color(), List.copyOf(shifted)));
        }
        int totalCommands = layers.stream().mapToInt(layer -> layer.commands().size()).sum();
        Bounds shiftedAddBounds = shift(addBounds, dx, 0);
        Metadata metadata = base.metadata();
        String source = joinSources(metadata.source(), addition.metadata().source());
        return new ProcessorOutput(new Metadata(source, metadata.generatedAt(), metadata.stationId(),
                metadata.units(), totalCommands, union(baseBounds, shiftedAddBounds)), List.copyOf(layers));
    }

    private static double appendOffsetX(Bounds base, Bounds addition) {
        if (!valid(base) || !valid(addition)) return 0;
        return base.maxX() - addition.minX() + 10.0;
    }

    private static Command shiftAndRenumber(Command command, int id, double dx, double dy) {
        if (command instanceof MoveCommand move) return new MoveCommand(id, move.x + dx, move.y + dy);
        if (command instanceof DrawCommand draw) return new DrawCommand(id, draw.points.stream()
                .map(point -> new Point(point.x() + dx, point.y() + dy)).toList());
        if (command instanceof RefillCommand refill) return new RefillCommand(id, refill.stationId);
        throw new IllegalArgumentException("Unsupported command " + command.getClass().getName());
    }

    private static int maxCommandId(ProcessorOutput output) {
        return output.layers().stream()
                .flatMap(layer -> layer.commands().stream())
                .mapToInt(Command::getId)
                .max().orElse(0);
    }

    private static Bounds shift(Bounds bounds, double dx, double dy) {
        return valid(bounds) ? new Bounds(bounds.minX() + dx, bounds.minY() + dy,
                bounds.maxX() + dx, bounds.maxY() + dy) : bounds;
    }

    private static Bounds union(Bounds a, Bounds b) {
        if (!valid(a)) return b;
        if (!valid(b)) return a;
        return new Bounds(Math.min(a.minX(), b.minX()), Math.min(a.minY(), b.minY()),
                Math.max(a.maxX(), b.maxX()), Math.max(a.maxY(), b.maxY()));
    }

    private static boolean valid(Bounds bounds) {
        return bounds != null
                && Double.isFinite(bounds.minX()) && Double.isFinite(bounds.minY())
                && Double.isFinite(bounds.maxX()) && Double.isFinite(bounds.maxY())
                && bounds.maxX() >= bounds.minX() && bounds.maxY() >= bounds.minY();
    }

    private static String uniqueLayerId(String candidate, List<Layer> existing) {
        String clean = candidate == null || candidate.isBlank() ? "Added artwork" : candidate;
        List<String> ids = existing.stream().map(Layer::id).toList();
        if (!ids.contains(clean)) return clean;
        int suffix = 2;
        while (ids.contains(clean + " " + suffix)) suffix++;
        return clean + " " + suffix;
    }

    private static String joinSources(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        return a + " + " + b;
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
