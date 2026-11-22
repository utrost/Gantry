package org.trostheide.gantry.app.plot;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.command.*;
import org.trostheide.gantry.model.io.AtomicFiles;

import java.io.*;
import java.security.*;
import java.time.Instant;
import java.util.*;

/**
 * Immutable prepared-job identity and explicit, pen-only restart proposals. Never infers executed
 * ink from ack.
 */
public final class ReviewedRestart {
    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);

    public record Stroke(
            int layer,
            int commandIndex,
            int commandId,
            int occurrence,
            String layerId,
            List<Point> points) {
        public Stroke {
            points = List.copyOf(points);
        }

        @Override
        public String toString() {
            return "Layer "
                    + (layer + 1)
                    + " · "
                    + layerId
                    + " · command "
                    + commandId
                    + " · occurrence "
                    + occurrence;
        }
    }

    public record Snapshot(
            int version,
            PlotJobHistory.Job source,
            String identity,
            String machineIdentity,
            List<Stroke> strokes,
            List<Integer> order,
            List<Integer> confirmed,
            int started,
            int acknowledged,
            String state,
            int selection,
            boolean reverse) {
        public Snapshot {
            strokes = List.copyOf(strokes);
            order = List.copyOf(order);
            confirmed = List.copyOf(confirmed);
        }
    }

    public static String identity(PlotJobHistory.Job job) {
        PlotSettings settings = job.settings();
        settings.reportPosition = false;
        settings.realtimePosition = false;
        settings.debugPosition = false;
        return digest(Arrays.asList(job.output(), settings, job.pens(), job.referenceMapping()));
    }

    public static String machineIdentity(GantryConfig config) {
        return digest(
                Arrays.asList(
                        config.gcode,
                        config.toPlotSettings(),
                        config.coordinateVersion,
                        config.machineOrigin,
                        config.orientation,
                        config.mock,
                        config.serialSimulator,
                        config.hasLimitSwitches,
                        config.softLimits));
    }

    private static String digest(Object value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(JSON.writeValueAsBytes(value)));
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Cannot identify restart job", failure);
        }
    }

    public static List<Stroke> strokes(PlotJobHistory.Job job) {
        var settings = job.settings();
        var output = job.output();
        if (output.layers().stream()
                        .anyMatch(
                                layer ->
                                        layer.maxDrawDistance() > 0
                                                || layer.commands().stream()
                                                        .anyMatch(c -> c instanceof RefillCommand))
                || (output.layers().size() > 1
                        && PlotService.rinseStation(settings.stations) != null))
            throw new IllegalArgumentException(
                    "Watercolor restart/reverse is unsupported: refill, rinse, loading and drying"
                            + " require a separate policy. Use a reviewed pen-only job.");
        List<Stroke> source = new ArrayList<>();
        for (int layerIndex = 0; layerIndex < output.layers().size(); layerIndex++) {
            Layer layer = output.layers().get(layerIndex);
            Map<Integer, Integer> occurrences = new HashMap<>();
            for (int commandIndex = 0; commandIndex < layer.commands().size(); commandIndex++)
                if (layer.commands().get(commandIndex) instanceof DrawCommand draw) {
                    if (!draw.points.isEmpty())
                        source.add(
                                new Stroke(
                                        layerIndex,
                                        commandIndex,
                                        draw.id,
                                        occurrences.merge(draw.id, 1, Integer::sum),
                                        layer.id(),
                                        List.of()));
                }
        }
        List<List<Point>> geometry = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) geometry.add(new ArrayList<>());
        // forEachDrawSegment numbers empty DRAWs too; map those ordinals to nonempty identities.
        Map<Integer, Integer> ordinalMap = new HashMap<>();
        int ordinal = 0, index = 0;
        for (Layer layer : output.layers())
            for (Command command : layer.commands())
                if (command instanceof DrawCommand draw) {
                    ordinal++;
                    if (!draw.points.isEmpty()) ordinalMap.put(ordinal, index++);
                }
        new PlotService(null, settings)
                .forEachDrawSegment(
                        output,
                        segment -> {
                            List<Point> points =
                                    geometry.get(ordinalMap.get(segment.strokeOrdinal()));
                            if (points.isEmpty()) points.add(new Point(segment.x1(), segment.y1()));
                            points.add(new Point(segment.x2(), segment.y2()));
                        });
        List<Stroke> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            var stroke = source.get(i);
            result.add(
                    new Stroke(
                            stroke.layer(),
                            stroke.commandIndex(),
                            stroke.commandId(),
                            stroke.occurrence(),
                            stroke.layerId(),
                            geometry.get(i)));
        }
        if (result.isEmpty())
            throw new IllegalArgumentException("No drawable strokes in this prepared job");
        return List.copyOf(result);
    }

    public static List<Integer> order(int count, int start, boolean reverse) {
        if (start < 0 || start >= count)
            throw new IllegalArgumentException("Choose a valid start stroke");
        List<Integer> order = new ArrayList<>();
        for (int i = start; i >= 0 && i < count; i += reverse ? -1 : 1) order.add(i);
        return List.copyOf(order);
    }

    /**
     * Commands are already in controller work coordinates; never re-rotate/re-align a selected
     * subset.
     */
    public static PlotJobHistory.Job execution(Snapshot proposal) {
        List<Layer> layers = new ArrayList<>();
        List<PlotJobHistory.PenSnapshot> pens = new ArrayList<>();
        int previousLayer = -1, id = 1;
        List<Command> commands = new ArrayList<>();
        Layer original = null;
        for (int index : proposal.order()) {
            Stroke stroke = proposal.strokes().get(index);
            if (stroke.layer() != previousLayer) {
                if (original != null)
                    layers.add(
                            new Layer(
                                    original.id(),
                                    original.stationId(),
                                    original.color(),
                                    commands));
                original = proposal.source().output().layers().get(stroke.layer());
                commands = new ArrayList<>();
                previousLayer = stroke.layer();
                if (stroke.layer() < proposal.source().pens().size())
                    pens.add(proposal.source().pens().get(stroke.layer()));
            }
            List<Point> points = new ArrayList<>(stroke.points());
            if (proposal.reverse()) Collections.reverse(points);
            Point start = points.get(0);
            commands.add(new MoveCommand(id++, start.x(), start.y()));
            commands.add(new DrawCommand(id++, points.subList(1, points.size())));
        }
        if (original != null)
            layers.add(new Layer(original.id(), original.stationId(), original.color(), commands));
        var source = proposal.source().output().metadata();
        var output =
                new ProcessorOutput(
                        new Metadata(
                                source.source(),
                                source.generatedAt(),
                                source.stationId(),
                                source.units(),
                                id - 1,
                                source.bounds()),
                        layers);
        PlotSettings settings = new PlotSettings();
        settings.machineWidth = proposal.source().settings().resolveMachineWidth();
        settings.machineHeight = proposal.source().settings().resolveMachineHeight();
        settings.originRight = proposal.source().settings().originRight;
        settings.originBottom = proposal.source().settings().originBottom;
        settings.alignmentOffsetOverride = new double[] {0, 0};
        return new PlotJobHistory.Job(
                Instant.now(),
                output,
                settings,
                pens,
                proposal.source().reviewSettings(),
                proposal.machineIdentity());
    }

    public static final class Store {
        private final File file;
        private Snapshot snapshot;
        private String loadError;

        private record Source(
                int version, PlotJobHistory.Job job, String identity, String machineIdentity) {}

        private record Progress(
                int version,
                String identity,
                List<Integer> confirmed,
                int started,
                int acknowledged,
                String state,
                int selection,
                boolean reverse) {}

        private File sourceFile() {
            return new File(file.getPath() + ".job.json");
        }

        public Store(File file) {
            this.file = file;
            if (file.isFile())
                try {
                    Progress progress = JSON.readValue(file, Progress.class);
                    Source source = JSON.readValue(sourceFile(), Source.class);
                    if (progress.version() != 1
                            || source.version() != 1
                            || !identity(source.job()).equals(source.identity())
                            || !source.identity().equals(progress.identity()))
                        throw new IOException(
                                "Restart checkpoint does not match its full prepared job");
                    var strokes = strokes(source.job());
                    var order = order(strokes.size(), progress.selection(), progress.reverse());
                    for (int confirmed : progress.confirmed())
                        if (confirmed < 0 || confirmed >= strokes.size())
                            throw new IOException("Invalid confirmed stroke identity");
                    if (progress.started() < 0
                            || progress.started() > order.size()
                            || progress.acknowledged() < 0
                            || progress.acknowledged() > progress.started())
                        throw new IOException("Invalid submitted/acknowledged progress");
                    snapshot =
                            new Snapshot(
                                    1,
                                    source.job(),
                                    source.identity(),
                                    source.machineIdentity(),
                                    strokes,
                                    order,
                                    progress.confirmed(),
                                    progress.started(),
                                    progress.acknowledged(),
                                    progress.state(),
                                    progress.selection(),
                                    progress.reverse());
                } catch (IOException | RuntimeException error) {
                    loadError = error.getMessage();
                }
        }

        public synchronized void discard() {
            try {
                java.nio.file.Files.deleteIfExists(file.toPath());
                java.nio.file.Files.deleteIfExists(sourceFile().toPath());
                snapshot = null;
                loadError = null;
            } catch (IOException failure) {
                throw new UncheckedIOException("Cannot discard restart checkpoint", failure);
            }
        }

        public synchronized Snapshot snapshot() {
            return snapshot;
        }

        public String loadError() {
            return loadError;
        }

        public synchronized void capture(PlotJobHistory.Job source, GantryConfig config) {
            List<Stroke> strokes = strokes(source);
            set(
                    new Snapshot(
                            1,
                            source,
                            identity(source),
                            machineIdentity(config),
                            strokes,
                            order(strokes.size(), 0, false),
                            List.of(),
                            0,
                            0,
                            "PROPOSED",
                            0,
                            false));
        }

        public synchronized void select(int start, boolean reverse) {
            Snapshot s = require();
            set(
                    new Snapshot(
                            1,
                            s.source(),
                            s.identity(),
                            s.machineIdentity(),
                            s.strokes(),
                            order(s.strokes().size(), start, reverse),
                            s.confirmed(),
                            0,
                            0,
                            "PROPOSED",
                            start,
                            reverse));
        }

        public synchronized void begin() {
            Snapshot s = require();
            set(copy(s, s.confirmed(), 0, 0, "RUNNING"));
        }

        public synchronized void progress(PlotService.StrokeProgress progress) {
            Snapshot s = require();
            int started = s.started(), acked = s.acknowledged();
            if (progress.phase() == PlotService.StrokeProgressPhase.STARTED) started++;
            else acked++;
            if (started > s.order().size() || acked > started)
                throw new IllegalStateException("Restart stroke identity/progress mismatch");
            set(copy(s, s.confirmed(), started, acked, "RUNNING"));
        }

        /**
         * Only a completed planner barrier can promote acknowledged strokes to
         * controller-confirmed.
         */
        public synchronized void layerCompleted() {
            Snapshot s = require();
            Set<Integer> confirmed = new TreeSet<>(s.confirmed());
            confirmed.addAll(s.order().subList(0, s.acknowledged()));
            set(copy(s, List.copyOf(confirmed), s.started(), s.acknowledged(), "RUNNING"));
        }

        public synchronized void finish(boolean success) {
            // Cancellation leaves the plot thread interrupted. Final state persistence
            // contains no motion and must still be able to fsync its atomic checkpoint.
            boolean interrupted = Thread.interrupted();
            try {
                Snapshot s = require();
                set(
                        copy(
                                s,
                                s.confirmed(),
                                s.started(),
                                s.acknowledged(),
                                success ? "SELECTION_COMPLETE" : "STOPPED_UNCERTAIN"));
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }

        public synchronized void compatible(GantryConfig config, PlotJobHistory.Job current) {
            Snapshot s = require();
            if (!s.machineIdentity().equals(machineIdentity(config)))
                throw new IllegalArgumentException(
                        "Machine, pen actuator, speed, origin or simulation settings changed. This"
                                + " restart is blocked; review the original job/profile.");
            if (current != null && !s.identity().equals(identity(current)))
                throw new IllegalArgumentException(
                        "Current geometry, placement, selected layers, passes or pens differ from"
                            + " the saved prepared job. Reopen the original project or clear the"
                            + " current artwork to review the stored job.");
        }

        private Snapshot require() {
            if (snapshot == null)
                throw new IllegalStateException(
                        loadError == null ? "No saved pen-only restart job" : loadError);
            return snapshot;
        }

        private static Snapshot copy(
                Snapshot s, List<Integer> confirmed, int started, int acked, String state) {
            return new Snapshot(
                    1,
                    s.source(),
                    s.identity(),
                    s.machineIdentity(),
                    s.strokes(),
                    s.order(),
                    confirmed,
                    started,
                    acked,
                    state,
                    s.selection(),
                    s.reverse());
        }

        private void set(Snapshot candidate) {
            try {
                if (snapshot == null
                        || !snapshot.identity().equals(candidate.identity())
                        || !snapshot.machineIdentity().equals(candidate.machineIdentity())) {
                    Source source =
                            new Source(
                                    1,
                                    candidate.source(),
                                    candidate.identity(),
                                    candidate.machineIdentity());
                    AtomicFiles.write(
                            sourceFile(),
                            temp -> JSON.writerWithDefaultPrettyPrinter().writeValue(temp, source));
                }
                Progress progress =
                        new Progress(
                                1,
                                candidate.identity(),
                                candidate.confirmed(),
                                candidate.started(),
                                candidate.acknowledged(),
                                candidate.state(),
                                candidate.selection(),
                                candidate.reverse());
                AtomicFiles.write(
                        file,
                        temp -> JSON.writerWithDefaultPrettyPrinter().writeValue(temp, progress));
                snapshot = candidate;
                loadError = null;
            } catch (IOException failure) {
                throw new UncheckedIOException(
                        "Restart checkpoint could not be saved; no untracked continuation allowed",
                        failure);
            }
        }
    }
}
