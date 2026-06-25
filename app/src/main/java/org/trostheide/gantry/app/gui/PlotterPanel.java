package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.CommandFile;
import org.trostheide.gantry.app.plot.ConfigStore;
import org.trostheide.gantry.app.plot.GantryConfig;
import org.trostheide.gantry.app.plot.PlotService;
import org.trostheide.gantry.app.plot.PlotSettings;
import org.trostheide.gantry.app.plot.StationConfig;
import org.trostheide.gantry.app.plot.TimeEstimator;
import org.trostheide.gantry.model.CoordinateTransform;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.pipeline.io.ProcessorOutputIO;
import org.trostheide.gantry.pipeline.optimize.MultipassStage;
import org.trostheide.gantry.pipeline.optimize.OptimizeStage;
import org.trostheide.gantry.pipeline.svgimport.SvgImportStage;
import org.trostheide.gantry.plotter.GcodeBackend;
import org.trostheide.gantry.plotter.GcodeFileBackend;
import org.trostheide.gantry.plotter.GcodeFileReplay;
import org.trostheide.gantry.plotter.GrblSettings;
import org.trostheide.gantry.plotter.MockPlotterBackend;
import org.trostheide.gantry.plotter.PlotterBackend;
import org.trostheide.gantry.watercolor.PaintStation;
import org.trostheide.gantry.watercolor.StationMapper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * Self-contained plotter window: live visualization, jog/pen/speed controls, raw G-code console
 * and Start/Confirm/Stop plot controls, wired directly to {@link PlotService} and a
 * {@link PlotterBackend} (no subprocess).
 */
public class PlotterPanel extends JPanel {

    private static final Color ACTION_GREEN = new Color(46, 125, 50);
    private static final Color ACTION_RED = new Color(198, 40, 40);

    private final File configFile = new File("config.json");
    // Captured before the config loads: a missing config.json means this is a fresh install, which
    // triggers a one-time offer to run the guided Setup Wizard.
    private final boolean firstRun = !configFile.exists();
    private GantryConfig config = ConfigStore.load(configFile);

    private final VisualizationPanel visPanel = new VisualizationPanel();
    private final JTextArea console = new JTextArea();

    private final JButton connectBtn = new JButton("Connect");
    private JMenuItem connectMenuItem;
    private final JButton startBtn = new JButton("Start Plot");
    private final JButton preflightBtn = new JButton("Pre-flight...");
    private final JButton confirmBtn = new JButton("Confirm Layer");
    private final JButton pauseBtn = new JButton("Pause");
    private final JButton stopBtn = new JButton("Stop");
    private final JLabel statusLabel = new JLabel("Disconnected");
    private final JLabel guidanceLabel = new JLabel();
    private JPanel guidancePanel;
    private boolean guidanceDismissed;
    private final JLabel speedLabel = new JLabel("100%");
    private final JSpinner jogStepSpinner = new JSpinner(new SpinnerNumberModel(10.0, 0.1, 200.0, 1.0));
    private final JTextField rawCommandField = new JTextField(16);
    private final JSpinner simplifyToleranceSpinner = new JSpinner(new SpinnerNumberModel(0.2, 0.0, 10.0, 0.1));
    private final JCheckBox reorderStrokesCheckBox = new JCheckBox("Reorder", true);
    private final JSpinner mergeToleranceSpinner = new JSpinner(new SpinnerNumberModel(0.2, 0.0, 10.0, 0.1));
    private final JSpinner multipassSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
    private final JSpinner posXSpinner = new JSpinner(new SpinnerNumberModel(0.0, -2000.0, 2000.0, 1.0));
    private final JSpinner posYSpinner = new JSpinner(new SpinnerNumberModel(0.0, -2000.0, 2000.0, 1.0));
    private final JLabel timeLabel = new JLabel("Est: --:--");
    /** One checkbox per layer (built on load); a ticked box means the layer is shown and plotted. */
    private final JPanel layerListPanel = new JPanel();
    private final List<JCheckBox> layerChecks = new ArrayList<>();
    private final JCheckBox colorByLayerCheck = new JCheckBox("Colour layers", true);
    /** Guards the layer checkboxes' listeners while we rebuild them programmatically. */
    private boolean repopulatingLayers;

    private PlotterBackend backend;
    private ProcessorOutput currentOutput;
    private File lastImportedSvgFile;
    private org.trostheide.gantry.pipeline.svgimport.SvgImportOptions lastImportOptions;
    /** Single-level undo snapshot of {@link #currentOutput} before the last destructive transform. */
    private ProcessorOutput undoSnapshot;
    private JMenuItem undoMenuItem;
    private volatile PlotService activeService;
    private final Semaphore confirmGate = new Semaphore(0);
    private boolean paused;
    private TimeEstimator.PlotEstimate currentEstimate;
    private volatile int speedPercent = 100;
    private javax.swing.Timer plotClockTimer;
    private long plotStartMillis;
    private long layerStartMillis;
    private String currentLayerId;
    private volatile boolean plotting;
    private java.awt.KeyEventDispatcher jogKeyDispatcher;

    /** Controls that should be disabled while a plot is running (jog, pen, speed, edit actions). */
    private final List<JComponent> plotDisabledControls = new ArrayList<>();
    private final List<JComponent> connectionRequiredControls = new ArrayList<>();

    private JSplitPane controlSplit;

    public PlotterPanel() {
        setLayout(new BorderLayout(4, 4));
        setBorder(new EmptyBorder(6, 6, 6, 6));

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(toolbar());
        north.add(guidanceBanner());
        add(north, BorderLayout.NORTH);

        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane consoleScroll = new JScrollPane(console);
        consoleScroll.setBorder(section("Console"));
        consoleScroll.setPreferredSize(new Dimension(260, 160));

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(capHeight(jogSection()));
        right.add(Box.createVerticalStrut(3));
        right.add(capHeight(overlaySection()));
        right.add(Box.createVerticalStrut(3));
        right.add(capHeight(plotSection()));
        right.add(Box.createVerticalStrut(3));
        right.add(capHeight(rawCommandSection()));
        right.add(Box.createVerticalStrut(3));
        // Console absorbs any leftover vertical space; the fixed sections stay at their natural height.
        right.add(consoleScroll);

        int rightWidth = 300;
        right.setPreferredSize(new Dimension(rightWidth, right.getPreferredSize().height));
        right.setMaximumSize(new Dimension(rightWidth, Integer.MAX_VALUE));

        controlSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, visPanel, right);
        // Give all extra space to the canvas; keep the control column at its compact width.
        controlSplit.setResizeWeight(1.0);
        controlSplit.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = controlSplit.getWidth();
                if (w > 0) {
                    controlSplit.setDividerLocation(w - rightWidth - controlSplit.getDividerSize());
                }
            }
        });
        JSplitPane split = controlSplit;
        add(split, BorderLayout.CENTER);

        startBtn.setBackground(ACTION_GREEN);
        startBtn.setForeground(Color.WHITE);
        startBtn.setOpaque(true);
        stopBtn.setBackground(ACTION_RED);
        stopBtn.setForeground(Color.WHITE);
        stopBtn.setOpaque(true);

        applyConfigToVis();
        setPlottingState(false);
        installJogKeyBindings();
        teeConsoleOutput();
        refreshGuidance();
        maybeOfferFirstRunSetup();
    }

    /** On a fresh install (no config.json yet), offer to run the guided Setup Wizard once. */
    private void maybeOfferFirstRunSetup() {
        if (!firstRun) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    "It looks like this is the first run (no saved settings yet).\n"
                            + "Would you like to run the guided Machine Setup wizard now?",
                    "Welcome to Gantry", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                onSetupWizard();
            }
        });
    }

    /**
     * Mirrors {@code System.out}/{@code System.err} into the GUI console. The plotter backend
     * reports timeouts, GRBL errors and lost-connection diagnostics via {@code System.out}; before
     * this, those only reached the terminal and were invisible to anyone running the app normally.
     */
    private void teeConsoleOutput() {
        System.setOut(teeStream(System.out));
        System.setErr(teeStream(System.err));
    }

    private java.io.PrintStream teeStream(java.io.PrintStream original) {
        return new java.io.PrintStream(new java.io.OutputStream() {
            private final StringBuilder line = new StringBuilder();

            @Override
            public void write(int b) {
                original.write(b);
                if (b == '\n') {
                    log(line.toString());
                    line.setLength(0);
                } else if (b != '\r') {
                    line.append((char) b);
                }
            }
        }, true);
    }

    /** Background/text/accent for the step banner — a muted slate-blue that fits the dark theme. */
    private static final Color GUIDANCE_BG = new Color(45, 58, 72);
    private static final Color GUIDANCE_ACCENT = new Color(80, 180, 255);
    private static final Color GUIDANCE_TEXT = new Color(220, 228, 235);

    /** Builds the persistent, dismissible step banner that tells the user what to do next. */
    private JPanel guidanceBanner() {
        guidancePanel = new JPanel(new BorderLayout());
        guidancePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, GUIDANCE_ACCENT),
                new EmptyBorder(3, 6, 3, 4)));
        guidancePanel.setBackground(GUIDANCE_BG);
        guidancePanel.setOpaque(true);
        guidanceLabel.setFont(guidanceLabel.getFont().deriveFont(Font.BOLD));
        guidanceLabel.setForeground(GUIDANCE_TEXT);

        JButton closeBtn = new JButton("×");
        closeBtn.setToolTipText("Hide this guidance banner");
        closeBtn.setMargin(new Insets(0, 4, 0, 4));
        closeBtn.setFocusPainted(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setForeground(GUIDANCE_TEXT);
        closeBtn.addActionListener(e -> {
            guidanceDismissed = true;
            guidancePanel.setVisible(false);
        });

        guidancePanel.add(guidanceLabel, BorderLayout.CENTER);
        guidancePanel.add(closeBtn, BorderLayout.EAST);
        return guidancePanel;
    }

    /**
     * Updates the step banner to reflect what the user should do next: connect, then
     * load/import a drawing, then position it, then start, then confirm each layer. Stopping a
     * plot un-dismisses the banner and resets it back to step guidance, in case the user wants
     * the prompts again after deviating from the suggested flow.
     */
    private void refreshGuidance() {
        String text;
        if (backend == null) {
            text = "Step 1: Click Connect to talk to the plotter.";
        } else if (currentOutput == null) {
            text = "Step 2: Open Commands (JSON) or Import SVG to load a drawing.";
        } else if (plotting) {
            text = paused
                    ? "Plot paused. Click Resume to continue, or Stop to cancel."
                    : "Plotting... Confirm Layer when prompted, or Pause/Stop as needed.";
        } else {
            text = "Step 3: Check position/optimize as needed, then click Start.";
        }
        guidanceLabel.setText(text);
        if (guidancePanel != null && !guidanceDismissed) {
            guidancePanel.setVisible(true);
        }
    }

    /** Un-dismisses and resets the guidance banner, e.g. after the user hits Stop. */
    private void resetGuidance() {
        guidanceDismissed = false;
        refreshGuidance();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (jogKeyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(jogKeyDispatcher);
        }
    }

    @Override
    public void removeNotify() {
        if (jogKeyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(jogKeyDispatcher);
        }
        super.removeNotify();
    }

    /**
     * Lets the arrow keys (and numpad 8/2/4/6 arrows) jog X/Y from anywhere in the window,
     * Shift+Up/Down raise/lower the pen, except while a text field or spinner editor has focus
     * (so normal text-cursor navigation keeps working there).
     */
    private void installJogKeyBindings() {
        jogKeyDispatcher = e -> {
            if (e.getID() != java.awt.event.KeyEvent.KEY_PRESSED) {
                return false;
            }
            if (plotting) {
                return false;
            }
            Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (focusOwner instanceof javax.swing.text.JTextComponent) {
                return false;
            }
            boolean shift = e.isShiftDown();
            switch (e.getKeyCode()) {
                case java.awt.event.KeyEvent.VK_UP, java.awt.event.KeyEvent.VK_KP_UP, java.awt.event.KeyEvent.VK_NUMPAD8 -> {
                    if (shift) {
                        runOnBackend(PlotterBackend::penup);
                    } else {
                        jog(0, 1);
                    }
                    return true;
                }
                case java.awt.event.KeyEvent.VK_DOWN, java.awt.event.KeyEvent.VK_KP_DOWN, java.awt.event.KeyEvent.VK_NUMPAD2 -> {
                    if (shift) {
                        runOnBackend(PlotterBackend::pendown);
                    } else {
                        jog(0, -1);
                    }
                    return true;
                }
                case java.awt.event.KeyEvent.VK_LEFT, java.awt.event.KeyEvent.VK_KP_LEFT, java.awt.event.KeyEvent.VK_NUMPAD4 -> { jog(-1, 0); return true; }
                case java.awt.event.KeyEvent.VK_RIGHT, java.awt.event.KeyEvent.VK_KP_RIGHT, java.awt.event.KeyEvent.VK_NUMPAD6 -> { jog(1, 0); return true; }
                default -> { return false; }
            }
        };
    }

    private JPanel toolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));

        connectBtn.addActionListener(e -> onConnectToggle());
        setConnectButtonColor(true);
        bar.add(connectBtn);

        bar.add(Box.createHorizontalStrut(12));
        bar.add(statusLabel);
        return bar;
    }

    /** Builds the classical File/Settings/Help menu bar, hosted by the top-level frame. */
    public JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        fileMenu.add(accel(tip(menuItem("Open Commands (JSON)...", e -> onLoadCommands(), true),
                "Open a saved Gantry command file (.json) — the editable drawing model "
                        + "(layers, moves, draws, refills). Not an SVG and not G-code."),
                KeyEvent.VK_O, shortcut));
        fileMenu.add(accel(tip(menuItem("Import SVG (artwork)...", e -> onImportSvg(), true),
                "Read a vector drawing (.svg) and convert it into the editable command model. "
                        + "This is where artwork enters Gantry."),
                KeyEvent.VK_I, shortcut));
        fileMenu.add(accel(tip(menuItem("Save Commands (JSON)...", e -> onSaveCommands(), true),
                "Save the current drawing as a Gantry command file (.json) so you can reopen and "
                        + "keep editing it later. This is Gantry's working format, not G-code."),
                KeyEvent.VK_S, shortcut));
        fileMenu.addSeparator();
        fileMenu.add(accel(tip(menuItem("Export G-code (for plotter)...", e -> onExportGcode(), true),
                "Write machine instructions (.gcode) for the plotter from the current drawing. "
                        + "One-way output — G-code can't be reopened for editing."),
                KeyEvent.VK_E, shortcut));
        fileMenu.add(tip(menuItem("Replay G-code...", e -> onReplayGcode(), true),
                "Stream an existing G-code file (.gcode) straight to the plotter, bypassing the "
                        + "command model."));
        fileMenu.addSeparator();
        fileMenu.add(accel(menuItem("Exit", e -> onExit(), false), KeyEvent.VK_Q, shortcut));
        menuBar.add(fileMenu);

        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic(KeyEvent.VK_E);
        undoMenuItem = accel(menuItem("Undo", e -> onUndo(), true), KeyEvent.VK_Z, shortcut);
        undoMenuItem.setEnabled(false);
        editMenu.add(undoMenuItem);
        editMenu.addSeparator();
        editMenu.add(tip(menuItem("Re-process Source SVG...", e -> onEditProcessSvg(), true),
                "Re-run the SVGToolBox processors against the SVG you imported, replacing the current "
                        + "drawing. Only available after Import SVG (a loaded .json command file has no source SVG)."));
        editMenu.add(tip(menuItem("Optimize Commands (JSON)...", e -> onOptimizeDialog(), true),
                "Clean up the current drawing in place — simplify, reorder, and weld touching strokes. "
                        + "Edits the command model; does not touch any SVG or G-code file."));
        editMenu.add(menuItem("Map Layer Colors to Stations", e -> onMapColorsToStations(), true));
        menuBar.add(editMenu);

        JMenu machineMenu = new JMenu("Machine");
        machineMenu.setMnemonic(KeyEvent.VK_M);
        connectMenuItem = tip(menuItem("Connect", e -> onConnectToggle(), false),
                "Open or close the serial connection to the plotter.");
        machineMenu.add(connectMenuItem);
        machineMenu.add(tip(menuItem("Home", e -> onHome(), true),
                "Run the homing cycle against the limit switches."));
        machineMenu.addSeparator();
        machineMenu.add(tip(menuItem("Pre-Plot Checklist...", e -> onPreflightWizard(), true),
                "Walk through connect / home / frame-the-job / pen-and-paper checks before starting a plot."));
        machineMenu.add(tip(menuItem("Setup Wizard...", e -> onSetupWizard(), false),
                "Guided first-time setup: machine size, origin, orientation, pen mode and speeds."));
        machineMenu.add(tip(menuItem("Calibrate Axes...", e -> onCalibrateAxesWizard(), false),
                "Check that the axes move the right direction, and correct steps/mm if a commanded "
                        + "move doesn't match the measured distance."));
        menuBar.add(machineMenu);

        JMenu settingsMenu = new JMenu("Settings");
        settingsMenu.setMnemonic(KeyEvent.VK_S);
        settingsMenu.add(menuItem("Preferences...", e -> onOpenSettings(), true));
        menuBar.add(settingsMenu);

        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        helpMenu.add(menuItem("User Guide...", e -> onShowHelp(), false));
        helpMenu.add(menuItem("About Gantry...", e -> onShowAbout(), false));
        menuBar.add(helpMenu);

        return menuBar;
    }

    /** Builds a menu item, optionally registering it to be disabled while a plot is running. */
    private JMenuItem menuItem(String label, ActionListener listener, boolean disableDuringPlot) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(listener);
        return disableDuringPlot ? disableDuringPlot(item) : item;
    }

    /** Attaches a hover tooltip to a menu item and returns it, so format/flow help is one mouse-over away. */
    private static JMenuItem tip(JMenuItem item, String tooltip) {
        item.setToolTipText(tooltip);
        return item;
    }

    /** Attaches a keyboard accelerator to a menu item and returns it. */
    private static JMenuItem accel(JMenuItem item, int keyCode, int modifiers) {
        item.setAccelerator(javax.swing.KeyStroke.getKeyStroke(keyCode, modifiers));
        return item;
    }

    private void onExit() {
        SwingUtilities.getWindowAncestor(this).dispose();
    }

    private void onShowHelp() {
        new HelpDialog(SwingUtilities.getWindowAncestor(this)).setVisible(true);
    }

    private void onShowAbout() {
        String message = "Gantry\nVersion 1.0-SNAPSHOT\n\nA pen-plotter control and SVG-to-G-code pipeline.";
        JOptionPane.showMessageDialog(this, message, "About Gantry", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Pre-plot wizard (roadmap Phase 14): an optional, repeatable walk through the steps that
     * matter before every job — connect, home, trace the job's outline pen-up so the operator can
     * check paper placement, confirm pen/paper/layers by eye, then start. Every step calls the
     * exact same {@link PlotService}/backend methods the non-wizard buttons use; nothing here is a
     * second code path.
     */
    private void onPreflightWizard() {
        if (currentOutput == null) {
            JOptionPane.showMessageDialog(this, "Load or import a drawing first.",
                    "Pre-Plot Checklist", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<WizardStep> steps = new ArrayList<>();
        PreflightConnectStep connectStep = new PreflightConnectStep();
        steps.add(connectStep);
        steps.add(new PreflightHomeStep());
        steps.add(new PreflightFrameStep());
        PreflightChecklistStep checklistStep = new PreflightChecklistStep();
        steps.add(checklistStep);
        steps.add(new PreflightConfirmStep());
        WizardDialog wizard = new WizardDialog(SwingUtilities.getWindowAncestor(this),
                "Pre-Plot Checklist", steps);
        connectStep.attachOwner(wizard);
        checklistStep.attachOwner(wizard);
        wizard.setVisible(true);
        if (wizard.finishedSuccessfully()) {
            onStartPlot();
        }
    }

    /** Step: connect to the plotter, or auto-advance if already connected. */
    private final class PreflightConnectStep implements WizardStep {
        private final JPanel panel;
        private final JLabel status = new JLabel();
        private WizardDialog owner;
        private javax.swing.Timer poll;

        PreflightConnectStep() {
            panel = new JPanel(new BorderLayout(0, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            panel.add(new JLabel("<html>Connect to the plotter before continuing.</html>"), BorderLayout.NORTH);
            JButton connect = new JButton("Connect");
            connect.addActionListener(e -> {
                onConnectToggle();
                refresh();
            });
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
            row.add(connect);
            row.add(status);
            panel.add(row, BorderLayout.CENTER);
        }

        void attachOwner(WizardDialog dialog) {
            this.owner = dialog;
        }

        private void refresh() {
            status.setText(backend != null ? "Connected." : "Not connected.");
            if (owner != null) {
                owner.updateNextEnabled();
            }
        }

        @Override public String title() {
            return "Connect";
        }

        @Override public JComponent panel() {
            return panel;
        }

        @Override public boolean canAdvance() {
            return backend != null;
        }

        @Override public void onEnter() {
            refresh();
            // Connecting happens on a background thread (see onConnectToggle), so poll briefly to
            // pick up the moment it finishes instead of leaving Next stuck disabled until some
            // unrelated click happens to call refresh() again.
            poll = new javax.swing.Timer(300, e -> refresh());
            poll.start();
        }

        @Override public void onLeave() {
            if (poll != null) {
                poll.stop();
                poll = null;
            }
        }
    }

    /** Step: run the homing cycle (existing {@code $H} + zero-origin flow). */
    private final class PreflightHomeStep implements WizardStep {
        private final JPanel panel;
        private final JLabel status = new JLabel("Not homed yet.");

        PreflightHomeStep() {
            panel = new JPanel(new BorderLayout(0, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            panel.add(new JLabel("<html>Run the homing cycle so the machine knows where it is.</html>"),
                    BorderLayout.NORTH);
            JButton home = new JButton("Home");
            home.addActionListener(e -> {
                onHome();
                status.setText("Homing requested - check the console for completion.");
            });
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
            row.add(home);
            row.add(status);
            panel.add(row, BorderLayout.CENTER);
        }

        @Override public String title() {
            return "Home";
        }

        @Override public JComponent panel() {
            return panel;
        }

        @Override public boolean isOptional() {
            return true;
        }
    }

    /** Step: trace the job's bounding box pen-up so the operator can check paper placement. */
    private final class PreflightFrameStep implements WizardStep {
        private final JPanel panel;
        private final JLabel status = new JLabel(" ");

        PreflightFrameStep() {
            panel = new JPanel(new BorderLayout(0, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            panel.add(new JLabel("<html>Trace the job's outline with the pen up, to check it lines up "
                    + "with the taped-down paper. Repeat as many times as you like.</html>"), BorderLayout.NORTH);
            JButton frame = new JButton("Frame the job");
            frame.addActionListener(e -> {
                frameJob();
                status.setText("Tracing outline...");
            });
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
            row.add(frame);
            row.add(status);
            panel.add(row, BorderLayout.CENTER);
        }

        @Override public String title() {
            return "Frame the job";
        }

        @Override public JComponent panel() {
            return panel;
        }

        @Override public boolean isOptional() {
            return true;
        }
    }

    /** Step: operator-attested physical checks (not machine-verified). */
    private final class PreflightChecklistStep implements WizardStep {
        private final JPanel panel;
        private final List<JCheckBox> checks = new ArrayList<>();
        private WizardDialog owner;

        PreflightChecklistStep() {
            panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            String penDesc = switch (config.gcode.penMode) {
                case "zaxis" -> "Z-axis pen holder";
                case "m3m5" -> "spindle-driven pen holder";
                default -> "servo pen lift";
            };
            for (String label : List.of(
                    "Correct pen installed for the configured " + penDesc,
                    "Pen lifts and lowers correctly (jog it once if unsure)",
                    "Paper is taped down flat at the framed area",
                    "Correct layers/stations are selected for this job")) {
                JCheckBox box = new JCheckBox(label);
                box.addActionListener(e -> { if (owner != null) owner.updateNextEnabled(); });
                checks.add(box);
                panel.add(box);
                panel.add(Box.createVerticalStrut(6));
            }
        }

        void attachOwner(WizardDialog dialog) {
            this.owner = dialog;
        }

        @Override public String title() {
            return "Physical checklist";
        }

        @Override public JComponent panel() {
            return panel;
        }

        @Override public boolean canAdvance() {
            return checks.stream().allMatch(JCheckBox::isSelected);
        }
    }

    /** Step: summary + time estimate, then Finish hands off to the existing Start action. */
    private final class PreflightConfirmStep implements WizardStep {
        private final JPanel panel;
        private final JLabel summary = new JLabel();

        PreflightConfirmStep() {
            panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            panel.add(summary, BorderLayout.NORTH);
        }

        @Override public String title() {
            return "Confirm & start";
        }

        @Override public JComponent panel() {
            return panel;
        }

        @Override public void onEnter() {
            summary.setText("<html>Everything checks out. Click Finish to start plotting "
                    + "(this triggers the same Start Plot action as the main toolbar button).</html>");
        }
    }

    /** Sends a pen-up rectangle around the job's current bounds, reusing the existing baked overlay. */
    private void frameJob() {
        if (currentOutput == null) {
            log("ERROR: No drawing loaded.");
            return;
        }
        // Frame exactly what Start would plot: same layer selection, same transform/alignment, and
        // the same soft-clamp-to-bed that the plot pipeline applies, so the trace can never command
        // the head outside the machine even if the drawing overhangs the bed.
        ProcessorOutput toFrame = preparePlotOutput();
        PlotSettings settings = config.toPlotSettings();
        settings.alignmentOffsetOverride = new double[] { visPanel.getAlignOffsetX(), visPanel.getAlignOffsetY() };
        double[] bounds = new PlotService(backend, settings).computeFrameBounds(toFrame);
        if (bounds == null) {
            log("ERROR: Nothing to frame (no drawable points in the selected layers).");
            return;
        }
        double minX = bounds[0], maxX = bounds[1], minY = bounds[2], maxY = bounds[3];
        runOnBackend(b -> {
            b.penup();
            b.moveto(minX, minY);
            b.moveto(maxX, minY);
            b.moveto(maxX, maxY);
            b.moveto(minX, maxY);
            b.moveto(minX, minY);
            log(String.format("Framed job bounds: (%.1f, %.1f) to (%.1f, %.1f)", minX, minY, maxX, maxY));
        });
    }

    /**
     * Machine setup wizard (roadmap Phase 15). Walks the {@link SettingsPanel} fields in a sensible
     * first-run order — connection → geometry → pen/speed — by re-parenting the real section panels
     * into wizard steps (so there is exactly one set of settings widgets, no duplication). On Finish
     * it commits, persists and applies the config exactly like {@code Settings > Preferences…}.
     */
    private void onSetupWizard() {
        SettingsPanel settings = new SettingsPanel();
        settings.loadConfig(config);

        List<WizardStep> steps = new ArrayList<>();
        steps.add(new PanelStep("Welcome", wrapStep(
                "<html><h3>Machine setup</h3>This wizard walks the machine settings in the order "
                        + "you'd set up a new plotter: <b>connection</b>, then <b>bed geometry &amp; "
                        + "origin</b>, then <b>pen &amp; speeds</b>. You can also reach all of these "
                        + "any time via <i>Settings &gt; Preferences…</i>.<br><br>Tip: once connected, "
                        + "use the Jog pad on the main window to confirm the geometry/origin match the "
                        + "machine before plotting.</html>")));
        steps.add(new PanelStep("Connection", wrapStep(settings.connectionPanel())));
        steps.add(new PanelStep("Geometry & origin", wrapStep(settings.geometryPanel())));
        steps.add(new PanelStep("Pen & speeds", wrapStep(settings.penPanel())));
        steps.add(new PanelStep("Done", wrapStep(
                "<html><h3>All set</h3>Click <b>Finish</b> to save these settings and apply them. "
                        + "Refill stations weren't touched here — edit those under "
                        + "<i>Settings &gt; Preferences…</i> if you use them.</html>")));

        WizardDialog wizard = new WizardDialog(SwingUtilities.getWindowAncestor(this),
                "Machine Setup", steps);
        wizard.setVisible(true);
        if (wizard.finishedSuccessfully()) {
            config = settings.toConfig();
            try {
                ConfigStore.save(config, configFile);
            } catch (IOException ex) {
                log("WARNING: Failed to save config: " + ex.getMessage());
            }
            applyConfigToVis();
            log("Machine setup saved and applied.");
        }
    }

    /** Wraps a settings section (or an HTML blurb) in a padded, scrollable wizard-step panel. */
    private static JComponent wrapStep(JComponent content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(content, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
    }

    private static JComponent wrapStep(String html) {
        // Constrain the HTML body width so the prose wraps to the dialog instead of forcing a
        // horizontal scrollbar (a bare <html> JLabel lays out at its single-line preferred width).
        String constrained = html.replaceFirst("(?i)<html>", "<html><body style='width:430px'>");
        JLabel label = new JLabel(constrained);
        label.setVerticalAlignment(SwingConstants.TOP);
        return wrapStep(label);
    }

    /**
     * Axis calibration wizard (roadmap Phase 16). Two halves: a direction sanity-check (jog +X/+Y
     * and, if the machine moved the wrong way, offer to flip the matching invert flag) and a
     * measure-and-correct scale calibration per axis (command a known distance, enter what was
     * actually measured, preview the corrected GRBL {@code $100}/{@code $101} steps/mm, and write
     * it). Requires a live connection — the scale half reads/writes GRBL settings over serial.
     */
    private void onCalibrateAxesWizard() {
        if (backend == null) {
            JOptionPane.showMessageDialog(this, "Connect to the plotter first — calibration drives the\n"
                            + "machine and reads/writes its GRBL settings.",
                    "Calibrate Axes", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<WizardStep> steps = new ArrayList<>();
        steps.add(new PanelStep("Intro", wrapStep(
                "<html><h3>Axis calibration</h3>This checks two things, in order:<br>"
                        + "<b>1. Direction</b> — jog +X and +Y and confirm the machine moves the way "
                        + "the screen says; if not, the wizard flips the matching invert setting.<br>"
                        + "<b>2. Scale</b> — command a known distance on each axis, measure what the "
                        + "machine actually moved with a ruler, and the wizard computes and writes the "
                        + "corrected steps/mm (<code>$100</code>/<code>$101</code>).<br><br>"
                        + "<b>Clear the bed and make sure the pen is up</b> — this moves the head.</html>")));
        steps.add(new CalibDirectionStep());
        steps.add(new CalibScaleStep('X', GrblSettings.X_STEPS_PER_MM));
        steps.add(new CalibScaleStep('Y', GrblSettings.Y_STEPS_PER_MM));
        steps.add(new PanelStep("Done", wrapStep(
                "<html><h3>Calibration complete</h3>Any direction flips and corrected steps/mm have "
                        + "been applied. Re-run this any time from <i>Machine &gt; Calibrate Axes…</i>.</html>")));

        WizardDialog wizard = new WizardDialog(SwingUtilities.getWindowAncestor(this), "Calibrate Axes", steps);
        wizard.setVisible(true);
        if (wizard.finishedSuccessfully()) {
            try {
                ConfigStore.save(config, configFile);
            } catch (IOException ex) {
                log("WARNING: Failed to save config: " + ex.getMessage());
            }
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
    private final class CalibDirectionStep implements WizardStep {
        private final JPanel panel;

        CalibDirectionStep() {
            panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            panel.add(new JLabel("<html>Jog each axis and confirm the head moved the way the on-screen "
                    + "+X (right) / +Y (up) arrows point. Tick the box if it went the wrong way.</html>"));
            panel.add(Box.createVerticalStrut(10));
            panel.add(axisRow("+X", 1, 0, () -> config.invertX, v -> config.invertX = v));
            panel.add(Box.createVerticalStrut(8));
            panel.add(axisRow("+Y", 0, 1, () -> config.invertY, v -> config.invertY = v));
        }

        private JPanel axisRow(String label, int dxDir, int dyDir,
                java.util.function.Supplier<Boolean> get, java.util.function.Consumer<Boolean> set) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton jogBtn = new JButton("Jog " + label);
            jogBtn.addActionListener(e -> jog(dxDir, dyDir));
            JCheckBox wrongWay = new JCheckBox("Moved the wrong way (flip " + label.substring(1) + ")");
            wrongWay.setSelected(get.get());
            wrongWay.addActionListener(e -> set.accept(wrongWay.isSelected()));
            row.add(jogBtn);
            row.add(wrongWay);
            return row;
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

    /** A left-aligned single-component row that won't stretch vertically in a BoxLayout column. */
    private static JComponent left(JComponent c) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.add(c);
        return row;
    }

    /** A left-aligned row of components. */
    private static JComponent rowOf(JComponent... cs) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (JComponent c : cs) {
            row.add(c);
        }
        return row;
    }

    /**
     * Caps a section's maximum height to its preferred height so BoxLayout won't stretch it
     * vertically, and pins it to the left so a panel narrower than the column (e.g. one with a
     * FlowLayout/BoxLayout that doesn't fill the available width) doesn't get centered and have
     * its right edge clipped by the column's fixed width.
     */
    private static <T extends JComponent> T capHeight(T component) {
        Dimension pref = component.getPreferredSize();
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
        component.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        return component;
    }

    /** Registers controls so they get disabled while a plot is running, then returns them for layout. */
    private <T extends JComponent> T disableDuringPlot(T component) {
        plotDisabledControls.add(component);
        return component;
    }

    /** Registers controls that only make sense once a backend connection is established. */
    private <T extends JComponent> T requireConnection(T component) {
        connectionRequiredControls.add(component);
        component.setEnabled(false);
        return component;
    }

    private static final Dimension JOG_BUTTON_SIZE = new Dimension(54, 54);

    private JButton jogButton(String label) {
        JButton b = new JButton(label);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 22f));
        b.setPreferredSize(JOG_BUTTON_SIZE);
        b.setMargin(new Insets(0, 0, 0, 0));
        return b;
    }

    private JPanel jogSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(section("Jog"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);

        JButton up = requireConnection(disableDuringPlot(jogButton("▲")));
        JButton down = requireConnection(disableDuringPlot(jogButton("▼")));
        JButton left = requireConnection(disableDuringPlot(jogButton("◄")));
        JButton right = requireConnection(disableDuringPlot(jogButton("►")));
        up.addActionListener(e -> jog(0, 1));
        down.addActionListener(e -> jog(0, -1));
        left.addActionListener(e -> jog(-1, 0));
        right.addActionListener(e -> jog(1, 0));

        gbc.gridx = 1; gbc.gridy = 0; panel.add(up, gbc);
        gbc.gridx = 0; gbc.gridy = 1; panel.add(left, gbc);
        gbc.gridx = 2; gbc.gridy = 1; panel.add(right, gbc);
        gbc.gridx = 1; gbc.gridy = 2; panel.add(down, gbc);

        // Step + pen controls tuck into the empty space to the right of the jog cross.
        JButton penUpBtn = requireConnection(disableDuringPlot(new JButton("Pen Up")));
        JButton penDownBtn = requireConnection(disableDuringPlot(new JButton("Pen Down")));
        penUpBtn.addActionListener(e -> runOnBackend(PlotterBackend::penup));
        penDownBtn.addActionListener(e -> runOnBackend(PlotterBackend::pendown));

        JPanel stepRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        jogStepSpinner.setToolTipText("Jog step size (mm)");
        stepRow.add(new JLabel("Step"));
        stepRow.add(disableDuringPlot(jogStepSpinner));

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        stepRow.setAlignmentX(LEFT_ALIGNMENT);
        penUpBtn.setAlignmentX(LEFT_ALIGNMENT);
        penDownBtn.setAlignmentX(LEFT_ALIGNMENT);
        penUpBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, penUpBtn.getPreferredSize().height));
        penDownBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, penDownBtn.getPreferredSize().height));
        side.add(stepRow);
        side.add(Box.createVerticalStrut(4));
        side.add(penUpBtn);
        side.add(Box.createVerticalStrut(2));
        side.add(penDownBtn);

        gbc.gridx = 3; gbc.gridy = 0; gbc.gridheight = 3;
        gbc.insets = new Insets(2, 10, 2, 2);
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(side, gbc);
        gbc.gridheight = 1; gbc.anchor = GridBagConstraints.WEST; gbc.insets = new Insets(2, 2, 2, 2);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 4;
        JPanel speedRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        speedRow.add(new JLabel("Speed"));
        JButton speedDown = requireConnection(new JButton("-"));
        JButton speedUp = requireConnection(new JButton("+"));
        JButton speedReset = requireConnection(new JButton("Reset"));
        speedDown.addActionListener(e -> runOnBackend(b -> b.adjustSpeed("down")));
        speedUp.addActionListener(e -> runOnBackend(b -> b.adjustSpeed("up")));
        speedReset.addActionListener(e -> runOnBackend(b -> b.adjustSpeed("reset")));
        speedRow.add(speedDown);
        speedRow.add(speedLabel);
        speedRow.add(speedUp);
        speedRow.add(speedReset);
        panel.add(speedRow, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JButton homeBtn = requireConnection(disableDuringPlot(new JButton("⌂ Home (limit switches)")));
        homeBtn.setFont(homeBtn.getFont().deriveFont(Font.BOLD));
        homeBtn.setToolTipText("Run the homing cycle against the limit switches");
        homeBtn.addActionListener(e -> onHome());
        panel.add(homeBtn, gbc);

        return panel;
    }

    /**
     * Edit > Optimize Loaded Commands...: prompts for simplify tolerance / reorder, then runs
     * {@link #onOptimize()}. Was previously a permanent panel in the command column; moved here
     * since it's an occasional, destructive transform like Process SVG and Map Colors, not
     * something needed at a glance.
     */
    private void onOptimizeDialog() {
        if (currentOutput == null) {
            info("Open a Commands (JSON) file or Import SVG first.");
            return;
        }
        simplifyToleranceSpinner.setToolTipText("Simplify tolerance (mm)");
        mergeToleranceSpinner.setToolTipText("Merge tolerance (mm): weld strokes that touch "
                + "end-to-end into one continuous line. 0 disables merging.");
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        form.add(new JLabel("Tolerance"));
        form.add(simplifyToleranceSpinner);
        form.add(reorderStrokesCheckBox);
        form.add(new JLabel("Merge"));
        form.add(mergeToleranceSpinner);

        int choice = JOptionPane.showConfirmDialog(this, form, "Optimize Commands (JSON)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice == JOptionPane.OK_OPTION) {
            onOptimize();
        }
    }

    private JPanel overlaySection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(section("Overlay / Position"));

        JButton resetBtn = disableDuringPlot(new JButton("Reset Position"));
        JButton rotateBtn = disableDuringPlot(new JButton("Rotate 90°"));
        JButton mirrorBtn = disableDuringPlot(new JButton("Mirror"));
        resetBtn.addActionListener(e -> visPanel.resetOverlay());
        rotateBtn.addActionListener(e -> visPanel.rotateOverlay());
        mirrorBtn.addActionListener(e -> visPanel.toggleMirror());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        buttons.add(resetBtn);
        buttons.add(rotateBtn);
        buttons.add(mirrorBtn);

        JPanel posRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        posXSpinner.setToolTipText("X offset in mm from the machine origin");
        posYSpinner.setToolTipText("Y offset in mm from the machine origin");
        posRow.add(new JLabel("X"));
        posXSpinner.setPreferredSize(new Dimension(64, posXSpinner.getPreferredSize().height));
        posRow.add(disableDuringPlot(posXSpinner));
        posRow.add(new JLabel("Y"));
        posYSpinner.setPreferredSize(new Dimension(64, posYSpinner.getPreferredSize().height));
        posRow.add(disableDuringPlot(posYSpinner));
        posRow.add(new JLabel("mm"));
        JButton setPosBtn = disableDuringPlot(new JButton("Set"));
        setPosBtn.addActionListener(e -> applyPositionFromFields());
        posRow.add(setPosBtn);

        buttons.setAlignmentX(LEFT_ALIGNMENT);
        posRow.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(buttons);
        panel.add(posRow);

        // Keep the numeric fields in sync as the drawing is dragged/positioned interactively.
        visPanel.setOverlayChangeListener(this::refreshPositionFields);
        // "Remove Drawing" in the canvas context menu drops the loaded output too.
        visPanel.setRemoveDrawingListener(this::onRemoveDrawing);
        return panel;
    }

    /** Moves the drawing so its origin-nearest corner sits at the X/Y entered in the fields. */
    private void applyPositionFromFields() {
        if (currentOutput == null) {
            log("ERROR: Load or import commands first.");
            return;
        }
        double x = ((Number) posXSpinner.getValue()).doubleValue();
        double y = ((Number) posYSpinner.getValue()).doubleValue();
        visPanel.setContentMotorMin(x, y);
    }

    /** Reflects the drawing's current origin-nearest corner position into the X/Y fields. */
    private void refreshPositionFields() {
        double[] pos = visPanel.getContentMotorMin();
        posXSpinner.setValue(Math.round(pos[0] * 10.0) / 10.0);
        posYSpinner.setValue(Math.round(pos[1] * 10.0) / 10.0);
    }

    private JPanel plotSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(section("Plot"));

        requireConnection(startBtn);
        startBtn.addActionListener(e -> {
            if (config.preflightBeforeStart) {
                onPreflightWizard();
            } else {
                onStartPlot();
            }
        });
        confirmBtn.addActionListener(e -> confirmGate.release());
        pauseBtn.addActionListener(e -> onPauseToggle());
        stopBtn.addActionListener(e -> onStopPlot());
        startBtn.setText("Start");
        startBtn.setToolTipText("Start Plot");
        confirmBtn.setText("Confirm");
        confirmBtn.setToolTipText("Confirm Layer");

        preflightBtn.setToolTipText("Walk through the Pre-Plot Checklist (connect, home, frame, physical checks) before plotting.");
        preflightBtn.addActionListener(e -> onPreflightWizard());

        // Non-wrapping horizontal rows: a FlowLayout would wrap onto a second line once the
        // control column is narrow, and the wrapped line then gets clipped by capHeight().
        JPanel row1 = new JPanel();
        row1.setLayout(new BoxLayout(row1, BoxLayout.X_AXIS));
        row1.add(new JLabel("Passes"));
        row1.add(Box.createHorizontalStrut(4));
        row1.add(multipassSpinner);
        row1.add(Box.createHorizontalStrut(4));
        row1.add(preflightBtn);
        row1.add(Box.createHorizontalStrut(4));
        row1.add(startBtn);
        row1.add(Box.createHorizontalStrut(4));
        row1.add(stopBtn);

        JPanel row2 = new JPanel();
        row2.setLayout(new BoxLayout(row2, BoxLayout.X_AXIS));
        row2.add(confirmBtn);
        row2.add(Box.createHorizontalStrut(4));
        row2.add(pauseBtn);

        // Layer header: label + All/None quick toggles.
        JPanel row3 = new JPanel();
        row3.setLayout(new BoxLayout(row3, BoxLayout.X_AXIS));
        row3.add(new JLabel("Layers"));
        row3.add(Box.createHorizontalStrut(4));
        JButton allBtn = new JButton("All");
        JButton noneBtn = new JButton("None");
        allBtn.setToolTipText("Select every layer");
        noneBtn.setToolTipText("Deselect every layer");
        allBtn.addActionListener(e -> setAllLayersChecked(true));
        noneBtn.addActionListener(e -> setAllLayersChecked(false));
        disableDuringPlot(allBtn);
        disableDuringPlot(noneBtn);
        row3.add(allBtn);
        row3.add(Box.createHorizontalStrut(2));
        row3.add(noneBtn);

        // The checkboxes themselves (one per layer), in a height-bounded scroll pane so a drawing
        // with many layers doesn't push the rest of the controls off-screen.
        layerListPanel.setLayout(new BoxLayout(layerListPanel, BoxLayout.Y_AXIS));
        JScrollPane layerScroll = new JScrollPane(layerListPanel);
        layerScroll.setToolTipText("Tick the layers to show and plot (e.g. one pen colour at a time); "
                + "unticked layers are ghosted in the preview and skipped when plotting/exporting.");
        layerScroll.setPreferredSize(new Dimension(180, 84));
        layerScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
        layerScroll.setAlignmentX(LEFT_ALIGNMENT);

        JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        timeLabel.setToolTipText("Per-layer time estimate (hover after loading/importing commands)");
        row4.add(timeLabel);
        colorByLayerCheck.setToolTipText("Draw each layer in its own colour (from the layer's source "
                + "colour) so layers/pens are easy to tell apart. Off = one uniform colour.");
        colorByLayerCheck.addActionListener(e -> visPanel.setColorByLayer(colorByLayerCheck.isSelected()));
        disableDuringPlot(colorByLayerCheck);
        row4.add(colorByLayerCheck);

        row1.setAlignmentX(LEFT_ALIGNMENT);
        row2.setAlignmentX(LEFT_ALIGNMENT);
        row3.setAlignmentX(LEFT_ALIGNMENT);
        row4.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(row1);
        panel.add(row2);
        panel.add(row3);
        panel.add(layerScroll);
        panel.add(row4);

        return panel;
    }

    /**
     * Rebuilds the layer checklist to match the current drawing: one checkbox per layer, labelled
     * with its id and source colour and tinted with the layer's preview colour so the list doubles
     * as a legend. All layers start ticked. Called whenever {@link #currentOutput} is replaced.
     */
    private void refreshLayerSelector() {
        repopulatingLayers = true;
        try {
            layerChecks.clear();
            layerListPanel.removeAll();
            if (currentOutput != null) {
                List<Layer> layers = currentOutput.layers();
                for (int i = 0; i < layers.size(); i++) {
                    Layer layer = layers.get(i);
                    String colour = layer.color() == null || layer.color().isEmpty() ? "no colour" : layer.color();
                    JCheckBox box = new JCheckBox(layer.id() + " — " + colour, true);
                    box.setForeground(visPanel.colorForLayer(i));
                    box.setAlignmentX(LEFT_ALIGNMENT);
                    box.addActionListener(e -> onLayerSelectionChanged());
                    box.setEnabled(!plotting);
                    layerChecks.add(box);
                    layerListPanel.add(box);
                }
            }
        } finally {
            repopulatingLayers = false;
        }
        layerListPanel.revalidate();
        layerListPanel.repaint();
        // Push the (all-ticked) selection to the preview.
        applyLayerFilter();
    }

    private void onLayerSelectionChanged() {
        if (repopulatingLayers) {
            return;
        }
        applyLayerFilter();
        refreshTimeEstimate();
    }

    /** Ticks or unticks every layer checkbox, then applies the change once. */
    private void setAllLayersChecked(boolean checked) {
        repopulatingLayers = true;
        try {
            for (JCheckBox box : layerChecks) {
                box.setSelected(checked);
            }
        } finally {
            repopulatingLayers = false;
        }
        onLayerSelectionChanged();
    }

    /** Pushes the currently ticked layers to the visualization. */
    private void applyLayerFilter() {
        visPanel.setSelectedLayers(selectedLayerIndices());
    }

    /** Indices (into {@code currentOutput.layers()}) of the ticked layers. */
    private List<Integer> selectedLayerIndices() {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < layerChecks.size(); i++) {
            if (layerChecks.get(i).isSelected()) {
                indices.add(i);
            }
        }
        return indices;
    }

    /** Recomputes and displays the pre-plot time estimate (total + per-layer, via tooltip). */
    private void refreshTimeEstimate() {
        if (currentOutput == null) {
            currentEstimate = null;
            timeLabel.setText("Est: --:--");
            return;
        }
        currentEstimate = TimeEstimator.estimate(preparePlotOutput(), config.gcode, config.stations);
        double speedFactor = 100.0 / speedPercent;
        timeLabel.setText("Est: " + TimeEstimator.format(currentEstimate.totalSeconds() * speedFactor));
        StringBuilder tooltip = new StringBuilder("<html>Per-layer estimate:<br>");
        for (TimeEstimator.LayerEstimate le : currentEstimate.layers()) {
            tooltip.append(le.layerId()).append(": ").append(TimeEstimator.format(le.estimatedSeconds() * speedFactor)).append("<br>");
        }
        tooltip.append("Total: ").append(TimeEstimator.format(currentEstimate.totalSeconds() * speedFactor));
        if (speedPercent != 100) {
            tooltip.append(" (at ").append(speedPercent).append("% speed)");
        }
        tooltip.append("</html>");
        timeLabel.setToolTipText(tooltip.toString());
    }

    /** Updates {@link #timeLabel} with live elapsed/estimated time while a plot is running. */
    private void updateTimeLabelDuringPlot() {
        if (currentEstimate == null) {
            return;
        }
        double speedFactor = 100.0 / speedPercent;
        double elapsed = (System.currentTimeMillis() - plotStartMillis) / 1000.0;
        StringBuilder text = new StringBuilder("Elapsed: ")
                .append(TimeEstimator.format(elapsed))
                .append(" / Est: ")
                .append(TimeEstimator.format(currentEstimate.totalSeconds() * speedFactor));
        if (currentLayerId != null) {
            double layerElapsed = (System.currentTimeMillis() - layerStartMillis) / 1000.0;
            double layerEstimate = currentEstimate.layers().stream()
                    .filter(le -> le.layerId().equals(currentLayerId))
                    .mapToDouble(TimeEstimator.LayerEstimate::estimatedSeconds)
                    .findFirst().orElse(0);
            text.append(" | ").append(currentLayerId).append(": ")
                    .append(TimeEstimator.format(layerElapsed)).append(" / ").append(TimeEstimator.format(layerEstimate * speedFactor));
        }
        timeLabel.setText(text.toString());
    }

    private JPanel rawCommandSection() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        panel.setBorder(section("Raw G-code"));

        JButton sendBtn = requireConnection(disableDuringPlot(new JButton("Send")));
        requireConnection(disableDuringPlot(rawCommandField));
        ActionListener send = e -> {
            String cmd = rawCommandField.getText().trim();
            if (cmd.isEmpty()) {
                return;
            }
            rawCommandField.setText("");
            runOnBackend(b -> {
                for (String line : b.sendRaw(cmd)) {
                    log(line);
                }
            });
        };
        sendBtn.addActionListener(send);
        rawCommandField.addActionListener(send);

        panel.add(rawCommandField);
        panel.add(sendBtn);
        return panel;
    }

    private static TitledBorder section(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD, 11f));
        return border;
    }

    // --- Actions ---------------------------------------------------------

    /** Creates a file chooser starting in the last directory a file was opened/saved in. */
    private JFileChooser newFileChooser() {
        JFileChooser chooser = new JFileChooser();
        if (config.lastDirectory != null) {
            File dir = new File(config.lastDirectory);
            if (dir.isDirectory()) {
                chooser.setCurrentDirectory(dir);
            }
        }
        return chooser;
    }

    /** Remembers {@code file}'s parent directory in the config so the next chooser starts there. */
    private void rememberDirectory(File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }
        config.lastDirectory = parent.getAbsolutePath();
        try {
            ConfigStore.save(config, configFile);
        } catch (IOException ex) {
            log("WARNING: Failed to save config: " + ex.getMessage());
        }
    }

    private void onLoadCommands() {
        JFileChooser chooser = newFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Gantry commands — JSON (*.json)", "json"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        rememberDirectory(file);
        try {
            currentOutput = CommandFile.load(file);
            // A loaded command file has no source SVG, so Edit > Re-process Source SVG must not reprocess a
            // previously-imported one. Clear the stale import state (and any cross-file undo).
            lastImportedSvgFile = null;
            lastImportOptions = null;
            undoSnapshot = null;
            if (undoMenuItem != null) {
                undoMenuItem.setEnabled(false);
            }
            visPanel.loadFromOutput(currentOutput);
            visPanel.setContentMotorMin(0, 0);
            refreshLayerSelector();
            refreshPositionFields();
            refreshTimeEstimate();
            refreshGuidance();
            log("Loaded " + file.getName());
        } catch (IOException ex) {
            error("Failed to load " + file.getName() + ": " + ex.getMessage());
        }
    }

    private void onImportSvg() {
        JFileChooser chooser = newFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Vector artwork — SVG (*.svg)", "svg"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        rememberDirectory(file);

        SvgImportDialog.Result dialogResult = new SvgImportDialog(SwingUtilities.getWindowAncestor(this)).showDialog();
        if (dialogResult == null) {
            return;
        }

        runBusy("Import", () -> {
            ProcessorOutput out = dialogResult.toolboxConfig() != null
                    ? SvgImportStage.importSvg(file, dialogResult.toolboxConfig(), dialogResult.importOptions())
                    : SvgImportStage.importSvg(file, dialogResult.importOptions());
            return autoMapColors(out);
        }, out -> {
            currentOutput = out;
            lastImportedSvgFile = file;
            lastImportOptions = dialogResult.importOptions();
            undoSnapshot = null;
            if (undoMenuItem != null) {
                undoMenuItem.setEnabled(false);
            }
            visPanel.loadFromOutput(currentOutput);
            visPanel.setContentMotorMin(0, 0);
            refreshLayerSelector();
            refreshPositionFields();
            refreshTimeEstimate();
            refreshGuidance();
            log(String.format("Imported %s: %d layer(s), %d command(s)",
                    file.getName(), currentOutput.layers().size(), currentOutput.metadata().totalCommands()));
        });
    }

    /**
     * Re-runs a subset of the SVGToolBox processors (Crop, Hatch, Palette, Rotate, Optimize)
     * against the originally imported SVG file, replacing {@link #currentOutput}. Unlike the
     * "Optimize" button (which only reorders/simplifies the already-imported paths), these
     * processors operate on the SVG document itself, so they need to re-import from the source
     * file using the same fit/position options chosen at import time.
     */
    private void onEditProcessSvg() {
        if (lastImportedSvgFile == null || lastImportOptions == null) {
            info("Import an SVG file first — Edit > Re-process Source SVG only applies to SVG imports.");
            return;
        }
        org.trostheide.gantry.svgtoolbox.Config config =
                new EditProcessDialog(SwingUtilities.getWindowAncestor(this)).showDialog();
        if (config == null) {
            return;
        }
        File source = lastImportedSvgFile;
        org.trostheide.gantry.pipeline.svgimport.SvgImportOptions options = lastImportOptions;
        snapshotForUndo();
        runBusy("Process SVG", () -> SvgImportStage.importSvg(source, config, options), out -> {
            currentOutput = out;
            visPanel.loadPathsPreservingOverlay(currentOutput);
            refreshLayerSelector();
            refreshPositionFields();
            refreshTimeEstimate();
            refreshGuidance();
            log("Reprocessed " + source.getName());
        });
    }

    /**
     * Reassigns each layer to the paint pot whose configured colour best matches the layer's
     * source colour (read from the SVG stroke/fill). Replaces fragile positional layer↔station
     * naming with a colour-driven mapping, so the operator only has to fill pots by colour.
     */
    private void onMapColorsToStations() {
        if (currentOutput == null) {
            info("Load or import a drawing first.");
            return;
        }
        List<PaintStation> stations = paintStations();
        if (stations.isEmpty()) {
            info("No stations have a colour configured. Set station colours in Settings > Refill Stations first.");
            return;
        }
        snapshotForUndo();
        currentOutput = StationMapper.assignByColor(currentOutput, stations);
        visPanel.loadPathsPreservingOverlay(currentOutput);
        refreshLayerSelector();
        logColorMapping();
        refreshTimeEstimate();
        refreshGuidance();
    }

    /** Stations that have a paint colour configured, as colour-matching targets. */
    private List<PaintStation> paintStations() {
        List<PaintStation> list = new ArrayList<>();
        for (Map.Entry<String, StationConfig> e : config.stations.entrySet()) {
            String color = e.getValue().color();
            if (color != null && !color.isBlank()) {
                list.add(new PaintStation(e.getKey(), color));
            }
        }
        return list;
    }

    /**
     * Applies colour→station mapping automatically right after an import, but only if the operator
     * has actually configured station colours; otherwise the drawing keeps its positional stations.
     */
    private ProcessorOutput autoMapColors(ProcessorOutput output) {
        if (paintStations().isEmpty()) {
            return output;
        }
        ProcessorOutput mapped = StationMapper.assignByColor(output, paintStations());
        return mapped;
    }

    private void logColorMapping() {
        for (Layer layer : currentOutput.layers()) {
            log(String.format("  %s (%s) -> station %s",
                    layer.id(), layer.color() == null ? "no colour" : layer.color(), layer.stationId()));
        }
    }

    private void onSaveCommands() {
        if (currentOutput == null) {
            info("Nothing to save. Load or import commands first.");
            return;
        }
        JFileChooser chooser = newFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Gantry commands — JSON (*.json)", "json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!confirmOverwrite(file)) {
            return;
        }
        rememberDirectory(file);
        try {
            ProcessorOutputIO.save(currentOutput, file);
            log("Saved " + file.getName());
        } catch (IOException ex) {
            error("Failed to save " + file.getName() + ": " + ex.getMessage());
        }
    }

    private void onOptimize() {
        if (currentOutput == null) {
            info("Open a Commands (JSON) file or Import SVG first.");
            return;
        }
        double tolerance = ((Number) simplifyToleranceSpinner.getValue()).doubleValue();
        boolean reorder = reorderStrokesCheckBox.isSelected();
        double mergeTolerance = ((Number) mergeToleranceSpinner.getValue()).doubleValue();

        snapshotForUndo();
        OptimizeStage.Stats before = OptimizeStage.computeStats(currentOutput);
        currentOutput = OptimizeStage.optimize(currentOutput, tolerance, reorder, mergeTolerance);
        OptimizeStage.Stats after = OptimizeStage.computeStats(currentOutput);

        visPanel.loadPathsPreservingOverlay(currentOutput);
        refreshLayerSelector();
        refreshPositionFields();
        refreshTimeEstimate();

        double travelSavedPct = before.travelDistanceMm() <= 0 ? 0
                : 100.0 * (before.travelDistanceMm() - after.travelDistanceMm()) / before.travelDistanceMm();
        log(String.format(
                "Optimized: travel %.1fmm -> %.1fmm (%.1f%% saved), points %d -> %d, strokes %d -> %d",
                before.travelDistanceMm(), after.travelDistanceMm(), travelSavedPct,
                before.pointCount(), after.pointCount(),
                before.strokeCount(), after.strokeCount()));
    }

    private void onOpenSettings() {
        SettingsPanel settingsPanel = new SettingsPanel();
        settingsPanel.loadConfig(config);

        // "Run Setup Wizard…" closes this all-at-once dialog and hands off to the guided wizard,
        // which keeps its own settings copy and applies its own result on Finish — so there's never
        // two live copies of the same fields fighting to save.
        boolean[] launchWizard = {false};
        JButton wizardBtn = new JButton("Run Setup Wizard...");
        wizardBtn.setToolTipText("Walk these settings step by step in setup order instead of all at once.");
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        header.add(wizardBtn);
        JPanel wrapper = new JPanel(new BorderLayout(0, 6));
        wrapper.add(header, BorderLayout.NORTH);
        wrapper.add(settingsPanel, BorderLayout.CENTER);

        JOptionPane pane = new JOptionPane(new JScrollPane(wrapper), JOptionPane.PLAIN_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = pane.createDialog(this, "Settings");
        wizardBtn.addActionListener(e -> { launchWizard[0] = true; dialog.dispose(); });
        dialog.setVisible(true);
        dialog.dispose();

        if (launchWizard[0]) {
            onSetupWizard();
            return;
        }
        if (!Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue())) {
            return;
        }
        config = settingsPanel.toConfig();
        try {
            ConfigStore.save(config, configFile);
        } catch (IOException ex) {
            log("WARNING: Failed to save config: " + ex.getMessage());
        }
        applyConfigToVis();
    }

    private void onConnectToggle() {
        if (backend == null) {
            PlotterBackend newBackend = config.mock ? new MockPlotterBackend(this::log) : new GcodeBackend(config.gcode);
            if (newBackend instanceof GcodeBackend gcode) {
                gcode.setPositionCallback((x, y) -> SwingUtilities.invokeLater(() -> visPanel.updatePosition(x, y)));
                gcode.setSpeedCallback(percent -> SwingUtilities.invokeLater(() -> {
                    speedLabel.setText(percent + "%");
                    visPanel.setSpeedPercent(percent);
                    speedPercent = percent;
                    if (plotClockTimer == null) {
                        refreshTimeEstimate();
                    } else {
                        updateTimeLabelDuringPlot();
                    }
                }));
                gcode.setSentCommandCallback(line -> log("> " + line));
            }
            connectBtn.setEnabled(false);
            statusLabel.setText("Connecting...");
            new Thread(() -> {
                boolean ok = newBackend.connect();
                SwingUtilities.invokeLater(() -> {
                    connectBtn.setEnabled(true);
                    if (ok) {
                        backend = newBackend;
                        connectBtn.setText("Disconnect");
                        if (connectMenuItem != null) {
                            connectMenuItem.setText("Disconnect");
                        }
                        setConnectButtonColor(false);
                        setConnectionRequiredControlsEnabled(true);
                        statusLabel.setText("Connected");
                        log("Connected.");
                    } else {
                        statusLabel.setText("Connection failed");
                        log("ERROR: Connection failed.");
                    }
                    refreshGuidance();
                });
            }, "backend-connect").start();
        } else {
            // Disconnecting mid-plot would orphan the plot thread and leave the machine moving with
            // the pen down — confirm, then stop the plot cleanly before tearing down the backend.
            if (plotting) {
                int choice = JOptionPane.showConfirmDialog(this,
                        "A plot is still running. Stop it and disconnect?",
                        "Plot in progress", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) {
                    return;
                }
                onStopPlot();
            }
            PlotterBackend toClose = backend;
            backend = null;
            connectBtn.setText("Connect");
            if (connectMenuItem != null) {
                connectMenuItem.setText("Connect");
            }
            setConnectButtonColor(true);
            setConnectionRequiredControlsEnabled(false);
            statusLabel.setText("Disconnected");
            new Thread(toClose::disconnect, "backend-disconnect").start();
            refreshGuidance();
        }
    }

    /**
     * Tints the Connect button to guide the user through the workflow: green ("Connect") when
     * idle/disconnected so it stands out as the first required step, red ("Disconnect") once
     * connected so the next reversible action (disconnecting) stays clearly visible too.
     */
    private void setConnectButtonColor(boolean disconnected) {
        connectBtn.setBackground(disconnected ? ACTION_GREEN : ACTION_RED);
        connectBtn.setForeground(Color.WHITE);
        connectBtn.setOpaque(true);
    }

    private void setConnectionRequiredControlsEnabled(boolean enabled) {
        for (JComponent c : connectionRequiredControls) {
            c.setEnabled(enabled);
        }
    }

    private void onStartPlot() {
        if (currentOutput == null) {
            log("ERROR: Load a commands file first.");
            return;
        }
        if (backend == null) {
            log("ERROR: Connect to the plotter first.");
            return;
        }
        if (selectedLayerIndices().isEmpty()) {
            log("ERROR: No layers selected. Tick at least one layer to plot.");
            return;
        }

        ProcessorOutput toPlot = preparePlotOutput();

        PlotSettings settings = config.toPlotSettings();
        settings.reportPosition = true;
        settings.realtimePosition = backend instanceof GcodeBackend;
        // Plot exactly what the preview shows: use the offset the visualization is displaying
        // (incl. any interactive drag/numeric positioning) rather than re-aligning baked content.
        settings.alignmentOffsetOverride = new double[] { visPanel.getAlignOffsetX(), visPanel.getAlignOffsetY() };

        PlotService service = new PlotService(backend, settings);
        service.setLogCallback(this::log);
        service.setCommandedPositionCallback((x, y) -> SwingUtilities.invokeLater(() -> visPanel.updatePosition(x, y)));
        service.setLayerGate(layer -> {
            log("Layer '" + layer.id() + "' ready. Click Confirm Layer to start.");
            confirmGate.acquire();
        });
        service.setLayerStartedCallback(layer -> SwingUtilities.invokeLater(() -> {
            currentLayerId = layer.id();
            layerStartMillis = System.currentTimeMillis();
        }));

        confirmGate.drainPermits();
        activeService = service;
        currentEstimate = TimeEstimator.estimate(toPlot, config.gcode, config.stations);
        currentLayerId = null;
        plotStartMillis = System.currentTimeMillis();
        setPlottingState(true);
        plotClockTimer = new javax.swing.Timer(500, e -> updateTimeLabelDuringPlot());
        plotClockTimer.start();

        ProcessorOutput finalOutput = toPlot;
        new Thread(() -> {
            try {
                service.plot(finalOutput);
                log("--- Plot finished ---");
            } finally {
                activeService = null;
                SwingUtilities.invokeLater(() -> setPlottingState(false));
            }
        }, "plot-thread").start();
    }

    private void onStopPlot() {
        PlotService service = activeService;
        if (service != null) {
            service.cancel();
        }
        confirmGate.release();
        // Cancelling only stops further commands from being sent; the backend may already have
        // queued motion in flight (e.g. GRBL's planner buffer), so halt it immediately too.
        runOnBackend(PlotterBackend::haltMotion);
        resetGuidance();
    }

    private void onPauseToggle() {
        PlotService service = activeService;
        if (service == null) {
            return;
        }
        if (!paused) {
            service.pause();
            paused = true;
            pauseBtn.setText("Resume");
        } else {
            service.resume();
            paused = false;
            pauseBtn.setText("Pause");
        }
        refreshGuidance();
    }

    private void setPlottingState(boolean plotting) {
        this.plotting = plotting;
        startBtn.setEnabled(!plotting && backend != null);
        confirmBtn.setEnabled(plotting);
        pauseBtn.setEnabled(plotting);
        stopBtn.setEnabled(plotting);
        // Jog/pen/edit controls are unsafe to use mid-plot — disable them while plotting.
        for (JComponent c : plotDisabledControls) {
            c.setEnabled(!plotting);
        }
        // The per-layer checkboxes are rebuilt on every load, so they aren't in the list above;
        // toggle them here so the layer selection can't change mid-plot.
        for (JCheckBox box : layerChecks) {
            box.setEnabled(!plotting);
        }
        if (!plotting) {
            paused = false;
            pauseBtn.setText("Pause");
            if (plotClockTimer != null) {
                plotClockTimer.stop();
                plotClockTimer = null;
            }
            currentLayerId = null;
            refreshTimeEstimate();
        }
        refreshGuidance();
    }

    private void jog(int dxDir, int dyDir) {
        if (backend == null) {
            return;
        }
        PlotSettings settings = config.toPlotSettings();
        double step = ((Number) jogStepSpinner.getValue()).doubleValue();
        double dx = dxDir;
        double dy = dyDir;
        if (settings.swapXY) {
            double t = dx;
            dx = dy;
            dy = t;
        }
        if (settings.invertX) {
            dx = -dx;
        }
        if (settings.invertY) {
            dy = -dy;
        }
        double mdx = dx * step;
        double mdy = dy * step;
        runOnBackend(b -> b.move(mdx, mdy));
    }

    private void onHome() {
        if (backend == null) {
            log("ERROR: Not connected.");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Run the homing cycle? The plotter will drive toward the limit switches at 0/0.",
                "Home plotter", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        log("Homing...");
        runOnBackend(b -> {
            b.home();
            log("Homed. Origin zeroed at (0, 0).");
        });
    }

    private void runOnBackend(java.util.function.Consumer<PlotterBackend> action) {
        PlotterBackend b = backend;
        if (b == null) {
            log("ERROR: Not connected.");
            return;
        }
        new Thread(() -> action.accept(b), "backend-action").start();
    }

    /**
     * The output that will actually be plotted/exported: {@link #currentOutput}, narrowed to the
     * layers ticked in the layer checklist. This is what lets the operator plot a chosen subset of
     * layers (e.g. one pen's worth of strokes), swap the pen, then plot the next.
     */
    private ProcessorOutput selectedOutput() {
        if (currentOutput == null) {
            return null;
        }
        List<Integer> selected = selectedLayerIndices();
        if (selected.size() == currentOutput.layers().size()) {
            return currentOutput;
        }
        List<Layer> kept = new ArrayList<>();
        for (int idx : selected) {
            kept.add(currentOutput.layers().get(idx));
        }
        return new ProcessorOutput(currentOutput.metadata(), kept);
    }

    /** Applies overlay baking and the configured multipass count to the selected output for plotting/export. */
    private ProcessorOutput preparePlotOutput() {
        ProcessorOutput result = selectedOutput();
        if (visPanel.hasOverlayTransform()) {
            result = bakeOverlay(result);
        }
        int passes = ((Number) multipassSpinner.getValue()).intValue();
        result = MultipassStage.apply(result, passes);
        return result;
    }

    private void onExportGcode() {
        if (currentOutput == null) {
            info("Open a Commands (JSON) file or Import SVG first.");
            return;
        }
        if (selectedLayerIndices().isEmpty()) {
            info("No layers selected. Tick at least one layer to export.");
            return;
        }
        JFileChooser chooser = newFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Plotter G-code (*.gcode)", "gcode"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!confirmOverwrite(file)) {
            return;
        }
        rememberDirectory(file);

        ProcessorOutput toExport = preparePlotOutput();
        PlotSettings settings = config.toPlotSettings();
        settings.alignmentOffsetOverride = new double[] { visPanel.getAlignOffsetX(), visPanel.getAlignOffsetY() };

        new Thread(() -> {
            GcodeFileBackend fileBackend = new GcodeFileBackend(config.gcode, file);
            PlotService service = new PlotService(fileBackend, settings);
            service.setLogCallback(this::log);
            if (fileBackend.connect()) {
                try {
                    service.plot(toExport);
                } finally {
                    fileBackend.disconnect();
                }
                log("Exported G-code to " + file.getName());
            } else {
                error("Failed to open " + file.getName() + " for writing.");
            }
        }, "gcode-export").start();
    }

    private void onReplayGcode() {
        if (!(backend instanceof GcodeBackend gcodeBackend)) {
            log("ERROR: Connect to a real G-code backend first (not available in mock mode).");
            return;
        }
        JFileChooser chooser = newFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Plotter G-code (*.gcode)", "gcode"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        rememberDirectory(file);
        new Thread(() -> {
            try {
                log("Replaying " + file.getName() + "...");
                GcodeFileReplay.replay(file, gcodeBackend, this::log);
                log("--- Replay finished ---");
            } catch (IOException ex) {
                log("ERROR: Failed to replay " + file.getName() + ": " + ex.getMessage());
            }
        }, "gcode-replay").start();
    }

    /**
     * Returns a copy of {@code output} with the interactive overlay transform
     * (drag/resize/rotate/mirror) baked into raw content coordinates. Does not mutate the
     * input, so {@link #currentOutput} stays in its original (un-baked) frame and can be
     * re-plotted or re-positioned without compounding the transform.
     */
    private ProcessorOutput bakeOverlay(ProcessorOutput output) {
        double[] rawBounds = visPanel.getRawBounds();
        double cx = (rawBounds[0] + rawBounds[1]) / 2.0;
        double cy = (rawBounds[2] + rawBounds[3]) / 2.0;
        double scale = visPanel.getOverlayScale();
        double offsetX = visPanel.getOverlayOffsetX();
        double offsetY = visPanel.getOverlayOffsetY();
        int rotation = visPanel.getOverlayRotation();
        boolean mirror = visPanel.isOverlayMirror();

        List<Layer> bakedLayers = new ArrayList<>();
        for (Layer layer : output.layers()) {
            List<Command> bakedCommands = new ArrayList<>();
            for (Command cmd : layer.commands()) {
                if (cmd instanceof DrawCommand draw) {
                    List<Point> bakedPoints = new ArrayList<>(draw.points.size());
                    for (Point p : draw.points) {
                        double[] np = CoordinateTransform.applyOverlayRaw(p.x(), p.y(), cx, cy, scale, offsetX, offsetY, rotation, mirror);
                        bakedPoints.add(new Point(np[0], np[1]));
                    }
                    bakedCommands.add(new DrawCommand(draw.id, bakedPoints));
                } else if (cmd instanceof org.trostheide.gantry.model.command.MoveCommand move) {
                    double[] np = CoordinateTransform.applyOverlayRaw(move.x, move.y, cx, cy, scale, offsetX, offsetY, rotation, mirror);
                    bakedCommands.add(new org.trostheide.gantry.model.command.MoveCommand(move.id, np[0], np[1]));
                } else {
                    bakedCommands.add(cmd);
                }
            }
            bakedLayers.add(new Layer(layer.id(), layer.stationId(), layer.color(), bakedCommands));
        }
        return new ProcessorOutput(output.metadata(), bakedLayers);
    }

    private void applyConfigToVis() {
        visPanel.setMachineOrigin(config.machineOrigin);
        visPanel.setOrientation(config.orientation);
        visPanel.setMachineSize(config.gcode.machineWidth, config.gcode.machineHeight);
        visPanel.setCanvasAlignment(config.canvasAlignment);
        visPanel.setDataRotation(config.dataRotation);
        PlotSettings settings = config.toPlotSettings();
        visPanel.setEffectiveAxes(settings.swapXY, settings.invertX, settings.invertY);
        visPanel.setPadding(config.paddingX, config.paddingY);
        visPanel.setFlipY(config.flipY);

        List<VisualizationPanel.Station> stations = new ArrayList<>();
        for (Map.Entry<String, StationConfig> entry : config.stations.entrySet()) {
            StationConfig s = entry.getValue();
            stations.add(new VisualizationPanel.Station(entry.getKey(), s.x(), s.y()));
        }
        visPanel.setStations(stations);
    }

    private void log(String line) {
        SwingUtilities.invokeLater(() -> {
            console.append(line + "\n");
            console.setCaretPosition(console.getDocument().getLength());
        });
    }

    /** Logs a failure to the console and also surfaces it as an error dialog so it can't be missed. */
    private void error(String message) {
        log("ERROR: " + message);
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE));
    }

    /** Logs an informational note and shows it as a dialog (for workflow guidance the user must act on). */
    private void info(String message) {
        log(message);
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, message, "Gantry", JOptionPane.INFORMATION_MESSAGE));
    }

    /** Returns true if {@code file} may be written: it doesn't exist, or the user confirms overwrite. */
    private boolean confirmOverwrite(File file) {
        if (!file.exists()) {
            return true;
        }
        return JOptionPane.showConfirmDialog(this,
                file.getName() + " already exists. Overwrite it?", "Confirm overwrite",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /**
     * Runs a potentially slow command-model transform off the EDT with a wait cursor, then applies
     * the result back on the EDT. Keeps the UI responsive during import/optimize/process and routes
     * any failure to an error dialog.
     */
    private void runBusy(String description, Callable<ProcessorOutput> task, Consumer<ProcessorOutput> onSuccess) {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) {
            window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        new javax.swing.SwingWorker<ProcessorOutput, Void>() {
            @Override
            protected ProcessorOutput doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                if (window != null) {
                    window.setCursor(Cursor.getDefaultCursor());
                }
                try {
                    onSuccess.accept(get());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    error(description + " failed: " + cause.getMessage());
                }
            }
        }.execute();
    }

    /**
     * Drops the loaded drawing (triggered from the canvas "Remove Drawing" context menu). The
     * visualization has already cleared itself; here we discard the backing output and reset the
     * dependent state so a subsequent plot/export has nothing stale to act on.
     */
    private void onRemoveDrawing() {
        if (currentOutput == null) {
            return;
        }
        currentOutput = null;
        lastImportedSvgFile = null;
        lastImportOptions = null;
        undoSnapshot = null;
        if (undoMenuItem != null) {
            undoMenuItem.setEnabled(false);
        }
        refreshLayerSelector();
        refreshPositionFields();
        refreshTimeEstimate();
        refreshGuidance();
        log("Removed the loaded drawing.");
    }

    /** Snapshots {@link #currentOutput} so the next destructive transform can be undone. */
    private void snapshotForUndo() {
        undoSnapshot = currentOutput;
        if (undoMenuItem != null) {
            undoMenuItem.setEnabled(true);
        }
    }

    private void onUndo() {
        if (undoSnapshot == null) {
            return;
        }
        currentOutput = undoSnapshot;
        undoSnapshot = null;
        if (undoMenuItem != null) {
            undoMenuItem.setEnabled(false);
        }
        visPanel.loadPathsPreservingOverlay(currentOutput);
        refreshLayerSelector();
        refreshPositionFields();
        refreshTimeEstimate();
        refreshGuidance();
        log("Undo: reverted the last transform.");
    }
}
