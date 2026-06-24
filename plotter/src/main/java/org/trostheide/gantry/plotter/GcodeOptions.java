package org.trostheide.gantry.plotter;

/** Configuration for {@link GcodeBackend}. */
public class GcodeOptions {
    public String serialPort = "/dev/ttyUSB0";
    public int baudRate = 115200;

    /** "servo", "zaxis" or "m3m5". */
    public String penMode = "servo";
    public int servoPin = 0;

    public int feedRateDraw = 1000;
    public int feedRateTravel = 3000;

    public int penServoUp = 60;
    public int penServoDown = 30;

    public double zUp = 5.0;
    public double zDown = 0.0;

    public double machineWidth = 300.0;
    public double machineHeight = 200.0;

    /** How often to poll GRBL for its realtime position, in seconds. */
    public double positionPollIntervalSeconds = 0.1;

    /** Delay after opening the port to let GRBL finish booting, in milliseconds. */
    public int bootDelayMillis = 2000;

    /**
     * Dwell after each pen-down, in milliseconds, to let a (possibly slow) servo/Z move finish
     * lowering before drawing starts. Kept short by default: a long dwell leaves a wet pen
     * sitting motionless on the paper, which pools ink into a visible dot at the start of every
     * line. Raise it only if a slow pen mechanism still skips the first millimetre; set 0 for a
     * fast pen.
     */
    public int penDownDelayMillis = 80;
}
