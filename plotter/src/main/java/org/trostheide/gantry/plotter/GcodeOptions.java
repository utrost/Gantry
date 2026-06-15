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
}
