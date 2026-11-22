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
    private final boolean simulation;

    private volatile SerialTransport transport;
    private volatile boolean running;
    private volatile boolean penIsDown;
    private volatile boolean collectRaw;
    /** Set while {@link #haltMotion()} is aborting, so the plot thread's in-flight/straggler
     * commands short-circuit instead of fighting the halt for the write lock and "ok" acks. */
    private volatile boolean aborting;
    private volatile GcodeBackendException terminalFailure;
    /** True while $H is intentionally recovering GRBL from its normal alarm lock. */
    private volatile boolean homingFromAlarm;
    private volatile long resetRevision;
    private volatile MachineState machineState = MachineState.UNKNOWN;
    /** Incremented for every fresh realtime status report, including repeated states. */
    private long stateRevision;

    private final Object writeLock = new Object();
    /** Prevents Stop recovery and Home recovery from resetting/interleaving with one another. */
    private final Object recoveryLock = new Object();
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
        this(options, transportFactory, false);
    }

    private GcodeBackend(GcodeOptions options, SerialTransportFactory transportFactory, boolean simulation) {
        this.options = options;
        this.transportFactory = transportFactory;
        this.simulation = simulation;
    }

    /** Guaranteed no-hardware path: the serial-port name is never passed to an OS transport. */
    public static GcodeBackend serialSimulator(GcodeOptions options) {
        return new GcodeBackend(options, (port, baud) -> new SerialSimulatorTransport(options), true);
    }

    @Override public boolean isSimulation() { return simulation || transport instanceof SerialSimulatorTransport; }

    private final java.util.concurrent.atomic.AtomicLong safetyRevision =
            new java.util.concurrent.atomic.AtomicLong();
    private volatile boolean requireSafetyReview, wcoKnown, referenceHomed;
    private volatile FirmwareEnvelope safetyEnvelope;
    private double plannedX, plannedY;

    public record SafetySnapshot(
            FirmwareEnvelope.Limits limits,
            double offsetX,
            double offsetY,
            long referenceRevision,
            boolean homed) {}

    public void requireSafetyReview(boolean required) {
        requireSafetyReview = required;
        invalidateSafetyReview();
    }

    public boolean needsSafetyReview() {
        FirmwareEnvelope envelope = safetyEnvelope;
        return requireSafetyReview
                && (!wcoKnown || envelope == null || !envelope.matches(options, wco));
    }

    public synchronized void invalidateSafetyReview() {
        safetyEnvelope = null;
        referenceHomed = false;
        safetyRevision.incrementAndGet();
    }

    public SafetySnapshot readSafetySnapshot() {
        // A failed/cancelled refresh must not leave an earlier limit receipt usable.
        // Preserve the homing observation while requiring a new explicit approval.
        synchronized (this) {
            safetyEnvelope = null;
            safetyRevision.incrementAndGet();
        }
        awaitMotionComplete();
        var limits = FirmwareEnvelope.Limits.parse(sendRaw("$$"));
        if (!wcoKnown)
            throw new GcodeBackendException(
                    "No valid machine/work offset report. Wait for status and retry; no coordinate"
                        + " assumption made.");
        return new SafetySnapshot(limits, wco[0], wco[1], safetyRevision.get(), referenceHomed);
    }

    public synchronized void approveSafetyReview(
            SafetySnapshot snapshot, FirmwareEnvelope.Space space, boolean referenceConfirmed) {
        if (machineState != MachineState.IDLE || terminalFailure != null || aborting)
            throw new GcodeBackendException(
                    "Controller must remain Idle and healthy throughout reference review");
        if (!referenceConfirmed)
            throw new GcodeBackendException(
                    "Verify the machine reference and paper/work origin explicitly. Reading"
                        + " settings cannot establish position.");
        if (snapshot.referenceRevision() != safetyRevision.get()
                || !wcoKnown
                || Math.abs(snapshot.offsetX() - wco[0]) > 1e-6
                || Math.abs(snapshot.offsetY() - wco[1]) > 1e-6)
            throw new GcodeBackendException(
                    "Controller reference changed during review; read firmware limits again.");
        var reviewed =
                snapshot.limits()
                        .review(
                                options,
                                new double[] {snapshot.offsetX(), snapshot.offsetY()},
                                space);
        double[] position = queryPosition();
        if (position == null || !Double.isFinite(position[0]) || !Double.isFinite(position[1]))
            throw new GcodeBackendException("Work position unavailable.");
        reviewed.check(position[0], position[1]);
        plannedX = position[0];
        plannedY = position[1];
        safetyEnvelope = reviewed;
    }

    @Override
    public void validateWorkEnvelope(double minX, double minY, double maxX, double maxY) {
        if (!requireSafetyReview) return;
        FirmwareEnvelope envelope = safetyEnvelope;
        if (envelope == null || !wcoKnown || !envelope.matches(options, wco))
            throw new GcodeBackendException(
                    "Review firmware limits and machine/work origin before motion (Machine > Review"
                        + " firmware limits...). Stop/reset/origin changes invalidate that"
                        + " review.");
        envelope.check(minX, minY);
        envelope.check(maxX, maxY);
    }

    private void validateTarget(double x, double y) {
        validateWorkEnvelope(x, y, x, y);
    }

    private void guardRaw(String command) {
        if (!requireSafetyReview) return;
        String line = command.trim().toUpperCase(java.util.Locale.ROOT);
        String compact = line.replaceAll("\\s+", "");
        if (compact.matches("\\$\\$|\\$[IG]|\\?")
                || compact.matches("G21|G90|G94|G4P[0-9.]+")
                || line.equals(GcodeFormatter.penUp(options))
                || line.equals(GcodeFormatter.penDown(options))) return;
        if (compact.matches("G92[XYZ0-9.+\\-]*|G10.*|\\$[0-9]+=.*|\\$X|\\$RST.*")) {
            invalidateSafetyReview();
            wcoKnown = false;
            return;
        }
        if (compact.matches("G0?[01](?:[XYF].*)?")) {
            var matcher =
                    java.util.regex.Pattern.compile(
                                    "([XYF])([-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+))")
                            .matcher(compact);
            java.util.Map<String, Double> words = new java.util.HashMap<>();
            int end = compact.startsWith("G00") || compact.startsWith("G01") ? 3 : 2;
            while (matcher.find()) {
                if (matcher.start() != end
                        || words.put(matcher.group(1), Double.parseDouble(matcher.group(2)))
                                != null)
                    throw new GcodeBackendException(
                            "Unsupported or duplicate raw motion words; no coordinate assumptions"
                                + " made");
                end = matcher.end();
            }
            if (end != compact.length()
                    || words.values().stream().anyMatch(v -> !Double.isFinite(v)))
                throw new GcodeBackendException("Unsupported raw motion syntax");
            FirmwareEnvelope envelope = safetyEnvelope;
            if (words.containsKey("F")
                    && (envelope == null
                            || words.get("F") <= 0
                            || words.get("F") > envelope.maxFeed()))
                throw new GcodeBackendException(
                        "Raw feed exceeds or lacks a reviewed firmware cap");
            double x = words.getOrDefault("X", plannedX), y = words.getOrDefault("Y", plannedY);
            validateTarget(x, y);
            plannedX = x;
            plannedY = y;
        } else
            throw new GcodeBackendException(
                    "Unsupported raw motion/modal command in reviewed mode. Use Home or reviewed"
                        + " absolute millimetre XY moves; no coordinate assumptions made.");
    }

    public GcodeOptions getOptions() {
        return options;
    }

    /**
     * Register a callback(x, y) invoked from the reader thread whenever GRBL reports a new realtime
     * work position.
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
        invalidateSafetyReview();wcoKnown=false;
        try {
            terminalFailure = null;
            aborting = false;
            // A failed disconnected draw can leave the command cache down. A new session
            // must send its first pen-down command instead of trusting the previous session.
            penIsDown = false;
            ackQueue.clear();
            rawQueue.clear();
            updateMachineState(MachineState.UNKNOWN);
            transport = transportFactory.open(options.serialPort, options.baudRate);
            Thread.sleep(simulation ? 0 : options.bootDelayMillis);

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
                if (!needsSafetyReview()) {
                    validateTarget(0, 0);
                    send(GcodeFormatter.home());
                    waitForOk();
                }
            }
        } catch (GcodeBackendException e) {
            System.out.println("ERROR: Graceful disconnect failed: " + e.getMessage());
        } finally {
            invalidateSafetyReview();
            wcoKnown = false;
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
        validateTarget(x,y);
        if (penIsDown) {
            penup();
        }
        send(GcodeFormatter.moveto(x, y, options.feedRateTravel));
        waitForOk();plannedX=x;plannedY=y;
    }

    @Override
    public void lineto(double x, double y) {
        validateTarget(x,y);
        if (!penIsDown) {
            pendown();
        }
        send(GcodeFormatter.lineto(x, y, options.feedRateDraw));
        waitForOk();plannedX=x;plannedY=y;
    }

    @Override
    public void move(double dx, double dy) {
        if(requireSafetyReview)awaitMotionComplete();
        validateTarget(plannedX+dx,plannedY+dy);
        for (String cmd : GcodeFormatter.relativeMove(dx, dy, options.feedRateTravel)) {
            send(cmd);
            waitForOk();
        }
        plannedX+=dx;plannedY+=dy;
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

    @Override
    public void dwell(long millis) {
        if (millis <= 0) return;
        send(GcodeFormatter.dwell(millis));
        waitForOk();
    }

    /**
     * Runs GRBL's homing cycle ({@code $H}) against the limit switches, then zeroes the work
     * origin at the resulting position so the plotter's logical (0,0) matches the switches.
     * Homing can take much longer than a normal move on large machines, so it gets its own
     * extended wait.
     */
    @Override
    public void home() {
        synchronized (recoveryLock) {
            homeAfterRecovery();
        }
    }

    private void homeAfterRecovery() {
        SerialTransport t = transport;
        if (t == null || !t.isOpen()) {
            return;
        }
        // A realtime Stop resets GRBL and leaves some firmware variants (including DrawCore) in
        // an alarm lock where accepting $H directly is unreliable. Perform a fully acknowledged
        // unlock first, then start homing. Ignore stale asynchronous <Alarm|...> status reports
        // during this short recovery sequence; explicit ALARM:n responses still fail normally.
        ackQueue.clear();
        terminalFailure = null;
        homingFromAlarm = true;
        try {
            send("$X");
            waitForOk(5);
            ackQueue.clear();
            terminalFailure = null;
            // Raise the pen before $H can move any axis. $X must precede this because GRBL rejects
            // ordinary pen commands while alarm-locked, but unlocking itself causes no motion.
            penup();
            // GRBL acknowledges a G0/G1 when it accepts it into the planner, not when motion has
            // completed. Synchronize the planner, then require fresh Idle before $H can start.
            awaitMotionComplete(30);
            // Use the firmware's configured homing cycle as one atomic operation. In particular,
            // DrawCore documents $H for homing; splitting it into generic $HX/$HY cycles can
            // disturb its Z-axis pen actuator and draw during the first axis move.
            send(GcodeFormatter.homingCycle());
            waitForOk(120);
            // A firmware homing cycle may alter or release Z. Wait for it to finish, then restore
            // the configured absolute pen-up height; Home must always finish safely clear.
            awaitFreshIdle(120);
            penup();
            awaitMotionComplete(30);
        } finally {
            homingFromAlarm = false;
        }
        send(GcodeFormatter.zeroWorkOrigin());
        waitForOk(30);
        lastWpos = new double[] { 0.0, 0.0 };
        safetyEnvelope=null;referenceHomed=true;safetyRevision.incrementAndGet();
    }

    /**
     * Immediately halts motion via GRBL's realtime soft-reset ({@code 0x18}), which stops the
     * steppers and discards the planner buffer (the source of the "still moves a bit after Stop"
     * lag, since normal lines only wait for "ok"/acceptance into the buffer, not motion completion).
     * Clears the resulting alarm with {@code $X} and lifts the pen.
     */
    @Override
    public void haltMotion() {
        synchronized (recoveryLock) {
            haltAndRecover();
        }
    }

    private void haltAndRecover() {
        SerialTransport t = transport;
        if (t == null || !t.isOpen()) {
            return;
        }
        // Phase 1 — urgent: a single-byte realtime soft-reset aborts GRBL's planner buffer so
        // motion stops *now*. This byte is safe to write even while the plot thread is mid-command;
        // setting `aborting` makes that thread's remaining send()/waitForOk() calls no-ops so the
        // two threads don't fight over the write lock or the ack queue.
        invalidateSafetyReview();wcoKnown=false;
        resetRevision++;
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
        guardRaw(command);
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

    private void send(String cmd) { send(cmd, Long.MAX_VALUE); }

    private void send(String cmd, long deadline) {
        if (aborting) {
            // Halt in progress: drop the plot thread's straggler commands rather than write them
            // into a machine that's being soft-reset.
            return;
        }
        throwIfFailed();
        awaitNotHeld(deadline);
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
        waitForOk(Math.max(1, options.responseTimeoutSeconds));
    }

    private void waitForOk(long timeoutSeconds) {
        if (transport == null || aborting) return;
        awaitAck(System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds),
                "G-code response timeout after " + timeoutSeconds + "s");
        awaitNotHeld();
    }

    private void awaitAck(long deadline, String timeoutMessage) {
        throwIfFailed();
        String line;
        try {
            line = ackQueue.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GcodeBackendException("Interrupted while waiting for GRBL response", e);
        }
        if (line == null) {
            SerialTransport t = transport;
            String message = t == null || !t.isOpen()
                    ? "Lost connection to plotter (serial port closed)" : timeoutMessage;
            GcodeBackendException failure = new GcodeBackendException(message);
            terminalFailure = failure;
            throw failure;
        }
        throwIfFailed();
        if (line.toLowerCase(Locale.ROOT).startsWith("error")) {
            throw new GcodeBackendException("GRBL rejected a command: " + line);
        }
    }

    @Override
    public MotionCompletion awaitMotionComplete() {
        return awaitMotionComplete(options.responseTimeoutSeconds);
    }

    private MotionCompletion awaitMotionComplete(long timeoutSeconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(1, timeoutSeconds));
        long revision = resetRevision;
        checkIdleWait(deadline, revision);
        // GRBL's zero dwell synchronizes its planner before acknowledging (mc_dwell).
        // A fresh Idle report alone cannot disambiguate old status reports already in transit.
        send("G4 P0", deadline);
        awaitAck(deadline, "Timed out waiting for motion-completion barrier acknowledgement");
        checkIdleWait(deadline, revision);
        awaitFreshIdleUntil(deadline, revision);
        if(requireSafetyReview&&lastWpos!=null){plannedX=lastWpos[0];plannedY=lastWpos[1];}
        return isSimulation() ? MotionCompletion.SIMULATED_IDLE : MotionCompletion.CONTROLLER_IDLE;
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
            if (low.startsWith("grbl ")) {
                invalidateSafetyReview();wcoKnown=false;
        resetRevision++;
                if (!aborting) {
                    if (terminalFailure == null)
                        terminalFailure =
                                new GcodeBackendException(
                                        "Controller restarted unexpectedly; review work origin"
                                            + " before recovery");
                    updateMachineState(MachineState.UNKNOWN);
                    ackQueue.offer(FAILURE_SENTINEL); rawQueue.offer(FAILURE_SENTINEL); wakeStateWaiters();
                }
            } else if (low.startsWith("alarm:")) {
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
                recordSerialFailure(
                        "Serial status polling failed (lost connection to plotter?)", e);
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
                    if(wcoKnown&&(Math.abs(wco[0]-w[0])>1e-6||Math.abs(wco[1]-w[1])>1e-6))invalidateSafetyReview();
                    wco = w;wcoKnown=Double.isFinite(w[0])&&Double.isFinite(w[1]);
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
        updateMachineState(state);
        if (state == MachineState.ALARM && !homingFromAlarm) {
            handleAlarm("GRBL status: " + stateToken);
        }
            return;
        }

        lastWpos = pos;
        updateMachineState(state);
        if (state == MachineState.ALARM && !homingFromAlarm) {
            handleAlarm("GRBL status: " + stateToken);
        }

        if (positionCallback != null) {
            try {
                positionCallback.accept(pos[0], pos[1]);
            } catch (Exception ignored) {
                // callbacks must not break the reader thread
            }
        }
    }

    private void handleAlarm(String detail) {
        invalidateSafetyReview();
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
        invalidateSafetyReview();
        wcoKnown = false;
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
    private void awaitNotHeld() { awaitNotHeld(Long.MAX_VALUE); }

    private void awaitNotHeld(long deadline) {
        synchronized (stateLock) {
            while (isPausedMachineState(machineState) && !aborting) {
                throwIfFailed();
                if (deadline != Long.MAX_VALUE) checkIdleWait(deadline);
                try {
                    stateLock.wait(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new GcodeBackendException(
                            "Interrupted while waiting for GRBL Hold to clear", e);
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
        synchronized (stateLock) {
            machineState = state;
            stateRevision++;
            stateLock.notifyAll();
        }
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

    /** Waits for a newly requested realtime report to say Idle, never trusting a stale cached state. */
    private void awaitFreshIdle(long timeoutSeconds) {
        awaitFreshIdleUntil(System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds), -1);
    }

    private void awaitFreshIdleUntil(long deadline, long revision) {
        while (true) {
            checkIdleWait(deadline, revision);
            long observedRevision;
            synchronized (stateLock) {
                observedRevision = stateRevision;
            }
            SerialTransport t = transport;
            if (t == null || !t.isOpen()) {
                throw new GcodeBackendException(
                        "Lost connection while waiting for motion completion");
            }
            synchronized (writeLock) {
                try {
                    t.writeBytes(new byte[] {'?'});
                } catch (IOException e) {
                    throw recordSerialFailure("Serial status request failed", e);
                }
            }
            synchronized (stateLock) {
                while (stateRevision <= observedRevision) {
                    checkIdleWait(deadline, revision);
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        throw new GcodeBackendException(
                                "Timed out waiting for fresh GRBL Idle status");
                    }
                    try {
                        TimeUnit.NANOSECONDS.timedWait(stateLock, remaining);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new GcodeBackendException(
                                "Interrupted while waiting for GRBL Idle", e);
                    }
                }
                checkIdleWait(deadline, revision);
                if (machineState == MachineState.IDLE) {
                    return;
                }
            }
            // Bound realtime polling while motion is active; do not flood the serial link.
            sleepQuietly(50);
        }
    }

    private void checkIdleWait(long deadline) { checkIdleWait(deadline, -1); }

    private void checkIdleWait(long deadline, long revision) {
        throwIfFailed();
        if (revision >= 0 && (resetRevision != revision || aborting)) {
            throw new GcodeBackendException("Controller reset during motion-completion wait",
                    new java.util.concurrent.CancellationException("Motion stopped"));
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new GcodeBackendException(
                    "Interrupted while waiting for GRBL Idle/motion completion",
                    new InterruptedException());
        }
        if (System.nanoTime() >= deadline) {
            throw new GcodeBackendException("Timed out waiting for GRBL Idle/motion completion");
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
