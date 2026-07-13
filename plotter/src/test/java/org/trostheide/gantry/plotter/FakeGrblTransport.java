package org.trostheide.gantry.plotter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal in-memory simulation of a GRBL controller: replies "ok" to every
 * line command, tracks a simulated XY position from G0/G1 moves, and answers
 * the realtime '?' status query with a status report reflecting that
 * position and the current feed-rate override.
 */
class FakeGrblTransport implements SerialTransport {

    private static final Pattern XY_MOVE = Pattern.compile("G[01] X(-?[0-9.]+) Y(-?[0-9.]+)");

    private final BlockingQueue<String> toClient = new LinkedBlockingQueue<>();
    private final List<String> sentCommands = new CopyOnWriteArrayList<>();
    private final StringBuilder lineBuffer = new StringBuilder();

    private volatile boolean open = true;
    private volatile double x;
    private volatile double y;
    private volatile int feedOverride = 100;
    private volatile String machineState = "Idle";
    private volatile boolean autoAck = true;
    private volatile IOException readFailure;
    private volatile IOException nextLineWriteFailure;

    List<String> sentCommands() {
        return sentCommands;
    }

    void injectLine(String line) {
        toClient.add(line);
    }

    void setMachineState(String state) {
        machineState = state;
    }

    void setAutoAck(boolean enabled) {
        autoAck = enabled;
    }

    void failReads(IOException failure) {
        readFailure = failure;
    }

    void failNextWrite(IOException failure) {
        nextLineWriteFailure = failure;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public String readLine() throws IOException {
        IOException failure = readFailure;
        if (failure != null) {
            throw failure;
        }
        try {
            return toClient.poll(200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public synchronized void writeBytes(byte[] data) throws IOException {
        IOException failure = nextLineWriteFailure;
        if (failure != null && contains(data, (byte) '\n')) {
            nextLineWriteFailure = null;
            throw failure;
        }
        for (byte b : data) {
            switch (b) {
                case '\n' -> {
                    String cmd = lineBuffer.toString();
                    lineBuffer.setLength(0);
                    handleCommand(cmd);
                }
                case (byte) 0x90 -> feedOverride = 100;
                case (byte) 0x91 -> feedOverride = Math.min(200, feedOverride + 10);
                case (byte) 0x92 -> feedOverride = Math.max(10, feedOverride - 10);
                case (byte) 0x18 -> lineBuffer.setLength(0); // soft-reset discards any partial input line
                case '?' -> toClient.add(statusReport());
                default -> lineBuffer.append((char) (b & 0xFF));
            }
        }
    }

    private static boolean contains(byte[] data, byte expected) {
        for (byte value : data) {
            if (value == expected) {
                return true;
            }
        }
        return false;
    }

    private void handleCommand(String cmd) {
        sentCommands.add(cmd);
        Matcher m = XY_MOVE.matcher(cmd);
        if (m.find()) {
            x = Double.parseDouble(m.group(1));
            y = Double.parseDouble(m.group(2));
        }
        if (autoAck) {
            toClient.add("ok");
        }
    }

    private String statusReport() {
        return String.format(java.util.Locale.ROOT,
                "<%s|MPos:%.3f,%.3f,0.000|FS:0,0|WCO:0.000,0.000,0.000|Ov:%d,100,100>",
                machineState, x, y, feedOverride);
    }

    @Override
    public void close() {
        open = false;
    }
}
