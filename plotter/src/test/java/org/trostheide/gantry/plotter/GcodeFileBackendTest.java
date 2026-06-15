package org.trostheide.gantry.plotter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GcodeFileBackendTest {

    @TempDir
    File tempDir;

    @Test
    void writesSetupMoveDrawAndHomeLines() throws IOException {
        File file = new File(tempDir, "job.gcode");
        GcodeOptions options = new GcodeOptions();
        GcodeFileBackend backend = new GcodeFileBackend(options, file);

        assertTrue(backend.connect());
        backend.moveto(10, 20);
        backend.lineto(5, 5);
        backend.disconnect();

        List<String> lines = Files.readAllLines(file.toPath());
        assertEquals(List.of("$X", "G21", "G90", "G92 X0 Y0"), lines.subList(0, 4));
        assertEquals("G0 X10.000 Y20.000 F3000", lines.get(4));
        // lineto lowers the pen (servo down) before drawing
        assertEquals("M280 P0 S30", lines.get(5));
        assertEquals("G1 X5.000 Y5.000 F1000", lines.get(6));
        // disconnect raises the pen and returns home
        assertEquals("M280 P0 S60", lines.get(7));
        assertEquals("G0 X0 Y0", lines.get(8));
    }
}
