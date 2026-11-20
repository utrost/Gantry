package org.trostheide.gantry.app.session;

import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.ProcessorOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Named physical pens and their assignments to artwork layers. */
public record PenLibrary(List<Pen> pens, List<String> layerPenIds) {
    public record Pen(String id, String name, String color, double widthMm) {
        public Pen {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Pen id is required");
            name = name == null || name.isBlank() ? id : name.trim();
            color = normalizedColor(color);
            if (!Double.isFinite(widthMm) || widthMm <= 0) widthMm = 0.3;
        }
    }

    public PenLibrary {
        pens = pens == null ? List.of() : List.copyOf(pens);
        layerPenIds = layerPenIds == null ? List.of() : List.copyOf(layerPenIds);
        if (pens.stream().map(Pen::id).distinct().count() != pens.size())
            throw new IllegalArgumentException("Duplicate pen ids");
        List<Pen> definedPens = pens;
        if (layerPenIds.stream().anyMatch(id -> definedPens.stream().noneMatch(pen -> pen.id().equals(id))))
            throw new IllegalArgumentException("Layer references an unknown pen");
    }

    public static PenLibrary fromLegacy(ProcessorOutput output, List<Double> widths) {
        if (output == null) return new PenLibrary(List.of(), List.of());
        List<Pen> pens = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < output.layers().size(); i++) {
            Layer layer = output.layers().get(i);
            String id = "pen-" + (i + 1);
            double width = widths != null && i < widths.size() && widths.get(i) != null ? widths.get(i) : 0.3;
            pens.add(new Pen(id, layer.id(), layer.color(), width));
            ids.add(id);
        }
        return new PenLibrary(pens, ids);
    }

    public Pen penForLayer(int layerIndex) {
        String id = layerPenIds.get(layerIndex);
        return pens.stream().filter(pen -> pen.id().equals(id)).findFirst().orElseThrow();
    }

    public List<Double> widths() {
        return java.util.stream.IntStream.range(0, layerPenIds.size())
                .mapToObj(i -> penForLayer(i).widthMm()).toList();
    }

    public PenLibrary withPen(Pen replacement) {
        Objects.requireNonNull(replacement, "replacement");
        List<Pen> updated = new ArrayList<>(pens);
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).id().equals(replacement.id())) {
                updated.set(i, replacement);
                return new PenLibrary(updated, layerPenIds);
            }
        }
        throw new IllegalArgumentException("Unknown pen " + replacement.id());
    }

    public PenLibrary assign(int layerIndex, String penId) {
        if (layerIndex < 0 || layerIndex >= layerPenIds.size()) throw new IndexOutOfBoundsException(layerIndex);
        if (pens.stream().noneMatch(pen -> pen.id().equals(penId))) throw new IllegalArgumentException("Unknown pen " + penId);
        List<String> assigned = new ArrayList<>(layerPenIds);
        assigned.set(layerIndex, penId);
        return new PenLibrary(pens, assigned);
    }

    public PenLibrary add(String name, String color, double widthMm) {
        List<Pen> updated = new ArrayList<>(pens);
        updated.add(new Pen(nextId(updated), name, color, widthMm));
        return new PenLibrary(updated, layerPenIds);
    }

    public PenLibrary append(List<Layer> allLayers) {
        List<Integer> mapping = new ArrayList<>();
        for (int i = 0; i < layerPenIds.size(); i++) mapping.add(i);
        for (int i = layerPenIds.size(); i < allLayers.size(); i++) mapping.add(-1);
        return remap(mapping, allLayers);
    }

    /** Keeps assignments for matched layers; new imported layers receive independent pens. */
    public PenLibrary remap(List<Integer> oldIndexByNewLayer, List<Layer> newLayers) {
        if (oldIndexByNewLayer.size() != newLayers.size()) throw new IllegalArgumentException("Layer mapping size mismatch");
        List<Pen> updated = new ArrayList<>(pens);
        List<String> assigned = new ArrayList<>();
        for (int i = 0; i < newLayers.size(); i++) {
            int oldIndex = oldIndexByNewLayer.get(i);
            if (oldIndex >= 0 && oldIndex < layerPenIds.size()) assigned.add(layerPenIds.get(oldIndex));
            else {
                String id = nextId(updated);
                Layer layer = newLayers.get(i);
                updated.add(new Pen(id, layer.id(), layer.color(), 0.3));
                assigned.add(id);
            }
        }
        return new PenLibrary(updated, assigned);
    }

    /** Reconciles an old or partial project without silently sharing previously independent layers. */
    public PenLibrary forOutput(ProcessorOutput output, List<Double> legacyWidths) {
        if (output == null) return new PenLibrary(pens, List.of());
        List<Pen> updated = new ArrayList<>(pens);
        List<String> assigned = new ArrayList<>(layerPenIds.subList(0, Math.min(layerPenIds.size(), output.layers().size())));
        for (int i = assigned.size(); i < output.layers().size(); i++) {
            String id = nextId(updated);
            Layer layer = output.layers().get(i);
            double width = legacyWidths != null && i < legacyWidths.size() && legacyWidths.get(i) != null
                    ? legacyWidths.get(i) : 0.3;
            updated.add(new Pen(id, layer.id(), layer.color(), width));
            assigned.add(id);
        }
        return new PenLibrary(updated, assigned);
    }

    /** Applies layer-width edits to the owning shared pen, then propagates its width to all layers. */
    public PenLibrary withLayerWidths(List<Double> desired, List<Double> previous) {
        PenLibrary result = this;
        for (int i = 0; i < layerPenIds.size() && desired != null && i < desired.size(); i++) {
            Double width = desired.get(i);
            if (width == null || !Double.isFinite(width) || width <= 0) continue;
            if (previous != null && i < previous.size() && Double.compare(width, previous.get(i)) == 0) continue;
            Pen old = result.penForLayer(i);
            result = result.withPen(new Pen(old.id(), old.name(), old.color(), width));
        }
        return result;
    }

    private static String nextId(List<Pen> pens) {
        int n = 1;
        while (true) {
            String id = "pen-" + n++;
            if (pens.stream().noneMatch(pen -> pen.id().equals(id))) return id;
        }
    }

    private static String normalizedColor(String color) {
        return color != null && color.matches("#[0-9a-fA-F]{6}")
                ? color.toLowerCase(Locale.ROOT) : null;
    }
}
