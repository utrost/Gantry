package org.trostheide.gantry.app.plot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.trostheide.gantry.model.Bounds;
import org.trostheide.gantry.model.Metadata;
import org.trostheide.gantry.model.ProcessorOutput;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class PlotJobHistoryTest {
    @TempDir
    Path tmp;

    @Test
    void persistsLatestPreparedJobWithDefensiveSettings() {
        File file = tmp.resolve("history.json").toFile();
        PlotJobHistory history = new PlotJobHistory(file);
        ProcessorOutput output = new ProcessorOutput(
                new Metadata("x", Instant.EPOCH, "s", "mm", 0, Bounds.empty()), List.of());
        PlotSettings settings = new PlotSettings();
        settings.alignmentOffsetOverride = new double[] {12, 34};
        history.add(new PlotJobHistory.Job(Instant.EPOCH, output, settings));

        settings.alignmentOffsetOverride[0] = 99;
        PlotJobHistory loaded = new PlotJobHistory(file);

        assertEquals(1, loaded.jobs().size());
        assertEquals(output, loaded.latest().output());
        assertEquals(12, loaded.latest().settings().alignmentOffsetOverride[0]);
        assertNotSame(loaded.latest().settings(), loaded.latest().settings());
    }
}
