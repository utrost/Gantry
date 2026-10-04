package org.trostheide.gantry.plotter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.regex.*;

/** Validates the complete file before sending a restricted, absolute-mm plot job. */
public final class GcodeFileReplay {
    private GcodeFileReplay() { }
    private static final Pattern WORD = Pattern.compile("([A-Z])([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+))");
    public static final class Plan {
        private final List<String> commands;
        private final String summary;
        private final List<Object> configuration;
        private Plan(List<String> commands, String summary, GcodeOptions options) {
            this.commands = List.copyOf(commands); this.summary = summary; this.configuration = configurationKey(options);
        }
        public boolean matches(GcodeOptions options) { return configuration.equals(configurationKey(options)); }
        public String summary() { return summary; }
        public List<String> commands() { return commands; }
    }

    public static Plan preflight(File file, GcodeOptions options) throws IOException {
        if (!Double.isFinite(options.machineWidth) || !Double.isFinite(options.machineHeight)
                || options.machineWidth <= 0 || options.machineHeight <= 0) throw new IOException("Configure positive machine dimensions before replay");
        byte[] bytes;
        try (InputStream input = Files.newInputStream(file.toPath())) {
            bytes = input.readNBytes(16 * 1024 * 1024 + 1);
        }
        if (bytes.length > 16 * 1024 * 1024) throw new IOException("G-code exceeds the 16 MiB replay limit");
        List<String> commands = new ArrayList<>();
        commands.add("G94"); // Do not inherit inverse-time feed mode from a previous manual command.
        Set<String> pens = new HashSet<>();
        for (String pen : Arrays.asList(GcodeFormatter.penUp(options), GcodeFormatter.penDown(options))) {
            if (pen != null) pens.add(pen.replaceAll("\\s+", ""));
        }
        boolean mm = false, absolute = false, moved = false;
        double maxX = 0, maxY = 0;
        int number = 0;
        for (String source : new String(bytes, StandardCharsets.UTF_8).split("\\R")) {
            number++;
            String line = source.replaceAll("\\([^()]*\\)", "").split(";", 2)[0].trim().toUpperCase(Locale.ROOT);
            if (line.isEmpty()) continue;
            String compact = line.replaceAll("\\s+", "");
            if (compact.equals("G21")) mm = true;
            else if (compact.equals("G90")) absolute = true;
            else if (compact.equals("G94")) { /* feed in mm/min */ }
            else {
                if (!mm || !absolute) throw rejected(number, "Declare G21 and G90 before motion or pen commands");
                if (!pens.contains(compact)) {
                    Map<Character, Double> words = new LinkedHashMap<>();
                    Matcher matcher = WORD.matcher(compact);
                    int end = 0;
                    while (matcher.find()) {
                        if (matcher.start() != end) throw rejected(number, "Unsupported syntax");
                        char key = matcher.group(1).charAt(0);
                        double value = Double.parseDouble(matcher.group(2));
                        if (!Double.isFinite(value) || words.put(key, value) != null) throw rejected(number, "Duplicate or invalid word");
                        end = matcher.end();
                    }
                    if (end != compact.length()) throw rejected(number, "Unsupported syntax or controller command");
                    Double g = words.get('G');
                    if (g != null && g == 4 && words.keySet().equals(Set.of('G', 'P'))) {
                        if (words.get('P') < 0 || words.get('P') > 60) throw rejected(number, "Dwell must be between 0 and 60 seconds");
                    } else if (g != null && (g == 0 || g == 1)
                            && words.keySet().stream().allMatch(k -> "GXYF".indexOf(k) >= 0)
                            && words.containsKey('X') && words.containsKey('Y')) {
                        double x = words.get('X'), y = words.get('Y');
                        if (x < 0 || y < 0 || x > options.machineWidth || y > options.machineHeight)
                            throw rejected(number, "Move exceeds configured machine bounds");
                        if (words.containsKey('F') && (words.get('F') <= 0 || words.get('F') > Math.max(options.feedRateDraw, options.feedRateTravel)))
                            throw rejected(number, "Feed exceeds configured speeds");
                        if (g == 1 && !words.containsKey('F')) throw rejected(number, "G1 requires an explicit feed rate");
                        maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); moved = true;
                    } else throw rejected(number, "Only absolute XY moves, bounded dwell and configured pen commands are allowed; relative motion, offsets, homing and firmware changes are blocked");
                }
            }
            commands.add(line);
        }
        if (!moved) throw new IOException("No XY moves in replay file");
        return new Plan(commands, String.format(Locale.ROOT,
                "%d commands; absolute millimetres in the current work coordinate system.\nXY envelope including origin: 0–%.3f × 0–%.3f mm.\nOnly configured pen commands; no homing, offsets or firmware changes.\nConfirm the work origin, clear the bed, and keep Stop available.", commands.size(), maxX, maxY), options);
    }
    private static List<Object> configurationKey(GcodeOptions options) {
        return List.of(options.machineWidth, options.machineHeight, options.feedRateDraw, options.feedRateTravel,
                String.valueOf(GcodeFormatter.penUp(options)), String.valueOf(GcodeFormatter.penDown(options)));
    }
    private static IOException rejected(int line, String message) { return new IOException("G-code line " + line + ": " + message); }

    public static void replay(Plan plan, PlotterBackend backend, Consumer<String> log) throws IOException {
        boolean completed = false;
        Throwable failure = null;
        try {
            for (String command : plan.commands) {
                if (Thread.currentThread().isInterrupted()) throw new CancellationException("Replay stopped");
                log.accept("> " + command);
                List<String> responses = backend.sendRaw(command);
                if (Thread.currentThread().isInterrupted()) throw new CancellationException("Replay stopped");
                boolean acknowledged = false;
                for (String response : responses) {
                    log.accept(response);
                    String lower = response.trim().toLowerCase(Locale.ROOT);
                    if (lower.startsWith("error") || lower.startsWith("alarm") || lower.startsWith("("))
                        throw new IOException("Replay stopped: " + response);
                    if (lower.equals("ok")) acknowledged = true;
                }
                if (!acknowledged) throw new IOException("Replay stopped: no controller acknowledgement");
            }
            backend.penup();
            completed = true;
        } catch (IOException | RuntimeException | Error thrown) { failure = thrown; throw thrown; }
        finally {
            if (!completed) {
                boolean interrupted = Thread.interrupted();
                try {
                    for (Runnable recovery : List.<Runnable>of(backend::haltMotion, backend::penup)) {
                        try { recovery.run(); }
                        catch (Throwable secondary) {
                            if (failure != null && failure != secondary) failure.addSuppressed(secondary);
                            System.err.println("Replay safety recovery failed: " + secondary);
                        }
                    }
                } finally { if (interrupted) Thread.currentThread().interrupt(); }
            }
        }
    }
}
