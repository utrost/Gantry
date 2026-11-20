package org.trostheide.gantry.plotter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Workflow simulator with cancellable feed-based XY interpolation, not a firmware emulator. */
public class MockPlotterBackend implements PlotterBackend {
    private static final Pattern WORD = Pattern.compile("([A-Z])([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+))");
    private final GcodeOptions options;
    private final Consumer<String> log;
    private final Object state = new Object();
    private boolean connected;
    private boolean penDown;
    private double x, y;
    private int feedOverride = 100;
    private boolean absolute = true;
    private double rawFeed;
    private Thread activeWorker;
    private long generation, activeGeneration;
    private double stepsPerMmX = 80, stepsPerMmY = 80;
    private int softLimits, hardLimits, homingEnable, homingDirMask = 3;
    private String simulatedPins = "";

    public MockPlotterBackend(GcodeOptions options, Consumer<String> log) {
        this.options = options != null ? options : new GcodeOptions();
        this.log = log != null ? log : message -> { };
    }
    public MockPlotterBackend(Consumer<String> log) { this(new GcodeOptions(), log); }
    public MockPlotterBackend() { this(new GcodeOptions(), null); }

    public void setSimulatedPins(String pins) {
        synchronized (state) { simulatedPins = pins == null ? "" : pins; }
    }
    /** Simulator observation used to verify recovery; it does not report a physical actuator. */
    public boolean isPenDown() { synchronized (state) { return penDown; } }

    @Override public boolean connect() {
        synchronized (state) {
            if (activeWorker != null) throw new IllegalStateException("Mock operation is still finishing");
            if (connected) return true;
            connected = true; penDown = false; absolute = true; x = 0; y = 0;
            rawFeed = options.feedRateTravel; feedOverride = 100;
        }
        log.accept("[Mock] Connected.");
        return true;
    }
    @Override public void disconnect() {
        Thread worker;
        synchronized (state) {
            connected = false; penDown = false; generation++; worker = activeWorker;
        }
        interruptOther(worker);
        log.accept("[Mock] Disconnected.");
    }
    @Override public void haltMotion() {
        Thread worker;
        synchronized (state) {
            // Invalidating once is enough; repeated Stop must not interrupt cleanup again.
            worker = activeWorker != null && generation == activeGeneration ? activeWorker : null;
            if (worker != null) generation++;
            penDown = false;
            absolute = true;
        }
        interruptOther(worker);
        log.accept("[Mock] Motion halted; pen up.");
    }
    private static void interruptOther(Thread worker) {
        if (worker != null && worker != Thread.currentThread()) worker.interrupt();
    }
    @Override public void moveto(double x, double y) {
        finite(x, y);
        operation(() -> {
            setPen(false);
            synchronized (state) { checkActive(); rawFeed = options.feedRateTravel; }
            log.accept(String.format(Locale.ROOT, "[Mock] Move to (%.2f, %.2f) [pen up]", x, y));
            simulatedMove(x, y, options.feedRateTravel);
        });
    }
    @Override public void lineto(double x, double y) {
        finite(x, y);
        operation(() -> {
            lowerPen();
            synchronized (state) { checkActive(); rawFeed = options.feedRateDraw; }
            log.accept(String.format(Locale.ROOT, "[Mock] Draw to (%.2f, %.2f) [pen down]", x, y));
            simulatedMove(x, y, options.feedRateDraw);
        });
    }
    @Override public void move(double dx, double dy) {
        finite(dx, dy);
        operation(() -> {
            double tx, ty;
            synchronized (state) { checkActive(); tx = x + dx; ty = y + dy; rawFeed = options.feedRateTravel; }
            simulatedMove(tx, ty, options.feedRateTravel);
            synchronized (state) { checkActive(); absolute = true; }
        });
    }
    @Override public void penup() {
        // Recovery is allowed on an interrupted owner, and never waits or interrupts its cleanup.
        synchronized (state) { requireConnected(); penDown = false; }
        log.accept("[Mock] Pen UP");
    }
    @Override public void pendown() { operation(this::lowerPen); }
    private void lowerPen() {
        boolean changed;
        synchronized (state) {
            checkActive(); changed = !penDown; penDown = true;
            if ("zaxis".equals(options.penMode)) rawFeed = options.feedRateDraw;
        }
        if (changed) { log.accept("[Mock] Pen DOWN"); delay(options.penDownDelayMillis); }
    }
    private void setPen(boolean down) { synchronized (state) { checkActive(); penDown = down; } }
    @Override public void dwell(long millis) { operation(() -> delay(Math.max(0, millis))); }
    @Override public double[] queryPosition() {
        synchronized (state) { return connected ? new double[] {x, y} : null; }
    }
    @Override public void home() {
        operation(() -> {
            synchronized (state) { checkActive(); penDown = false; x = 0; y = 0; absolute = true; }
            log.accept("[Mock] Homing cycle -> origin zeroed at (0.00, 0.00)");
        });
    }
    @Override public void adjustSpeed(String direction) {
        synchronized (state) {
            requireConnected();
            switch (direction.toLowerCase(Locale.ROOT)) {
                case "up" -> feedOverride = Math.min(200, feedOverride + 10);
                case "down" -> feedOverride = Math.max(10, feedOverride - 10);
                case "reset" -> feedOverride = 100;
                default -> { return; }
            }
        }
    }

    @Override public List<String> sendRaw(String command) {
        String cmd = command == null ? "" : command.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        synchronized (state) {
            if (!connected) return List.of("(no serial connection)");
            if (cmd.equals("?")) {
                String pins = simulatedPins.isEmpty() ? "" : "|Pn:" + simulatedPins;
                return List.of(String.format(Locale.ROOT, "<%s|WPos:%.3f,%.3f,0.000|FS:0,0%s>",
                        activeWorker == null ? "Idle" : "Run", x, y, pins));
            }
            if (cmd.equals("$$")) return settingsDump();
        }
        try {
            // Validate the complete line before changing any state. Unsupported commands fail explicitly.
            Runnable action = rawAction(cmd);
            operation(action);
            log.accept("[Mock] Raw: " + command);
            return List.of("ok");
        } catch (IllegalArgumentException | IllegalStateException rejected) {
            return List.of("error: Mock " + rejected.getMessage());
        }
    }
    private Runnable rawAction(String cmd) {
        if (cmd.equals("G21") || cmd.equals("G94") || cmd.equals("$X")) return () -> { };
        if (cmd.equals("G90") || cmd.equals("G91")) return () -> {
            synchronized (state) { checkActive(); absolute = cmd.equals("G90"); }
        };
        if (cmd.equals("$H")) return this::home;
        String up = GcodeFormatter.penUp(options), down = GcodeFormatter.penDown(options);
        if (up != null && cmd.equals(up.replaceAll("\\s+", ""))) return () -> setPen(false);
        if (down != null && cmd.equals(down.replaceAll("\\s+", ""))) return this::lowerPen;
        if (cmd.startsWith("$")) return settingWrite(cmd);
        Map<Character, Double> words = words(cmd);
        Double g = words.get('G');
        if (g != null && g == 4 && words.keySet().equals(java.util.Set.of('G', 'P'))) {
            double seconds = words.get('P');
            if (seconds < 0 || seconds > 60) throw new IllegalArgumentException("dwell must be 0–60 seconds");
            return () -> delay(Math.round(seconds * 1000));
        }
        if (g != null && g == 92 && words.keySet().stream().allMatch(k -> "GXY".indexOf(k) >= 0)
                && (words.containsKey('X') || words.containsKey('Y'))) return () -> {
            synchronized (state) { checkActive(); x = words.getOrDefault('X', x); y = words.getOrDefault('Y', y); }
        };
        if (g == null || (g != 0 && g != 1) || words.keySet().stream().anyMatch(k -> "GXYF".indexOf(k) < 0)
                || (!words.containsKey('X') && !words.containsKey('Y')))
            throw new IllegalArgumentException("unsupported raw command");
        if (words.containsKey('F') && words.get('F') <= 0) throw new IllegalArgumentException("feed must be positive");
        return () -> {
            double tx, ty, feed;
            synchronized (state) {
                checkActive();
                if (words.containsKey('F')) rawFeed = words.get('F');
                tx = words.containsKey('X') ? words.get('X') + (absolute ? 0 : x) : x;
                ty = words.containsKey('Y') ? words.get('Y') + (absolute ? 0 : y) : y;
                feed = g == 0 ? options.feedRateTravel : rawFeed;
            }
            simulatedMove(tx, ty, feed); // Raw motion preserves the explicit pen state, as G-code does.
        };
    }
    private static Map<Character, Double> words(String cmd) {
        Map<Character, Double> words = new LinkedHashMap<>();
        Matcher m = WORD.matcher(cmd);
        int end = 0;
        while (m.find()) {
            if (m.start() != end) throw new IllegalArgumentException("unsupported raw syntax");
            double value = Double.parseDouble(m.group(2));
            if (!Double.isFinite(value) || words.put(m.group(1).charAt(0), value) != null)
                throw new IllegalArgumentException("duplicate or invalid word");
            end = m.end();
        }
        if (end != cmd.length() || words.isEmpty()) throw new IllegalArgumentException("unsupported raw syntax");
        return words;
    }
    private Runnable settingWrite(String cmd) {
        int equal = cmd.indexOf('=');
        if (equal < 0) throw new IllegalArgumentException("unsupported setting");
        String key = cmd.substring(0, equal);
        double value = Double.parseDouble(cmd.substring(equal + 1));
        if (!Double.isFinite(value)) throw new IllegalArgumentException("invalid setting value");
        boolean steps = key.equals("$100") || key.equals("$101");
        if (steps ? value <= 0 : !(key.matches("\\$2[0-3]") && value == Math.rint(value)
                && value >= 0 && value <= (key.equals("$23") ? 7 : 1)))
            throw new IllegalArgumentException("unsupported setting or value");
        return () -> {
            synchronized (state) {
                checkActive();
                switch (key) {
                    case "$100" -> stepsPerMmX = value;
                    case "$101" -> stepsPerMmY = value;
                    case "$20" -> softLimits = (int) value;
                    case "$21" -> hardLimits = (int) value;
                    case "$22" -> homingEnable = (int) value;
                    case "$23" -> homingDirMask = (int) value;
                    default -> throw new IllegalArgumentException("unsupported setting");
                }
            }
        };
    }
    private List<String> settingsDump() {
        return List.of("$20=" + softLimits, "$21=" + hardLimits, "$22=" + homingEnable,
                "$23=" + homingDirMask, "$100=" + stepsPerMmX, "$101=" + stepsPerMmY, "ok");
    }

    private void operation(Runnable action) {
        boolean owner;
        synchronized (state) {
            requireConnected();
            owner = activeWorker == null;
            if (!owner && activeWorker != Thread.currentThread()) throw new IllegalStateException("operation busy");
            if (owner) { activeWorker = Thread.currentThread(); activeGeneration = generation; }
        }
        try {
            synchronized (state) { checkActive(); }
            action.run();
            synchronized (state) { checkActive(); }
        } finally {
            if (owner) synchronized (state) { activeWorker = null; }
        }
    }
    private void requireConnected() {
        if (!connected) throw new IllegalStateException("Mock plotter is disconnected");
    }
    private void checkActive() {
        if (Thread.currentThread().isInterrupted() || generation != activeGeneration)
            throw new CancellationException("Simulated operation stopped");
        requireConnected();
    }
    private void simulatedMove(double toX, double toY, double feed) {
        finite(toX, toY, feed);
        if (feed <= 0) throw new IllegalArgumentException("Feed must be positive");
        double fromX, fromY, override;
        synchronized (state) { checkActive(); fromX = x; fromY = y; override = feedOverride / 100.0; }
        double duration = Math.hypot(toX - fromX, toY - fromY) / (feed * override) * 60_000;
        if (!Double.isFinite(duration) || duration > Long.MAX_VALUE) throw new IllegalArgumentException("Move too large");
        long total = Math.round(duration), elapsed = 0;
        while (elapsed < total) {
            long step = Math.min(50, total - elapsed);
            delay(step);
            elapsed += step;
            synchronized (state) {
                checkActive();
                x = fromX + (toX - fromX) * ((double) elapsed / total);
                y = fromY + (toY - fromY) * ((double) elapsed / total);
            }
        }
        synchronized (state) { checkActive(); x = toX; y = toY; }
    }
    private void delay(long millis) {
        synchronized (state) { checkActive(); }
        try { if (millis > 0) awaitStep(millis); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Simulated operation interrupted");
        }
        synchronized (state) { checkActive(); }
    }
    /** Timing seam for deterministic tests; production waits are interruptible. */
    protected void awaitStep(long millis) throws InterruptedException { Thread.sleep(millis); }
    private static void finite(double... values) {
        for (double v : values) if (!Double.isFinite(v)) throw new IllegalArgumentException("Coordinates/feed must be finite");
    }
}
