package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.plotter.*;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.*;

/** Guided axis, scale, limit-switch, and pen-lift calibration. */
final class CalibrationWorkflow {
    private final Window owner;
    private final GantryConfig config;
    private final BooleanSupplier connected;
    private final Runnable connect;
    private final Consumer<Consumer<PlotterBackend>> backendAction;
    private final Consumer<String> logger;
    private final Runnable apply;
    private final Runnable save;

    CalibrationWorkflow(Window owner, GantryConfig config, BooleanSupplier connected, Runnable connect,
            Consumer<Consumer<PlotterBackend>> backendAction, Consumer<String> logger,
            Runnable apply, Runnable save) {
        this.owner=owner; this.config=config; this.connected=connected; this.connect=connect;
        this.backendAction=backendAction; this.logger=logger; this.apply=apply; this.save=save;
    }

    void show() {
        // No "connect first" dead-end: the wizard's own Connect step establishes the link (using the
        // saved connection settings / mock) before any step that drives the machine.
        List<WizardStep> steps = new ArrayList<>();
        steps.add(new PanelStep("Intro", wrapStep(
                "<html><h3>Axis calibration</h3>First this connects to the plotter, then:<br>"
                        + "<b>1. Direction</b> — jog each motor axis and click the way the pen actually "
                        + "moved; the wizard derives the right swap/invert settings (it catches "
                        + "swapped axes, not just reversed ones).<br>"
                        + "<b>2. Scale</b> — command a known distance on each axis, measure what the "
                        + "machine actually moved with a ruler, and the wizard computes and writes the "
                        + "corrected steps/mm (<code>$100</code>/<code>$101</code>).<br>"
                        + "<b>3. Limit switches</b> — record whether the machine has them, enable homing, "
                        + "and watch each switch register as you press it.<br>"
                        + "<b>4. Pen lift</b> — pick the lift type (servo / Z / M3-M5) and test it.<br><br>"
                        + "<b>Clear the bed and make sure the pen is up</b> — this moves the head.</html>")));
        ConnectionWizardStep connectStep = new ConnectionWizardStep(connected, connect);
        steps.add(connectStep);
        steps.add(new CalibDirectionStep());
        steps.add(new CalibScaleStep('X', GrblSettings.X_STEPS_PER_MM));
        steps.add(new CalibScaleStep('Y', GrblSettings.Y_STEPS_PER_MM));
        steps.add(new CalibLimitsStep());
        steps.add(new CalibZStep());
        steps.add(new PanelStep("Done", wrapStep(
                "<html><h3>Calibration complete</h3>Any direction fix, corrected steps/mm, limit "
                        + "settings and pen-lift values have been applied. Re-run this any time from "
                        + "<i>Machine &gt; Calibrate Axes…</i>.</html>")));

        WizardDialog wizard = new WizardDialog(owner, "Calibrate Axes", steps);
        connectStep.attachOwner(wizard);
        wizard.setVisible(true);
        if (wizard.finishedSuccessfully()) {
            save.run();
            applyConfigToVis();
            log("Axis calibration finished.");
        }
    }

    /**
     * Direction sanity-check step: jog +X / +Y a fixed amount and let the operator say whether the
     * machine moved the expected way; "moved the wrong way" toggles the matching invert flag on
     * {@link #config} (applied for real on Finish). Uses the existing {@link #jog} action so the
     * test reflects exactly how plotting will move the head.
     */
    /** How far each raw-motor calibration jog moves (mm) — big enough to read the direction clearly. */
    private static final double CALIB_JOG_MM = 20.0;

    /**
     * Direction calibration by observation (Phase 16b): centre the pen, jog each <em>raw motor</em>
     * axis, and click the arrow for the way the pen actually moved. {@link AxisDirectionSolver}
     * derives swap + invert in one shot — catching an axis <em>swap</em> the old two-checkbox step
     * couldn't — and applies it to the config (and the live preview) immediately.
     */
    private final class CalibDirectionStep implements WizardStep {
        private final JPanel panel;
        private final JLabel result = new JLabel(" ");
        private AxisDirectionSolver.Dir observedX;
        private AxisDirectionSolver.Dir observedY;

        CalibDirectionStep() {
            panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            panel.add(new JLabel("<html><b>1.</b> Jog the head to roughly the <b>middle of the bed</b> "
                    + "(use the main Jog pad) so it has room to move in every direction.</html>"));
            panel.add(Box.createVerticalStrut(10));
            panel.add(new JLabel("<html><b>2.</b> Move each motor axis and click the arrow for the way "
                    + "the pen <b>actually</b> moved. Right/Up are the on-screen +X/+Y senses.</html>"));
            panel.add(Box.createVerticalStrut(10));
            panel.add(axisRow("Move motor X +", CALIB_JOG_MM, 0, d -> { observedX = d; recompute(); }));
            panel.add(Box.createVerticalStrut(8));
            panel.add(axisRow("Move motor Y +", 0, CALIB_JOG_MM, d -> { observedY = d; recompute(); }));
            panel.add(Box.createVerticalStrut(12));
            result.setForeground(new Color(0, 110, 60));
            panel.add(result);
        }

        private JPanel axisRow(String jogLabel, double dxMm, double dyMm,
                java.util.function.Consumer<AxisDirectionSolver.Dir> onObserved) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton jogBtn = new JButton(jogLabel);
            jogBtn.addActionListener(e -> runOnBackend(b -> b.move(dxMm, dyMm)));
            row.add(jogBtn);
            row.add(new JLabel("moved:"));
            ButtonGroup group = new ButtonGroup();
            for (AxisDirectionSolver.Dir d : new AxisDirectionSolver.Dir[]{
                    AxisDirectionSolver.Dir.RIGHT, AxisDirectionSolver.Dir.LEFT,
                    AxisDirectionSolver.Dir.UP, AxisDirectionSolver.Dir.DOWN}) {
                JToggleButton b = new JToggleButton(arrow(d));
                b.setToolTipText(d.toString());
                b.addActionListener(e -> onObserved.accept(d));
                group.add(b);
                row.add(b);
            }
            return row;
        }

        private String arrow(AxisDirectionSolver.Dir d) {
            return switch (d) {
                case RIGHT -> "→"; case LEFT -> "←"; case UP -> "↑"; case DOWN -> "↓";
            };
        }

        /** Once both axes are observed, solve and apply (or report an inconsistent pair). */
        private void recompute() {
            if (observedX == null || observedY == null) {
                return;
            }
            var solved = AxisDirectionSolver.solveEffective(observedX, observedY);
            if (solved.isEmpty()) {
                result.setForeground(new Color(180, 60, 60));
                result.setText("Those two directions can't both be right (the axes are perpendicular) — re-check.");
                return;
            }
            boolean originRight = config.machineOrigin.toLowerCase().contains("right");
            boolean originBottom = config.machineOrigin.toLowerCase().contains("bottom");
            AxisDirectionSolver.AxisConfig stored =
                    AxisDirectionSolver.toStoredExtra(solved.get(), originRight, originBottom);
            config.swapXY = stored.swapXY();
            config.invertX = stored.invertX();
            config.invertY = stored.invertY();
            applyConfigToVis(); // live preview now matches the machine
            result.setForeground(new Color(0, 110, 60));
            result.setText(String.format("Applied: swap=%s, invert X=%s, invert Y=%s.",
                    stored.swapXY() ? "yes" : "no", stored.invertX() ? "yes" : "no",
                    stored.invertY() ? "yes" : "no"));
        }

        @Override public String title() { return "Direction check"; }
        @Override public JComponent panel() { return panel; }
    }

    /**
     * Scale-calibration step for one axis: reads the current GRBL steps/mm, commands a known move,
     * takes the measured distance, previews the corrected steps/mm, and writes it back. Optional —
     * an operator who only needed the direction check can Skip it.
     */
    private final class CalibScaleStep implements WizardStep {
        private final char axis;
        private final int setting;
        private final JPanel panel;
        private final JLabel currentLabel = new JLabel("Current steps/mm: (read on entry)");
        private final JSpinner commandedSpinner = new JSpinner(new SpinnerNumberModel(100.0, 1.0, 1000.0, 10.0));
        private final JSpinner measuredSpinner = new JSpinner(new SpinnerNumberModel(100.0, 0.1, 2000.0, 1.0));
        private final JLabel previewLabel = new JLabel("Corrected: —");
        private Double currentStepsPerMm;

        CalibScaleStep(char axis, int setting) {
            this.axis = axis;
            this.setting = setting;
            panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            panel.add(new JLabel("<html><b>" + axis + " axis</b> scale calibration (GRBL $" + setting + ").</html>"));
            panel.add(Box.createVerticalStrut(6));
            panel.add(left(currentLabel));

            JButton moveBtn = new JButton("Move " + axis + " by commanded distance");
            moveBtn.addActionListener(e -> {
                double d = ((Number) commandedSpinner.getValue()).doubleValue();
                runOnBackend(b -> {
                    b.penup();
                    if (axis == 'X') { b.move(d, 0); } else { b.move(0, d); }
                    log(String.format("Calibration: commanded %c move of %.1f mm.", axis, d));
                });
            });
            panel.add(Box.createVerticalStrut(8));
            panel.add(rowOf(new JLabel("Commanded (mm):"), commandedSpinner, moveBtn));
            panel.add(Box.createVerticalStrut(4));
            JButton measureBtn = new JButton("Compute corrected steps/mm");
            measureBtn.addActionListener(e -> updatePreview());
            measuredSpinner.addChangeListener(e -> updatePreview());
            panel.add(rowOf(new JLabel("Measured (mm):"), measuredSpinner, measureBtn));
            panel.add(Box.createVerticalStrut(8));
            panel.add(left(previewLabel));

            JButton writeBtn = new JButton("Write $" + setting + " to the machine");
            writeBtn.addActionListener(e -> writeCorrected());
            panel.add(Box.createVerticalStrut(6));
            panel.add(left(writeBtn));
        }

        private void updatePreview() {
            if (currentStepsPerMm == null) {
                previewLabel.setText("Corrected: (read current value first)");
                return;
            }
            double commanded = ((Number) commandedSpinner.getValue()).doubleValue();
            double measured = ((Number) measuredSpinner.getValue()).doubleValue();
            Double corrected = GrblSettings.correctedStepsPerMm(currentStepsPerMm, commanded, measured);
            previewLabel.setText(corrected == null ? "Corrected: (enter a measured distance > 0)"
                    : String.format("Corrected: %.3f steps/mm (was %.3f)", corrected, currentStepsPerMm));
        }

        private void writeCorrected() {
            if (currentStepsPerMm == null) {
                log("Calibration: no current steps/mm read yet; cannot write.");
                return;
            }
            double commanded = ((Number) commandedSpinner.getValue()).doubleValue();
            double measured = ((Number) measuredSpinner.getValue()).doubleValue();
            Double corrected = GrblSettings.correctedStepsPerMm(currentStepsPerMm, commanded, measured);
            if (corrected == null) {
                log("Calibration: measured distance must be > 0 to compute a correction.");
                return;
            }
            String cmd = GrblSettings.writeCommand(setting, corrected);
            runOnBackend(b -> {
                b.sendRaw(cmd);
                log(String.format("Calibration: wrote %s ($%d %c steps/mm).", cmd, setting, axis));
                currentStepsPerMm = corrected;
                SwingUtilities.invokeLater(() -> {
                    currentLabel.setText(String.format("Current steps/mm: %.3f", corrected));
                    updatePreview();
                });
            });
        }

        @Override public String title() { return axis + "-axis scale"; }
        @Override public JComponent panel() { return panel; }
        @Override public boolean isOptional() { return true; }

        @Override public void onEnter() {
            currentLabel.setText("Current steps/mm: reading…");
            runOnBackend(b -> {
                Double v = GrblSettings.findSetting(b.sendRaw("$$"), setting);
                SwingUtilities.invokeLater(() -> {
                    currentStepsPerMm = v;
                    currentLabel.setText(v == null ? "Current steps/mm: (couldn't read $" + setting + ")"
                            : String.format("Current steps/mm: %.3f", v));
                    updatePreview();
                });
            });
        }
    }

    /**
     * Limit/stop-switch step (Phase 16b). Records whether the machine has switches, reads the GRBL
     * homing/limit settings ($20–$23), lets the operator enable the homing cycle, and — the real
     * "test" — polls the GRBL status report so pressing each limit switch by hand lights it up live.
     * Read-only except the explicit homing-enable toggle, so it can't misconfigure the machine.
     */
    private final class CalibLimitsStep implements WizardStep {
        private final JPanel panel;
        private final JCheckBox hasSwitches = new JCheckBox("My machine has limit / homing switches");
        private final JCheckBox homingEnabled = new JCheckBox("Homing cycle enabled (GRBL $22)");
        private final JLabel settingsLabel = new JLabel("GRBL limits: (read on entry)");
        private final JLabel pinsLabel = new JLabel("Triggered pins: —");
        private final javax.swing.Timer pollTimer;
        private boolean syncing;

        CalibLimitsStep() {
            panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            panel.add(new JLabel("<html><b>Limit / stop switches.</b> Optional, but they enable homing "
                    + "(a repeatable origin) and hard limits (over-travel protection).</html>"));
            panel.add(Box.createVerticalStrut(8));

            hasSwitches.setSelected(config.hasLimitSwitches);
            hasSwitches.addActionListener(e -> config.hasLimitSwitches = hasSwitches.isSelected());
            panel.add(left(hasSwitches));
            panel.add(Box.createVerticalStrut(6));
            panel.add(left(settingsLabel));

            homingEnabled.addActionListener(e -> {
                if (syncing) {
                    return;
                }
                String cmd = "$22=" + (homingEnabled.isSelected() ? 1 : 0);
                runOnBackend(b -> {
                    b.sendRaw(cmd);
                    log("Limit setup: wrote " + cmd + (homingEnabled.isSelected()
                            ? " (homing enabled)" : " (homing disabled)"));
                });
            });
            panel.add(Box.createVerticalStrut(6));
            panel.add(left(homingEnabled));

            panel.add(Box.createVerticalStrut(12));
            panel.add(new JLabel("<html><b>Test:</b> press each limit switch by hand — it should appear "
                    + "below within a second. (Nothing moves.)</html>"));
            panel.add(Box.createVerticalStrut(4));
            pinsLabel.setFont(pinsLabel.getFont().deriveFont(Font.BOLD));
            panel.add(left(pinsLabel));

            pollTimer = new javax.swing.Timer(500, e -> runOnBackend(b -> {
                String pins = GrblSettings.parsePins(b.sendRaw("?"));
                SwingUtilities.invokeLater(() -> pinsLabel.setText(pins == null
                        ? "Triggered pins: (no status from machine)"
                        : "Triggered pins: " + (pins.isEmpty() ? "none" : pins)));
            }));
            pollTimer.setRepeats(true);
        }

        @Override public String title() { return "Limit switches"; }
        @Override public JComponent panel() { return panel; }
        @Override public boolean isOptional() { return true; }

        @Override public void onEnter() {
            settingsLabel.setText("GRBL limits: reading…");
            runOnBackend(b -> {
                List<String> dump = b.sendRaw("$$");
                Double soft = GrblSettings.findSetting(dump, GrblSettings.SOFT_LIMITS);
                Double hard = GrblSettings.findSetting(dump, GrblSettings.HARD_LIMITS);
                Double homing = GrblSettings.findSetting(dump, GrblSettings.HOMING_ENABLE);
                SwingUtilities.invokeLater(() -> {
                    settingsLabel.setText(String.format("GRBL limits: soft=$20=%s, hard=$21=%s, homing=$22=%s",
                            fmtFlag(soft), fmtFlag(hard), fmtFlag(homing)));
                    syncing = true;
                    homingEnabled.setSelected(homing != null && homing != 0);
                    syncing = false;
                });
            });
            pollTimer.start();
        }

        @Override public void onLeave() {
            pollTimer.stop();
        }

        private String fmtFlag(Double v) {
            return v == null ? "?" : String.valueOf(v.intValue());
        }
    }

    /**
     * Pen-lift (Z) step (Phase 16b): pick the lift type — servo, Z-axis, or M3/M5 spindle — set its
     * up/down values, and Test them live. Because the connected backend shares {@code config.gcode}
     * and reads it per pen command, edits here take effect on the very next Test with no reconnect.
     */
    private final class CalibZStep implements WizardStep {
        private final JPanel panel;
        private final JComboBox<String> modeCombo = new JComboBox<>(new String[]{"servo", "zaxis", "m3m5"});
        private final JPanel cards = new JPanel(new CardLayout());

        CalibZStep() {
            panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            panel.add(new JLabel("<html><b>Pen lift.</b> How does this machine raise and lower the pen?</html>"));
            panel.add(Box.createVerticalStrut(8));

            modeCombo.setSelectedItem(config.gcode.penMode);
            modeCombo.addActionListener(e -> {
                config.gcode.penMode = (String) modeCombo.getSelectedItem();
                ((CardLayout) cards.getLayout()).show(cards, config.gcode.penMode);
            });
            panel.add(rowOf(new JLabel("Lift type:"), modeCombo));
            panel.add(Box.createVerticalStrut(8));

            cards.add(servoCard(), "servo");
            cards.add(zCard(), "zaxis");
            cards.add(m3Card(), "m3m5");
            ((CardLayout) cards.getLayout()).show(cards, config.gcode.penMode);
            panel.add(left(cards));

            JButton testBtn = new JButton("Test pen (up → down → up)");
            testBtn.addActionListener(e -> runOnBackend(b -> {
                log("Pen test: lifting, lowering, lifting…");
                b.penup();
                sleepQuietly(700);
                b.pendown();
                sleepQuietly(700);
                b.penup();
            }));
            panel.add(Box.createVerticalStrut(12));
            panel.add(left(testBtn));
            panel.add(Box.createVerticalStrut(4));
            panel.add(new JLabel("<html><i>Adjust the values and re-test until the pen clears the paper "
                    + "when up and presses firmly when down.</i></html>"));
        }

        private JPanel servoCard() {
            JSpinner up = intSpinner(config.gcode.penServoUp, 0, 180, v -> config.gcode.penServoUp = v);
            JSpinner down = intSpinner(config.gcode.penServoDown, 0, 180, v -> config.gcode.penServoDown = v);
            JPanel p = new JPanel(new GridLayout(0, 2, 6, 6));
            p.add(new JLabel("Servo up angle (°)")); p.add(up);
            p.add(new JLabel("Servo down angle (°)")); p.add(down);
            return p;
        }

        private JPanel zCard() {
            JSpinner up = dblSpinner(config.gcode.zUp, -50, 50, v -> config.gcode.zUp = v);
            JSpinner down = dblSpinner(config.gcode.zDown, -50, 50, v -> config.gcode.zDown = v);
            JPanel p = new JPanel(new GridLayout(0, 2, 6, 6));
            p.add(new JLabel("Z up (mm)")); p.add(up);
            p.add(new JLabel("Z down (mm)")); p.add(down);
            return p;
        }

        private JPanel m3Card() {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
            p.add(new JLabel("M3 (down) / M5 (up) spindle control — no extra parameters."));
            return p;
        }

        private JSpinner intSpinner(int value, int min, int max, java.util.function.IntConsumer set) {
            JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
            s.addChangeListener(e -> set.accept(((Number) s.getValue()).intValue()));
            return s;
        }

        private JSpinner dblSpinner(double value, double min, double max, java.util.function.DoubleConsumer set) {
            JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, 0.5));
            s.addChangeListener(e -> set.accept(((Number) s.getValue()).doubleValue()));
            return s;
        }

        @Override public String title() { return "Pen lift (Z)"; }
        @Override public JComponent panel() { return panel; }
        @Override public boolean isOptional() { return true; }
    }


    private void runOnBackend(Consumer<PlotterBackend> action) { backendAction.accept(action); }
    private void log(String line) { logger.accept(line); }
    private void applyConfigToVis() { apply.run(); }
    private static JComponent left(JComponent c) { JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0)); p.add(c); return p; }
    private static JComponent rowOf(JComponent... cs) { JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT)); for(JComponent c:cs)p.add(c); return p; }
    private static void sleepQuietly(long millis) { try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
    private static JComponent wrapStep(JComponent content) { JPanel p=new JPanel(new BorderLayout()); p.setBorder(BorderFactory.createEmptyBorder(8,8,8,8)); p.add(content,BorderLayout.NORTH); JScrollPane s=new JScrollPane(p); s.setBorder(BorderFactory.createEmptyBorder()); return s; }
    private static JComponent wrapStep(String html) { JLabel l=new JLabel(html.replaceFirst("(?i)<html>","<html><body style='width:430px'>")); l.setVerticalAlignment(SwingConstants.TOP); return wrapStep(l); }
}
