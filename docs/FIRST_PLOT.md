# First plot with Gantry

This guide is the shortest path from a fresh Gantry checkout or release artifact
to a safe first practice plot. Use it before connecting a real machine.

## What you need

- Java 17 or newer.
- For building from source: Maven 3.8 or newer.
- Optional for a real plot: a GRBL-based pen plotter connected over USB serial.

You can complete the guided practice with **no hardware**. The mock backend uses
the same application workflow but never talks to a physical controller.

## 1. Start Gantry

From a source checkout:

```bash
./scripts/start.sh
```

On Windows:

```cmd
scripts\start.cmd
```

If you downloaded a release JAR instead, run the GUI JAR with Java 17+:

```bash
java -jar Gantry-<version>.jar
```

## 2. Choose guided practice

On a fresh profile, Gantry opens **Your first plot**.

Choose **Start guided practice**.

This route:

1. opens the real setup workflow;
2. selects the mock backend;
3. loads a supplied practice drawing;
4. shows the drawing on the virtual bed;
5. leads into the normal connection and safety check;
6. completes a simulated plot without moving hardware.

If the welcome was dismissed, reopen it with **Help > Guided First Plot...**.

## 3. Confirm the safe state

Before connecting anything, Gantry should say **Safe — nothing will move**. You
can load, process, position, and save artwork while disconnected. That preview is
not a hardware connection.

Do not connect a real plotter until the drawing size and bed position look
reasonable.

## 4. Complete the mock plot

Follow the primary action in the right-hand workflow area:

1. **Add artwork** if no practice artwork is loaded.
2. **Connect plotter** using the mock backend.
3. **Check before plotting**.
4. **Start plotting**.
5. Confirm pen or colour prompts if they appear.

A successful mock run proves the basic workflow, not the physical calibration of
a real machine.

## 5. Prepare for a real plot

Before using hardware:

1. Measure the real travel area of the plotter, not just the paper size.
2. Set the machine width, height, origin corner, and pen-lift mode in Settings or
   the Setup Wizard.
3. Connect the plotter and verify manual jog directions with the pen raised.
4. Use **Find starting corner (Home)** only if the controller and limit switches
   are configured for homing.
5. Run **Check before plotting** and frame the job before putting the pen down.
6. Keep a hand near power or emergency stop for the first real test.

Start with a small, simple SVG near the centre of the bed. Avoid full-page or
watercolor jobs until jog, frame, stop, and pen lift have been verified.

## 6. If something is unclear

Use these docs next:

- [`USER_GUIDE.md`](USER_GUIDE.md) — full operating guide.
- [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) — serial ports, GRBL states, and
  first-run failures.
- [`KNOWN_GOOD_SETUPS.md`](KNOWN_GOOD_SETUPS.md) — verified hardware/configuration
  reports.

If reporting an issue, include the operating system, Java version, Gantry
version or commit, plotter/controller, connection type, and what you clicked just
before the problem.
