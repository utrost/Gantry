package org.trostheide.gantry.app.plot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.trostheide.gantry.model.ProcessorOutput;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Bounded persistent history of immutable prepared plot jobs. */
public final class PlotJobHistory {
    public record Job(Instant timestamp, ProcessorOutput output, PlotSettings settings) {
        public Job {
            settings = copySettings(settings);
        }

        /** Returns a defensive copy because plotting adds transient runtime flags. */
        @Override
        public PlotSettings settings() {
            return copySettings(settings);
        }
    }

    private static final int MAX_JOBS = 10;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final File file;
    private final List<Job> jobs = new ArrayList<>();

    public PlotJobHistory(File file) {
        this.file = file;
        if (file.isFile()) {
            try {
                jobs.addAll(MAPPER.readValue(file, new TypeReference<List<Job>>() { }));
            } catch (IOException ignored) {
                // A corrupt history must never prevent the application from starting.
            }
        }
    }

    public List<Job> jobs() {
        return List.copyOf(jobs);
    }

    public Job latest() {
        return jobs.isEmpty() ? null : jobs.get(0);
    }

    public void add(Job job) {
        jobs.add(0, job);
        while (jobs.size() > MAX_JOBS) {
            jobs.remove(jobs.size() - 1);
        }
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, jobs);
        } catch (IOException ignored) {
            // History is a convenience; a completed plot remains successful if persistence fails.
        }
    }

    private static PlotSettings copySettings(PlotSettings source) {
        if (source == null) {
            return new PlotSettings();
        }
        PlotSettings copy = new PlotSettings();
        copy.model = source.model;
        copy.invertX = source.invertX;
        copy.invertY = source.invertY;
        copy.swapXY = source.swapXY;
        copy.flipY = source.flipY;
        copy.dataRotation = source.dataRotation;
        copy.canvasAlign = source.canvasAlign;
        copy.alignmentOffsetOverride = source.alignmentOffsetOverride == null
                ? null : source.alignmentOffsetOverride.clone();
        copy.originRight = source.originRight;
        copy.originBottom = source.originBottom;
        copy.paddingX = source.paddingX;
        copy.paddingY = source.paddingY;
        copy.machineWidth = source.machineWidth;
        copy.machineHeight = source.machineHeight;
        copy.debugPosition = source.debugPosition;
        copy.reportPosition = source.reportPosition;
        copy.realtimePosition = source.realtimePosition;
        copy.stations = new HashMap<>(source.stations);
        return copy;
    }
}
