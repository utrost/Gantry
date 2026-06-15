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

    private final GcodeOptions options;
    private final SerialTransportFactory transportFactory;

    private volatile SerialTransport transport;
    private volatile boolean running;
    private volatile boolean penIsDown;
    private volatile boolean collectRaw;

    private final Object writeLock = new Object();
    private final BlockingQueue<String> ackQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> rawQueue = new LinkedBlockingQueue<>();

    /** Latest known work position and work-coordinate offset reported by GRBL. */
    private volatile double[] lastWpos = {0.0, 0.0};
    private volatile double[] wco = {0.0, 0.0};

    /** Realtime feed-rate override, percent of programmed feed (GRBL clamps to 10-200). */
    private volatile int feedOverride = 100;

    private BiConsumer<Double, Double> positionCallback;
    private IntConsumer speedCallback;

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
            } catch (IOException ignored) {
                // realtime commands are fire-and-forget
            }
        }
    }

    @Override
    public boolean connect() {
        try {
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

            // Unlock alarm state (GRBL boots in alarm on many setups).
            send("$X");
            waitForOk();
            send("G21");
            waitForOk();
            send("G90");
            waitForOk();
            // No homing/limit switches: define the pen's CURRENT position as the work
            // origin (0,0), so absolute moves are relative to where the pen starts.
            send("G92 X0 Y0");
            waitForOk();

            // Start realtime position polling.
            pollerThread = new Thread(this::pollerLoop, "gcode-poller");
            pollerThread.setDaemon(true);
            pollerThread.start();
            return true;
        } catch (Exception e) {
            System.out.println("ERROR: G-code connect failed: " + e.getMessage());
            running = false;
            return false;
        }
    }

    @Override
    public void disconnect() {
        SerialTransport t = transport;
        if (t != null && t.isOpen()) {
            penup();
            send("G0 X0 Y0");
            waitForOk();
            running = false;
            synchronized (writeLock) {
                t.close();
            }
        } else {
            running = false;
        }
        joinQuietly(readerThread);
        joinQuietly(pollerThread);
        transport = null;
    }

    @Override
    public void moveto(double x, double y) {
        if (penIsDown) {
            penup();
        }
        send(String.format(Locale.ROOT, "G0 X%.3f Y%.3f F%d", x, y, options.feedRateTravel));
        waitForOk();
    }

    @Override
    public void lineto(double x, double y) {
        if (!penIsDown) {
            pendown();
        }
        send(String.format(Locale.ROOT, "G1 X%.3f Y%.3f F%d", x, y, options.feedRateDraw));
        waitForOk();
    }

    @Override
    public void move(double dx, double dy) {
        send("G91");
        waitForOk();
        send(String.format(Locale.ROOT, "G1 X%.3f Y%.3f F%d", dx, dy, options.feedRateTravel));
        waitForOk();
        send("G90");
        waitForOk();
    }

    @Override
    public void penup() {
        penIsDown = false;
        switch (options.penMode) {
            case "servo" -> send(String.format(Locale.ROOT, "M280 P%d S%d", options.servoPin, options.penServoUp));
            case "zaxis" -> send(String.format(Locale.ROOT, "G0 Z%.2f", options.zUp));
            case "m3m5" -> send(String.format(Locale.ROOT, "M3 S%d", options.penServoUp));
            default -> { }
        }
        waitForOk();
    }

    @Override
    public void pendown() {
        penIsDown = true;
        switch (options.penMode) {
            case "servo" -> send(String.format(Locale.ROOT, "M280 P%d S%d", options.servoPin, options.penServoDown));
            case "zaxis" -> send(String.format(Locale.ROOT, "G1 Z%.2f F%d", options.zDown, options.feedRateDraw));
            case "m3m5" -> send(String.format(Locale.ROOT, "M3 S%d", options.penServoDown));
            default -> { }
        }
        waitForOk();
        sleepQuietly(150);
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
        SerialTransport t = transport;
        if (t != null && t.isOpen()) {
            synchronized (writeLock) {
                try {
                    t.writeLine(cmd);
                } catch (IOException ignored) {
                    // dropped writes surface as a waitForOk() timeout
                }
            }
        }
    }

    private void waitForOk() {
        if (transport == null) {
            return;
        }
        String line;
        try {
            line = ackQueue.poll(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (line == null) {
            System.out.println("WARNING: G-code response timeout");
            return;
        }
        if (line.toLowerCase(Locale.ROOT).startsWith("error")) {
            System.out.println("WARNING: G-code error: " + line);
        }
    }

    /**
     * Single owner of serial reads. Classifies each line and dispatches acks, status
     * reports and raw responses to the right consumer.
     */
    private void readerLoop() {
        while (running) {
            SerialTransport t = transport;
            if (t == null || !t.isOpen()) {
                break;
            }
            String line;
            try {
                line = t.readLine();
            } catch (IOException e) {
                break;
            }
            if (line == null || line.isEmpty()) {
                continue;
            }
            String low = line.toLowerCase(Locale.ROOT);
            if (low.startsWith("ok") || low.startsWith("error")) {
                if (collectRaw) {
                    rawQueue.add(line);
                } else {
                    ackQueue.add(line);
                }
            } else if (line.startsWith("<")) {
                handleStatus(line);
                if (collectRaw) {
                    rawQueue.add(line);
                }
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
                break;
            }
            try {
                synchronized (writeLock) {
                    t.writeBytes(new byte[] { '?' });
                }
            } catch (IOException e) {
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
        String body = line.substring(1, line.length() - 1);
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
