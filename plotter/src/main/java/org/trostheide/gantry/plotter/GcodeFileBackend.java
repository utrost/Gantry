package org.trostheide.gantry.plotter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * A {@link PlotterBackend} that writes the same G-code a {@link GcodeBackend} would stream over
 * serial to a {@code .gcode} file instead, for later inspection or replay via
 * {@link GcodeFileReplay}.
 */
public class GcodeFileBackend implements PlotterBackend {

    private final GcodeOptions options;
    private final File file;

    private BufferedWriter writer;
    private double x;
    private double y;
    private boolean penIsDown;

    public GcodeFileBackend(GcodeOptions options, File file) {
        this.options = options;
        this.file = file;
    }

    @Override
    public boolean connect() {
        try {
            writer = new BufferedWriter(new FileWriter(file));
            for (String cmd : GcodeFormatter.setupSequence()) {
                writeLine(cmd);
            }
            return true;
        } catch (IOException e) {
            System.out.println("ERROR: Failed to open G-code file " + file + ": " + e.getMessage());
            writer = null;
            return false;
        }
    }

    @Override
    public void disconnect() {
        if (writer == null) {
            return;
        }
        penup();
        writeLine(GcodeFormatter.home());
        try {
            writer.close();
        } catch (IOException ignored) {
            // best-effort close
        }
        writer = null;
    }

    @Override
    public void moveto(double x, double y) {
        if (penIsDown) {
            penup();
        }
        writeLine(GcodeFormatter.moveto(x, y, options.feedRateTravel));
        this.x = x;
        this.y = y;
    }

    @Override
    public void lineto(double x, double y) {
        if (!penIsDown) {
            pendown();
        }
        writeLine(GcodeFormatter.lineto(x, y, options.feedRateDraw));
        this.x = x;
        this.y = y;
    }

    @Override
    public void move(double dx, double dy) {
        for (String cmd : GcodeFormatter.relativeMove(dx, dy, options.feedRateTravel)) {
            writeLine(cmd);
        }
        this.x += dx;
        this.y += dy;
    }

    @Override
    public void penup() {
        penIsDown = false;
        String cmd = GcodeFormatter.penUp(options);
        if (cmd != null) {
            writeLine(cmd);
        }
    }

    @Override
    public void pendown() {
        penIsDown = true;
        String cmd = GcodeFormatter.penDown(options);
        if (cmd != null) {
            writeLine(cmd);
        }
    }

    @Override
    public double[] queryPosition() {
        return new double[] { x, y };
    }

    @Override
    public List<String> sendRaw(String command) {
        writeLine(command);
        return List.of("(written to file)");
    }

    private void writeLine(String line) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            System.out.println("ERROR: Failed to write G-code line: " + e.getMessage());
        }
    }
}
