package org.trostheide.gantry.plotter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

/**
 * GRBL/G-code plotter backend over a serial connection. Port of the Python
 * {@code gcode_backend.py}.
 *
 * <p>Serial reads are owned by a single reader thread, because GRBL responds
 * to the realtime {@code ?} status query with a status report at any time,
 * interleaved with the line-based command/"ok" protocol. The reader
 * classifies each line and routes it to the right consumer:
 * <ul>
 *   <li>"ok"/"error" lines &rarr; {@code ackQueue} (unblocks {@link #waitForOk()})
 *       or {@code rawQueue} while {@link #sendRaw} is collecting</li>
 *   <li>"&lt;...&gt;" status reports &rarr; {@link #handleStatus}</li>
 *   <li>anything else &rarr; printed as a GRBL message</li>
 * </ul>
 * A second poller thread periodically sends the realtime {@code ?} query.
 */
public class GcodeBackend implements PlotterBackend {

    /** Coarse GRBL controller states surfaced by realtime {@code <...>} reports. */
    public enum MachineState {
        UNKNOWN, IDLE, RUN, HOLD, ALARM, JOG, HOME, CHECK, DOOR, SLEEP, DISCONNECTED, OTHER
    }

    private static final String FAILURE_SENTINEL = "\u0000failure";

    private final GcodeOptions options;
    private final SerialTransportFactory transportFactory;

    private volatile SerialTransport transport;
    private volatile boolean running;
    private volatile boolean penIsDown;
    private volatile boolean collectRaw;
    /** Set while {@link #haltMotion()} is aborting, so the plot thread's in-flight/straggler
     * commands short-circuit instead of fighting the halt for the write lock and "ok" acks. */
    private volatile boolean aborting;
    private volatile GcodeBackendException terminalFailure;
    private volatile MachineState machineState = MachineState.UNKNOWN;

    private final Object writeLock = new Object();
    private final Object stateLock = new Object();
    private final BlockingQueue<String> ackQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> rawQueue = new LinkedBlockingQueue<>();

    /** Latest known work position and work-coordinate offset reported by GRBL. */
    private volatile double[] lastWpos = {0.0, 0.0};
    private volatile double[] wco = {0.0, 0.0};

    /** Realtime feed-rate override, percent of programmed feed (GRBL clamps to 10-200). */
    private volatile int feedOverride = 100;

    private BiConsumer<Double, Double> positionCallback;
    private IntConsumer speedCallback;
    private java.util.function.Consumer<String> sentCommandCallback;
    private java.util.function.Consumer<MachineState> stateCallback;

    private Thread readerThread;
    private Thread pollerThread;

    public GcodeBackend(GcodeOptions options) {
        this(options, (port, baud) -> JSerialCommTransport.open(port, baud, 1000));
    }

    public GcodeBackend(GcodeOptions options, SerialTransportFactory transportFactory) {
        this.options = options;
        this.transportFactory = transportFactory;
    }

    public GcodeOptions getOptions() {
        return options;
    }

    /**
     * Register a callback(x, y) invoked from the reader thread whenever GRBL reports a
     * new realtime work position.
     */
    public void setPositionCallback(BiConsumer<Double, Double> callback) {
        this.positionCallback = callback;
    }

    /** Register a callback(percent) invoked when GRBL's active feed-rate override changes. */
    public void setSpeedCallback(IntConsumer callback) {
        this.speedCallback = callback;
    }

    /** Register a callback(line) invoked with every G-code line as it's sent to the machine. */
    public void setSentCommandCallback(java.util.function.Consumer<String> callback) {
        this.sentCommandCallback = callback;
    }

    /** Register a callback invoked when the controller enters a different GRBL state. */
    public void setStateCallback(java.util.function.Consumer<MachineState> callback) {
        this.stateCallback = callback;
    }

    /** Returns the latest state reported by GRBL. */
    public MachineState getMachineState() {
        return machineState;
    }

    /**
     * Change the plot speed in realtime via GRBL feed-rate override. These are
     * single-byte realtime commands processed immediately, without disturbing the
     * planner buffer, so they take effect mid-move.
     * <ul>
     *   <li>"up" &rarr; +10% (0x91)</li>
     *   <li>"down" &rarr; -10% (0x92)</li>
     *   <li>"reset" &rarr; 100% (0x90)</li>
     * </ul>
     */
    @Override
    public void adjustSpeed(String direction) {
        SerialTransport t = transport;
        if (t == null || !t.isOpen()) {
            return;
        }
        byte[] command = switch (direction.toLowerCase(Locale.ROOT)) {
            case "up" -> new byte[] { (byte) 0x91 };
            case "down" -> new byte[] { (byte) 0x92 };
            case "reset" -> new byte[] { (byte) 0x90 };
            default -> null;
        };
        if (command == null) {
            return;
        }
        synchronized (writeLock) {
            try {
                t.writeBytes(command);
            } catch (IOException e) {
                throw recordSerialFailure("Serial realtime-command write failed", e);
            }
        }
    }

    @Override
    public boolean connect() {
        try {
            terminalFailure = null;
            aborting = false;
            ackQueue.clear();
            rawQueue.clear();
            updateMachineState(MachineState.UNKNOWN);
            transport = transportFactory.open(options.serialPort, options.baudRate);
            Thread.sleep(options.bootDelayMillis);

            // Drain and display the GRBL boot banner before the reader starts.
            String banner;
            while ((banner = transport.readLine()) != null) {
                if (!banner.isEmpty()) {
                    System.out.println("GRBL: " + banner);
                }
            }

            // Start the reader thread so waitForOk() can receive acks.
            running = true;
            readerThread = new Thread(this::readerLoop, "gcode-reader");
            readerThread.setDaemon(true);
            readerThread.start();

            // Unlock alarm state (GRBL boots in alarm on many setups), set units/mode and zero the origin.
            for (String cmd : GcodeFormatter.setupSequence()) {
                send(cmd);
                waitForOk();
            }

            // Start realtime position polling.
            pollerThread = new Thread(this::pollerLoop, "gcode-poller");
            pollerThread.setDaemon(true);
            pollerThread.start();
            return true;
        } catch (Exception e) {
            System.out.println("ERROR: G-code connect failed: " + e.getMessage());
            running = false;
            SerialTransport t = transport;
            if (t != null) {
                // Release the OS handle on failure so a retry doesn't also fail with
                // "Failed to open serial port" because this attempt's handle is still open.
                t.close();
            }
            transport = null;
            updateMachineState(MachineState.DISCONNECTED);
            return false;
        }
    }

    @Override
    public void disconnect() {
        SerialTransport t = transport;
        try {
            if (t != null && t.isOpen() && terminalFailure == null) {
                penup();
                send(GcodeFormatter.home());
                waitForOk();
            }
        } catch (GcodeBackendException e) {
            System.out.println("ERROR: Graceful disconnect failed: " + e.getMessage());
        } finally {
            running = false;
            if (t != null) {
                synchronized (writeLock) {
                    t.close();
                }
            }
        }
        wakeStateWaiters();
        joinQuietly(readerThread);
        joinQuietly(pollerThread);
        transport = null;
        // The connection coordinator already presents a normal user-requested disconnect. Keep
        // the backend state accurate without misreporting it to the UI as an unexpected loss.
        machineState = MachineState.DISCONNECTED;
        wakeStateWaiters();
    }

    @Override
    public void moveto(double x, double y) {
        if (penIsDown) {
            penup();
        }
        send(GcodeFormatter.moveto(x, y, options.feedRateTravel));
        waitForOk();
    }

    @Override
    public void lineto(double x, double y) {
        if (!penIsDown) {
            pendown();
        }
        send(GcodeFormatter.lineto(x, y, options.feedRateDraw));
        waitForOk();
    }

    @Override
    public void move(double dx, double dy) {
        for (String cmd : GcodeFormatter.relativeMove(dx, dy, options.feedRateTravel)) {
            send(cmd);
            waitForOk();
        }
    }

    @Override
    public void penup() {
        penIsDown = false;
        String cmd = GcodeFormatter.penUp(options);
        if (cmd != null) {
            send(cmd);
            waitForOk();
        }
    }

    @Override
    public void pendown() {
        penIsDown = true;
        String cmd = GcodeFormatter.penDown(options);
        if (cmd != null) {
            send(cmd);
            waitForOk();
        }
        sleepQuietly(options.penDownDelayMillis);
    }

    @Override
    public void pendown(double zDown) {
        penIsDown = true;
        String cmd = GcodeFormatter.penDownAt(options, zDown);
        if (cmd != null) {
            send(cmd);
            waitForOk();
        }
        sleepQuietly(options.penDownDelayMillis);
    }

    /**
     * Runs GRBL's homing cycle ({@code $H}) against the limit switches, then zeroes the work
     * origin at the resulting position so the plotter's logical (0,0) matches the switches.
     * Homing can take much longer than a normal move on large machines, so it gets its own
     * extended wait.
     */
    @Override
    public void home() {
        SerialTransport t = transport;
        if (t == null || !t.isOpen()) {
            return;
        }
        send(GcodeFormatter.homingCycle());
        waitForOk(120);
        send(GcodeFormatter.zeroWorkOrigin());
        waitForOk(30);
        lastWpos = new double[] { 0.0, 0.0 };
    }

    /**
     * Immediately halts motion via GRBL's realtime soft-reset ({@code 0x18}), which stops the
     * steppers and discards the planner buffer (the source of the "still moves a bit after Stop"
     * lag, since normal lines only wait for "ok"/acceptance into the buffer, not motion completion).
     * Clears the resulting alarm with {@code $X} and lifts the pen.
     */
    @Override
    public void haltMotion() {
        SerialTransport t = transport;
        if (t == null || !t.isOpen()) {
            return;
        }
        // Phase 1 — urgent: a single-byte realtime soft-reset aborts GRBL's planner buffer so
        // motion stops *now*. This byte is safe to write even while the plot thread is mid-command;
        // setting `aborting` makes that thread's remaining send()/waitForOk() calls no-ops so the
        // two threads don't fight over the write lock or the ack queue.
        aborting = true;
        synchronized (writeLock) {
            try {
                t.writeBytes(new byte[] { 0x18 });
            } catch (IOException ignored) {
                // realtime commands are fire-and-forget
            }
        }
        // After the soft-reset the "ok" the plot thread is blocked on will never arrive; push a
        // sentinel so its waitForOk() returns promptly and it can observe cancellation and exit.
        ackQueue.clear();
        ackQueue.offer("ok");
        sleepQuietly(500);

        // Phase 2 — recover on this single thread now that the plot thread has stood down.
        ackQueue.clear();
        rawQueue.clear();
        terminalFailure = null;
        aborting = false;
        send("$X");      // clear the post-reset alarm state
        waitForOk(5);
        penup();         // lift the pen so it isn't left resting on the paper
    }

    /** Returns the most recent work position reported by the poller, or null if disconnected. */
    @Override
    public double[] queryPosition() {
        SerialTransport t = transport;
        if (t == null || !t.isOpen()) {
            return null;
        }
        return lastWpos;
    }

    @Override
    public List<String> sendRaw(String command) {
        SerialTransport t = transport;
        if (t == null || !t.isOpen()) {
            return List.of("(no serial connection)");
        }
        rawQueue.clear();
        collectRaw = true;
        List<String> responses = new ArrayList<>();
        try {
            send(command);
            long deadline = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < deadline) {
                throwIfFailed();
                String response;
                try {
                    response = rawQueue.poll(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (response == null) {
                    continue;
                }
                if (FAILURE_SENTINEL.equals(response)) {
                    throwIfFailed();
                }
                responses.add(response);
                String low = response.toLowerCase(Locale.ROOT);
                if (low.startsWith("ok") || low.startsWith("error")) {
                    break;
                }
            }
        } finally {
            collectRaw = false;
        }
        return responses.isEmpty() ? List.of("(no response)") : responses;
    }

    // --- Internal serial plumbing ---------------------------------------

    private void send(String cmd) {
        if (aborting) {
            // Halt in progress: drop the plot thread's straggler commands rather than write them
            // into a machine that's being soft-reset.
            return;
        }
        throwIfFailed();
        awaitNotHeld();
        SerialTransport t = transport;
        if (t == null || !t.isOpen()) {
            throw recordSerialFailure("Lost connection to plotter",
                    new IOException("serial port closed"));
        }
        synchronized (writeLock) {
            try {
                t.writeLine(cmd);
                if (sentCommandCallback != null) {
                    sentCommandCallback.accept(cmd);
                }
            } catch (IOException e) {
                // A failed write almost always means the port vanished (cable unplugged, adapter
                // reset). Surface it instead of swallowing it, so the operator doesn't think a
                // plot finished cleanly when it actually died mid-stroke.
                throw recordSerialFailure("Serial write failed (lost connection to plotter?)", e);
            }
        }
    }

    private void waitForOk() {
        waitForOk(30);
    }

    private void waitForOk(long timeoutSeconds) {
        if (transport == null || aborting) {
            return;
        }
        throwIfFailed();
        String line;
        try {
            line = ackQueue.poll(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GcodeBackendException("Interrupted while waiting for GRBL response", e);
        }
        if (line == null) {
            // No ack within the timeout. Distinguish a genuine lost connection (port closed) from a
            // slow machine, so a mid-plot disconnect reads as an ERROR rather than a soft warning
            // that the plot loop quietly plows through.
            SerialTransport t = transport;
            String message = t == null || !t.isOpen()
                    ? "Lost connection to plotter (serial port closed)"
                    : "G-code response timeout after " + timeoutSeconds + "s";
            GcodeBackendException failure = new GcodeBackendException(message);
            terminalFailure = failure;
            throw failure;
        }
        throwIfFailed();
        if (line.toLowerCase(Locale.ROOT).startsWith("error")) {
            throw new GcodeBackendException("GRBL rejected a command: " + line);
        }
        awaitNotHeld();
    }

    /**
     * Single owner of serial reads. Classifies each line and dispatches acks, status
     * reports and raw responses to the right consumer.
     */
    private void readerLoop() {
        while (running) {
            SerialTransport t = transport;
            if (t == null || !t.isOpen()) {
                if (running && !aborting) {
                    recordSerialFailure("Lost connection to plotter",
                            new IOException("serial port closed"));
                }
                break;
            }
            String line;
            try {
                line = t.readLine();
            } catch (IOException e) {
                recordSerialFailure("Serial read failed (lost connection to plotter?)", e);
                break;
            }
            if (line == null || line.isEmpty()) {
                continue;
            }
            String low = line.toLowerCase(Locale.ROOT);
            if (low.startsWith("alarm:")) {
                handleAlarm(line);
            } else if (low.startsWith("ok") || low.startsWith("error")) {
                if (collectRaw) {
                    rawQueue.add(line);
                } else {
                    ackQueue.add(line);
                }
            } else if (line.startsWith("<")) {
                // Status reports are pushed asynchronously by the background poller, unrelated to
                // whatever command sendRaw() is currently waiting on -- never fold them into its
                // response collection, or an unlucky poll tick pollutes the command's response list.
                handleStatus(line);
            } else {
                if (collectRaw) {
                    rawQueue.add(line);
                } else {
                    System.out.println("GRBL: " + line);
                }
            }
        }
    }

    /** Periodically requests a GRBL status report via the realtime {@code ?} command. */
    private void pollerLoop() {
        long intervalMs = Math.max(0, (long) (options.positionPollIntervalSeconds * 1000));
        while (running) {
            SerialTransport t = transport;
            if (t == null || !t.isOpen()) {
                if (running && !aborting) {
                    recordSerialFailure("Lost connection to plotter",
                            new IOException("serial port closed"));
                }
                break;
            }
            try {
                synchronized (writeLock) {
                    t.writeBytes(new byte[] { '?' });
                }
            } catch (IOException e) {
                recordSerialFailure("Serial status polling failed (lost connection to plotter?)", e);
                break;
            }
            sleepQuietly(intervalMs);
        }
    }

    /**
     * Parses a GRBL status report such as
     * {@code <Idle|MPos:1.000,2.000,0.000|FS:0,0|WCO:0.000,0.000,0.000>} and emits the
     * work position via the position callback.
     */
    private void handleStatus(String line) {
        if (line.length() < 2 || !line.endsWith(">")) {
            return;
        }
        String body = line.substring(1, line.length() - 1);
        String stateToken = body.split("\\|", 2)[0];
        MachineState state = parseMachineState(stateToken);
        updateMachineState(state);
        if (state == MachineState.ALARM) {
            handleAlarm("GRBL status: " + stateToken);
        }
        double[] mpos = null;
        double[] wpos = null;
        for (String field : body.split("\\|")) {
            if (field.startsWith("MPos:")) {
                mpos = parseXY(field.substring(5));
            } else if (field.startsWith("WPos:")) {
                wpos = parseXY(field.substring(5));
            } else if (field.startsWith("WCO:")) {
                double[] w = parseXY(field.substring(4));
                if (w != null) {
                    wco = w;
                }
            } else if (field.startsWith("Ov:")) {
                try {
                    int feed = Integer.parseInt(field.substring(3).split(",")[0]);
                    if (feed != feedOverride) {
                        feedOverride = feed;
                        if (speedCallback != null) {
                            speedCallback.accept(feed);
                        }
                    }
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
                    // malformed Ov: field, skip
                }
            }
        }

        double[] pos;
        if (wpos != null) {
            pos = wpos;
        } else if (mpos != null) {
            pos = new double[] { mpos[0] - wco[0], mpos[1] - wco[1] };
        } else {
            return;
        }

        lastWpos = pos;
        if (positionCallback != null) {
            try {
                positionCallback.accept(pos[0], pos[1]);
            } catch (Exception ignored) {
                // callbacks must not break the reader thread
            }
        }
    }

    private void handleAlarm(String detail) {
        updateMachineState(MachineState.ALARM);
        if (aborting) {
            return;
        }
        GcodeBackendException failure = new GcodeBackendException("GRBL alarm: " + detail);
        if (terminalFailure == null) {
            terminalFailure = failure;
            System.out.println("ERROR: " + failure.getMessage());
        }
        ackQueue.offer(FAILURE_SENTINEL);
        rawQueue.offer(detail);
        wakeStateWaiters();
    }

    private GcodeBackendException recordSerialFailure(String message, IOException cause) {
        GcodeBackendException failure = terminalFailure;
        if (failure == null) {
            failure = new GcodeBackendException(message + ": " + cause.getMessage(), cause);
            terminalFailure = failure;
            System.out.println("ERROR: " + failure.getMessage());
        }
        running = false;
        SerialTransport t = transport;
        if (t != null) {
            t.close();
        }
        updateMachineState(MachineState.DISCONNECTED);
        ackQueue.offer(FAILURE_SENTINEL);
        rawQueue.offer(FAILURE_SENTINEL);
        wakeStateWaiters();
        return failure;
    }

    private void throwIfFailed() {
        GcodeBackendException failure = terminalFailure;
        if (failure != null && !aborting) {
            throw failure;
        }
    }

    /** A controller feed-hold pauses plot progression until GRBL reports a resumable state. */
    private void awaitNotHeld() {
        synchronized (stateLock) {
            while (isPausedMachineState(machineState) && !aborting) {
                throwIfFailed();
                try {
                    stateLock.wait(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new GcodeBackendException("Interrupted while waiting for GRBL Hold to clear", e);
                }
            }
        }
        throwIfFailed();
    }

    private static boolean isPausedMachineState(MachineState state) {
        return state == MachineState.HOLD || state == MachineState.DOOR;
    }

    private void updateMachineState(MachineState state) {
        MachineState previous = machineState;
        machineState = state;
        wakeStateWaiters();
        if (state != previous && stateCallback != null) {
            try {
                stateCallback.accept(state);
            } catch (Exception ignored) {
                // presentation callbacks must not break serial handling
            }
        }
    }

    private void wakeStateWaiters() {
        synchronized (stateLock) {
            stateLock.notifyAll();
        }
    }

    private static MachineState parseMachineState(String token) {
        String base = token.split(":", 2)[0].toLowerCase(Locale.ROOT);
        return switch (base) {
            case "idle" -> MachineState.IDLE;
            case "run" -> MachineState.RUN;
            case "hold" -> MachineState.HOLD;
            case "alarm" -> MachineState.ALARM;
            case "jog" -> MachineState.JOG;
            case "home" -> MachineState.HOME;
            case "check" -> MachineState.CHECK;
            case "door" -> MachineState.DOOR;
            case "sleep" -> MachineState.SLEEP;
            case "" -> MachineState.UNKNOWN;
            default -> MachineState.OTHER;
        };
    }

    private static double[] parseXY(String s) {
        try {
            String[] parts = s.split(",");
            return new double[] { Double.parseDouble(parts[0]), Double.parseDouble(parts[1]) };
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void joinQuietly(Thread t) {
        if (t == null) {
            return;
        }
        try {
            t.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
