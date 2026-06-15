package org.trostheide.gantry.plotter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.function.Consumer;

/** Streams a {@code .gcode} file (e.g. written by {@link GcodeFileBackend}) to a connected {@link GcodeBackend}. */
public final class GcodeFileReplay {

    private GcodeFileReplay() {
    }

    /**
     * Sends every non-empty, non-comment line of {@code file} to {@code backend} via
     * {@link GcodeBackend#sendRaw(String)}, logging each command and its response.
     */
    public static void replay(File file, GcodeBackend backend, Consumer<String> log) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String cmd = line.trim();
                if (cmd.isEmpty() || cmd.startsWith(";") || cmd.startsWith("(")) {
                    continue;
                }
                log.accept("> " + cmd);
                for (String response : backend.sendRaw(cmd)) {
                    log.accept(response);
                }
            }
        }
    }
}
