package org.trostheide.gantry.plotter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;
class GcodeFileReplayTest {
    @TempDir Path directory;
    private GcodeFileReplay.Plan plan(String body) throws IOException {
        return GcodeFileReplay.preflight(Files.writeString(directory.resolve("job.gcode"), body).toFile(), new GcodeOptions());
    }
    @Test void planDetectsMachineChangesAndKeepsValidatedBytes() throws Exception {
        var plan = plan("G21\nG90\nG1 X1 Y1 F100");
        var options = new GcodeOptions();
        assertTrue(plan.matches(options));
        options.machineWidth = 0.5;
        assertFalse(plan.matches(options));
        Files.writeString(directory.resolve("job.gcode"), "$H");
        assertFalse(plan.commands().contains("$H"));
        assertThrows(UnsupportedOperationException.class, () -> plan.commands().add("$H"));
    }
    @Test void rejectsUnsafeModesCommandsAndBoundsBeforeStreaming() {
        for (String invalid : List.of("G91", "G92 X0 Y0", "$X", "$H", "$100=2", "G1 X301 Y0 F100", "G1 X-1 Y0 F100", "G1 X1 Y1", "G1 X1 X2 Y2 F100", "G0 Z-999", "M3 S999", "G20", "G54", "G1 X1 Y1 F99999", "G4 P61")) {
            assertThrows(IOException.class, () -> plan("G21\nG90\n"+invalid+"\nG1 X1 Y1 F100\n"), invalid);
        }
        assertThrows(IOException.class, () -> plan("G1 X1 Y1 F100"));
    }
    @Test void supportsCompactAbsoluteMotionAndExportedFile() throws Exception {
        assertEquals(5, plan("; comment\nG21\nG90\nG1X10Y20F100 (draw)\nG4 P0.1").commands().size());
        File output = directory.resolve("export.gcode").toFile();
        var backend = new GcodeFileBackend(new GcodeOptions(), output);
        assertTrue(backend.connect()); backend.moveto(1,2); backend.lineto(3,4); backend.disconnect();
        assertNotNull(GcodeFileReplay.preflight(output, new GcodeOptions()));
    }
    @Test void errorTimeoutAlarmAndDisconnectStopBeforeFollowingCommand() throws Exception {
        var plan = plan("G21\nG90\nG1 X1 Y1 F100\nG1 X2 Y2 F100");
        for (String response : List.of("error:1", "ALARM:1", "(no response)", "(no serial connection)", "<Idle>")) {
            var backend = new RecordingBackend(); backend.failure = response;
            assertThrows(IOException.class, () -> GcodeFileReplay.replay(plan, backend, s -> {}));
            assertFalse(backend.calls.contains("G1 X2 Y2 F100"));
            assertEquals(List.of("halt", "up"), backend.calls.subList(backend.calls.size()-2, backend.calls.size()));
        }
    }
    @Test void cancellationPreservesInterruptAndRecovers() throws Exception {
        var plan = plan("G21\nG90\nG1 X1 Y1 F100");
        var backend = new RecordingBackend();
        try {
            Thread.currentThread().interrupt();
            assertThrows(CancellationException.class, () -> GcodeFileReplay.replay(plan, backend, s -> {}));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(List.of("halt", "up"), backend.calls);
        } finally { Thread.interrupted(); }
    }
    static class RecordingBackend implements PlotterBackend {
        final List<String> calls = new ArrayList<>(); String failure;
        public boolean connect(){return true;} public void disconnect(){}
        public void moveto(double x,double y){} public void lineto(double x,double y){} public void move(double x,double y){}
        public void pendown(){} public void penup(){calls.add("up");} public void haltMotion(){calls.add("halt");}
        public List<String> sendRaw(String command){calls.add(command);return List.of(command.startsWith("G1") && failure != null ? failure : "ok");}
    }
}
