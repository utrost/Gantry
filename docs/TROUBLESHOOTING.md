# Gantry troubleshooting

This page collects first-run and support checks for users who are installing
Gantry, connecting a GRBL plotter, or trying to complete a first plot.

## Start with the mock backend

If Gantry does not behave as expected, first repeat the task with the mock
backend. A successful mock run separates UI/workflow problems from serial,
controller, wiring, and machine-configuration problems.

Use **Help > Guided First Plot...** to restart the no-hardware route.

## Java and launch problems

### `java` is not found

Install Java 17 or newer and ensure it is on `PATH`.

Check with:

```bash
java -version
```

### The app was built but the JAR is missing

Run from the repository root:

```bash
./scripts/build.sh
```

Then start again:

```bash
./scripts/start.sh
```

On Windows, use the `.cmd` scripts in `scripts\`.

## Serial port problems

### Windows

- Serial devices usually appear as `COM3`, `COM4`, etc.
- Check **Device Manager > Ports (COM & LPT)**.
- If the board uses a CH340 or CP210x USB-serial chip, install the vendor driver
  if Windows did not recognize it automatically.
- Unplug and reconnect the controller, then click **Refresh** in Gantry's serial
  port list.

### macOS

- Serial devices usually appear as `/dev/cu.usbserial-*`, `/dev/cu.usbmodem*`, or
  similar.
- Prefer `/dev/cu.*` for outgoing serial connections.
- If macOS blocks an unsigned driver, check **System Settings > Privacy &
  Security** after installing the driver.

### Linux

- Serial devices usually appear as `/dev/ttyUSB0` or `/dev/ttyACM0`.
- If the port is visible but cannot be opened, add the user to the serial group:

```bash
sudo usermod -aG dialout "$USER"
```

Log out and back in after changing groups.

Check permissions with:

```bash
ls -l /dev/ttyUSB* /dev/ttyACM* 2>/dev/null
```

## Baud rate

Most GRBL controllers use `115200`. If connection succeeds but status is
unreadable or commands time out, confirm the controller's configured baud rate
and update Gantry's connection settings.

## GRBL alarm or lock states

A GRBL controller may reject movement while it is alarmed or locked. Common
causes:

- homing is enabled but not yet run;
- a limit switch is triggered;
- the controller reset while Gantry was connected;
- previous motion was interrupted.

Use the controller's normal unlock/homing procedure. Do not bypass alarms until
the machine bed is clear and the pen is raised.

## Jog direction is wrong

Stop before plotting. In Gantry:

1. raise the pen;
2. use **Move pen manually** for small jogs;
3. note the physical direction of each movement;
4. correct origin, orientation, invert, or swap settings;
5. repeat the jog test.

Do not compensate for wrong jog direction by rotating or mirroring every artwork
file. Fix machine geometry first.

## Drawing appears mirrored, upside down, or in the wrong corner

Check these settings in order:

1. machine origin corner;
2. machine width and height;
3. orientation;
4. extra invert X/Y and swap X/Y;
5. canvas alignment and padding;
6. SVG import options such as mirror or preserve page margins.

Use a simple asymmetric test drawing with text or an arrow when validating
orientation.

## Plot is too large or hits the frame

- Confirm the machine dimensions are the physical travel range.
- Use the safe import default to fit inside the machine bed.
- Keep a margin until the machine is calibrated.
- Use **Check before plotting** and framing before starting.
- If physical movement distance differs from commanded movement distance, use
  axis calibration rather than changing artwork scale by guesswork.

## Pen does not lift or lower correctly

Check the configured pen mode:

- `zaxis` — real Z-axis lift with `Z up` and `Z down` positions;
- `servo` — servo lift values and pin;
- `m3m5` — spindle-style on/off values.

Test lift with the pen clear of the paper first. For early tests, keep the pen
holder high enough that a wrong value cannot gouge the paper or bed.

## Stop/cancel behavior

Gantry's safety invariant is that stopping, cancelling, or failing should raise
the pen. If a real machine does not raise the pen after stop/cancel, record the
backend mode, pen mode, controller state, and last visible Gantry message, then
open a bug report.

## What to include in a bug report

Include:

- Gantry version or commit;
- operating system;
- Java version;
- controller/GRBL version if known;
- serial port and baud rate;
- mock backend or real backend;
- pen mode;
- what you clicked just before the issue;
- expected behavior;
- actual behavior;
- screenshots or logs if available.
