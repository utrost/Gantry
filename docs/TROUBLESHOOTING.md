# Gantry troubleshooting

This page collects first-run and support checks for users who are installing
Gantry, connecting a GRBL plotter, or trying to complete a first plot.

## Start with the mock backend

If Gantry does not behave as expected, first repeat the task with the mock
backend. A successful mock run separates UI/workflow problems from serial,
controller, wiring, and machine-configuration problems.

Use **Help > Guided First Plot...** to restart the no-hardware route.

## Java and launch problems

Installer builds include Java. If an installed app fails to start, reinstall the
matching Windows x64 MSI or Ubuntu x64 DEB and report the OS and release version.
The instructions below apply to JAR downloads and source builds.

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

## Long, slow move reports a G-code response timeout

Some controller firmware sends `ok` only after a move completes. A long segment
at a low feed rate can therefore be healthy even when no acknowledgement arrives
for several minutes. Open **Settings > Preferences... > Pen & Speeds** and increase **G-code Timeout
(seconds)**. The default is 600 seconds (10 minutes), and the maximum is 3600
seconds. Gantry still reports a closed serial port or GRBL alarm immediately.

## Stop/cancel behavior

Gantry's safety invariant is that stopping, cancelling, or failing should raise
the pen. If a real machine does not raise the pen after stop/cancel, record the
backend mode, pen mode, controller state, and last visible Gantry message, then
open a bug report.

## What to include in a bug report

Start with **Help > Copy Diagnostics** in Gantry and paste the copied plain-text
summary into the issue. It includes the Gantry version, Java/OS information,
backend type, selected serial/configuration details, the last console error, and
the recent Console tail without requiring users to find log files manually.

If you cannot launch Gantry or cannot use the menu, include these details
manually:

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

## Filled areas have gaps or overshoot

Use measured effective stroke width, not only the number printed on the pen.
Preview the round-tip coverage and try a calibration swatch with several overlaps.
Enable **Keep strokes inside boundary** to inset centreline paths; very narrow
features can disappear and are shown as uncovered. Paper, pressure, ink and speed
can change the result. Resize before filling, or regenerate after resizing.

If a preview reports excessive complexity, reduce overlap, simplify the source or
process fewer areas. If Apply is disabled, wait for the current preview or correct
the reported settings. Pen-width mode requires straight lines and rejects
hand-drawn distortion, line simplification/merging and cropping that would change
its predicted coverage. Manual spacing remains available for decorative patterns.

## Text is reflected or a stroke is missing

Current development fixes workspace/SVG/overlap orientation and missing initial
segments. Public RC11 and the unchanged RC12 draft predate these fixes. Confirm
version/commit in diagnostics before comparing their behavior with current captures.
Existing calibrated profiles intentionally keep Legacy mapping. For an explicit
upright opt-in, follow [the profile guide](USER_GUIDE.md#artwork-orientation-and-existing-profiles)
and recheck physical directions/frame before plotting. Deliberate extra axis
corrections, Flip Y and rotation still apply.

An initial DRAW or one after refill/rinse can start in controller space. Current
previews show that segment without changing the document. To report a mismatch,
include a minimal command file, exact profile/placement and workspace/export views.
A preview pass does not establish physical calibration.

## Plot time differs from the estimate

Current estimates include the prepared clipped route, park/rinse/refill motion,
configured dips and pen settling. Feed overrides change motion time while dwells
stay fixed. They exclude acceleration, serial latency, pen mechanics and operator
waiting; elapsed time includes pauses and pen-change confirmations. The progress
remaining-time display extrapolates command counts, which may have unequal costs.
See [time estimation](USER_GUIDE.md#time-estimate). Compare the same prepared job,
profile, speeds and dwell settings; do not treat the estimate as a completion promise.

### Initial DRAW and the work origin

A disconnected preview/export plans an initial DRAW from work origin (0,0).
Include an explicit initial MOVE in command files to establish the intended start.
Before an initial DRAW without MOVE or a known refill, execution checks reported
position. A known non-origin/nonfinite position rejects the job with instructions
to add MOVE or deliberately return pen-up to origin and review again; it does not
silently position the machine. Halt/pen-up recovery still applies. If the backend
cannot report position, a warning records the origin assumption; this does not
establish actual position. Mock results cannot substitute for physical checks.

## Watercolor refill or station-nudge discrepancies in RC13

RC13 can undercount refill distances for a MOVE followed by a single-endpoint
DRAW, reorder buffered ink at exact refill boundaries, or begin a DRAW-only
layer from its initial pot. Its automatic color mapping can also select a
colored rinse/water cup, and a station-wizard nudge can move relative to the
head's previous position instead of the saved station coordinate. Development
fixes and reproduction evidence are in the
[watercolor/station/homing audit](release-results/2026-11-21-watercolor-homing.md).
Those fixes are absent from the immutable RC13 artifacts; do not treat an old
artifact as evidence for accepting them. Inspect exported paths and complete a
new candidate's controlled station dry/wet checks before physical watercolor use.

The same audit fixes homing waiting for Idle after an explicit controller alarm.
Record the alarm and diagnostics, use Stop and the controlled recovery procedure,
and inspect the hardware cause; do not repeatedly retry Home past an alarm.


## Waiting for motion completion (development after RC13)

A layer may reach 100% submitted progress while the controller is still executing
queued travel. Gantry waits for an acknowledged `G4 P0` planner barrier and a fresh
Idle report before the next pen prompt or final success. Stop remains available;
Pause cannot add commands during this handoff wait. The configured response timeout
bounds the wait, including a controller Hold.

A timeout, rejected barrier, alarm, unexpected startup/reset or disconnect is a
failure requiring review, even if another status report says Idle. Do not infer a
missing acknowledgement from position or status. Preserve Copy Diagnostics and
check the actual firmware's command support, connection and controller state.

To reproduce software flow without hardware, disconnect, open Settings, enable
**Simulation (no physical hardware)** and choose **Serial GRBL simulator**. The
workspace and diagnostics identify it; its invalid/unused port name is never
opened. Its faster simulated execution and synthetic Home do not establish real
motion, lift, switches or pot clearance. See the [backend contract](BACKEND_CONTRACT.md)
and [verification](release-results/2026-11-21-completion-serial-simulator.md).
RC13 downloads do not contain these later source changes.

## Firmware review or XY movement is blocked

Development after RC13 requires a fresh **Machine > Review firmware limits...**
for GUI GRBL connections, including the serial simulator. Check acknowledged
`$130/$131/$110/$111`, fresh WCO and an explicit machine-coordinate range for each
axis; Z pen mode additionally needs `$112`. Unsupported firmware/settings are
reported rather than guessed. Compare configured feeds to the smaller XY limit;
change your profile deliberately, never enlarge it merely to dismiss a warning.

Firmware reference review also requires an explicit `$13=0` (millimetre status
and WCO reporting). Missing/inch/invalid report units are unsupported and block
approval; Gantry does not change them automatically. The actual reported work
position must fit the reviewed envelope. GRBL distinguishes report units from
command units: [upstream configuration](https://github.com/gnea/grbl/wiki/Grbl-v1.1-Configuration#13---report-inches-boolean).

Stop, reset, alarm, disconnect, origin and relevant profile/feed changes invalidate
review. Establish the actual reference and paper origin again. Home, machine zero,
work zero and paper registration are different. After suspected lost steps choose
**Machine > Mark reference uncertain**. See the [operating contract](USER_GUIDE.md#firmware-limits-and-machine-reference-development-after-rc13).

## Partial restart is unavailable or does not match

A saved restart uses the full prepared geometry, inclusion/order, passes, pens and
machine/profile identity. Restore the original project/profile or clear current
artwork to review the stored job; no filename/count shortcut bypasses this match.
Watercolor loading/refill/rinsing is unsupported for partial restart/reverse.

A corrupt checkpoint never resumes. Use **File > Discard restart proposal...**
only when you intend to abandon that saved proposal. For write failures check
profile permissions/free space; progress persistence failure stops tracked
execution. Reopening never moves hardware. Inspect uncertain strokes on paper,
review selection and all confirmations, then establish reference/checks and Start.
See [reviewed restart](USER_GUIDE.md#reviewed-partial-restart-and-reverse-development-after-rc13).

## Resizing or rotating keeps being rejected

The selected nine-point reference must stay fixed. If the transformed bounding
box would leave the bed, the edit is restored rather than moving that point.
Reposition first or reduce the size. X/Y describe the selected reference, not
necessarily the nearest corner; changing Reference alone never moves artwork.
