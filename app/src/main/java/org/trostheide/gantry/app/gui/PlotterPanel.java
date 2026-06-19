package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.CommandFile;
import org.trostheide.gantry.app.plot.ConfigStore;
import org.trostheide.gantry.app.plot.GantryConfig;
import org.trostheide.gantry.app.plot.PlotService;
import org.trostheide.gantry.app.plot.PlotSettings;
import org.trostheide.gantry.app.plot.StationConfig;
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
import org.trostheide.gantry.plotter.MockPlotterBackend;
import org.trostheide.gantry.plotter.PlotterBackend;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Self-contained plotter window: live visualization, jog/pen/speed controls, raw G-code console
 * and Start/Confirm/Stop plot controls, wired directly to {@link PlotService} and a
 * {@link PlotterBackend} (no subprocess).
 */
public class PlotterPanel extends JPanel {

    private final File configFile = new File("config.json");
    private GantryConfig config = ConfigStore.load(configFile);

    private final VisualizationPanel visPanel = new VisualizationPanel();
    private final JTextArea console = new JTextArea();

    private final JButton connectBtn = new JButton("Connect");
    private final JButton startBtn = new JButton("Start Plot");
    private final JButton confirmBtn = new JButton("Confirm Layer");
    private final JButton pauseBtn = new JButton("Pause");
    private final JButton stopBtn = new JButton("Stop");
    private final JLabel statusLabel = new JLabel("Disconnected");
    private final JLabel speedLabel = new JLabel("100%");
    private final JSpinner jogStepSpinner = new JSpinner(new SpinnerNumberModel(10.0, 0.1, 200.0, 1.0));
    private final JTextField rawCommandField = new JTextField(16);
    private final JSpinner simplifyToleranceSpinner = new JSpinner(new SpinnerNumberModel(0.2, 0.0, 10.0, 0.1));
    private final JCheckBox reorderStrokesCheckBox = new JCheckBox("Reorder strokes (minimize travel)", true);
    private final JSpinner multipassSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
    private final JSpinner posXSpinner = new JSpinner(new SpinnerNumberModel(0.0, -2000.0, 2000.0, 1.0));
    private final JSpinner posYSpinner = new JSpinner(new SpinnerNumberModel(0.0, -2000.0, 2000.0, 1.0));

    private PlotterBackend backend;
    private ProcessorOutput currentOutput;
    private volatile PlotService activeService;
    private final Semaphore confirmGate = new Semaphore(0);
    private boolean paused;
    private java.awt.KeyEventDispatcher jogKeyDispatcher;

    public PlotterPanel() {
        setLayout(new BorderLayout(4, 4));
        setBorder(new EmptyBorder(6, 6, 6, 6));

        add(toolbar(), BorderLayout.NORTH);

        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane consoleScroll = new JScrollPane(console);
        consoleScroll.setBorder(section("Console"));
        consoleScroll.setPreferredSize(new Dimension(260, 160));

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(jogSection());
        right.add(Box.createVerticalStrut(6));
        right.add(penSpeedSection());
        right.add(Box.createVerticalStrut(6));
        right.add(optimizeSection());
        right.add(Box.createVerticalStrut(6));
        right.add(overlaySection());
        right.add(Box.createVerticalStrut(6));
        right.add(plotSection());
        right.add(Box.createVerticalStrut(6));
        right.add(rawCommandSection());
        right.add(Box.createVerticalStrut(6));
        right.add(consoleScroll);

        right.setPreferredSize(new Dimension(300, right.getPreferredSize().height));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, visPanel, right);
        split.setResizeWeight(0.85);
        add(split, BorderLayout.CENTER);

        startBtn.setBackground(new Color(46, 125, 50));
        startBtn.setForeground(Color.WHITE);
        startBtn.setOpaque(true);
        stopBtn.setBackground(new Color(198, 40, 40));
        stopBtn.setForeground(Color.WHITE);
        stopBtn.setOpaque(true);

        applyConfigToVis();
        setPlottingState(false);
        installJogKeyBindings();
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

        JButton loadBtn = new JButton("Load Commands...");
        loadBtn.addActionListener(e -> onLoadCommands());
        bar.add(loadBtn);

        JButton importSvgBtn = new JButton("Import SVG...");
        importSvgBtn.addActionListener(e -> onImportSvg());
        bar.add(importSvgBtn);

        JButton saveBtn = new JButton("Save Commands...");
        saveBtn.addActionListener(e -> onSaveCommands());
        bar.add(saveBtn);

        JButton settingsBtn = new JButton("Settings...");
        settingsBtn.addActionListener(e -> onOpenSettings());
        bar.add(settingsBtn);

        connectBtn.addActionListener(e -> onConnectToggle());
        bar.add(connectBtn);

        bar.add(Box.createHorizontalStrut(12));
        bar.add(statusLabel);
        return bar;
    }

    private static final Dimension JOG_BUTTON_SIZE = new Dimension(72, 72);

    private JButton jogButton(String label) {
        JButton b = new JButton(label);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 28f));
        b.setPreferredSize(JOG_BUTTON_SIZE);
        b.setMargin(new Insets(0, 0, 0, 0));
        return b;
    }

    private JPanel jogSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(section("Jog"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);

        JButton up = jogButton("▲");
        JButton down = jogButton("▼");
        JButton left = jogButton("◄");
        JButton right = jogButton("►");
        up.addActionListener(e -> jog(0, 1));
        down.addActionListener(e -> jog(0, -1));
        left.addActionListener(e -> jog(-1, 0));
        right.addActionListener(e -> jog(1, 0));

        gbc.gridx = 1; gbc.gridy = 0; panel.add(up, gbc);
        gbc.gridx = 0; gbc.gridy = 1; panel.add(left, gbc);
        gbc.gridx = 2; gbc.gridy = 1; panel.add(right, gbc);
        gbc.gridx = 1; gbc.gridy = 2; panel.add(down, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        panel.add(new JLabel("Step (mm)"), gbc);
        gbc.gridx = 1; gbc.gridy = 3; gbc.gridwidth = 2;
        panel.add(jogStepSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 3;
        JPanel penButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton penUpBtn = new JButton("Pen Up");
        JButton penDownBtn = new JButton("Pen Down");
        penUpBtn.addActionListener(e -> runOnBackend(PlotterBackend::penup));
        penDownBtn.addActionListener(e -> runOnBackend(PlotterBackend::pendown));
        penButtons.add(penUpBtn);
        penButtons.add(penDownBtn);
        panel.add(penButtons, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 3;
        JButton homeBtn = new JButton("⌂ Home (limit switches)");
        homeBtn.setFont(homeBtn.getFont().deriveFont(Font.BOLD));
        homeBtn.setToolTipText("Run the homing cycle against the limit switches");
        homeBtn.addActionListener(e -> onHome());
        panel.add(homeBtn, gbc);

        return panel;
    }

    private JPanel penSpeedSection() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        panel.setBorder(section("Speed Override"));

        JButton speedDown = new JButton("-");
        JButton speedReset = new JButton("Reset");
        JButton speedUp = new JButton("+");
        speedDown.addActionListener(e -> runOnBackend(b -> b.adjustSpeed("down")));
        speedUp.addActionListener(e -> runOnBackend(b -> b.adjustSpeed("up")));
        speedReset.addActionListener(e -> runOnBackend(b -> b.adjustSpeed("reset")));

        panel.add(speedDown);
        panel.add(speedLabel);
        panel.add(speedUp);
        panel.add(speedReset);
        return panel;
    }

    private JPanel optimizeSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(section("Optimize"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Simplify tolerance (mm)"), gbc);
        gbc.gridx = 1;
        panel.add(simplifyToleranceSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        panel.add(reorderStrokesCheckBox, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        JButton optimizeBtn = new JButton("Optimize Loaded Commands");
        optimizeBtn.addActionListener(e -> onOptimize());
        panel.add(optimizeBtn, gbc);

        return panel;
    }

    private JPanel overlaySection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(section("Overlay / Position"));

        JButton resetBtn = new JButton("Reset Position");
        JButton rotateBtn = new JButton("Rotate 90°");
        JButton mirrorBtn = new JButton("Mirror");
        resetBtn.addActionListener(e -> visPanel.resetOverlay());
        rotateBtn.addActionListener(e -> visPanel.rotateOverlay());
        mirrorBtn.addActionListener(e -> visPanel.toggleMirror());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        buttons.add(resetBtn);
        buttons.add(rotateBtn);
        buttons.add(mirrorBtn);

        JPanel posRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        posRow.add(new JLabel("X"));
        posXSpinner.setPreferredSize(new Dimension(70, posXSpinner.getPreferredSize().height));
        posRow.add(posXSpinner);
        posRow.add(new JLabel("Y (mm from origin)"));
        posYSpinner.setPreferredSize(new Dimension(70, posYSpinner.getPreferredSize().height));
        posRow.add(posYSpinner);
        JButton setPosBtn = new JButton("Set");
        setPosBtn.addActionListener(e -> applyPositionFromFields());
        posRow.add(setPosBtn);

        buttons.setAlignmentX(LEFT_ALIGNMENT);
        posRow.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(buttons);
        panel.add(posRow);

        // Keep the numeric fields in sync as the drawing is dragged/positioned interactively.
        visPanel.setOverlayChangeListener(this::refreshPositionFields);
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

        startBtn.addActionListener(e -> onStartPlot());
        confirmBtn.addActionListener(e -> confirmGate.release());
        pauseBtn.addActionListener(e -> onPauseToggle());
        stopBtn.addActionListener(e -> onStopPlot());

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row1.add(new JLabel("Passes"));
        row1.add(multipassSpinner);
        row1.add(startBtn);
        row1.add(confirmBtn);
        row1.add(pauseBtn);
        row1.add(stopBtn);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton exportBtn = new JButton("Export G-code...");
        exportBtn.addActionListener(e -> onExportGcode());
        row2.add(exportBtn);

        JButton replayBtn = new JButton("Replay G-code...");
        replayBtn.addActionListener(e -> onReplayGcode());
        row2.add(replayBtn);

        row1.setAlignmentX(LEFT_ALIGNMENT);
        row2.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(row1);
        panel.add(row2);

        return panel;
    }

    private JPanel rawCommandSection() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        panel.setBorder(section("Raw G-code"));

        JButton sendBtn = new JButton("Send");
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
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD, 12f));
        return border;
    }

    // --- Actions ---------------------------------------------------------

    private void onLoadCommands() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Command files (*.json)", "json"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            currentOutput = CommandFile.load(file);
            visPanel.loadFromOutput(currentOutput);
            refreshPositionFields();
            log("Loaded " + file.getName());
        } catch (IOException ex) {
            log("ERROR: Failed to load " + file.getName() + ": " + ex.getMessage());
        }
    }

    private void onImportSvg() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SVG files (*.svg)", "svg"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();

        SvgImportDialog.Result dialogResult = new SvgImportDialog(SwingUtilities.getWindowAncestor(this)).showDialog();
        if (dialogResult == null) {
            return;
        }

        try {
            currentOutput = dialogResult.toolboxConfig() != null
                    ? SvgImportStage.importSvg(file, dialogResult.toolboxConfig(), dialogResult.importOptions())
                    : SvgImportStage.importSvg(file, dialogResult.importOptions());
            visPanel.loadFromOutput(currentOutput);
            refreshPositionFields();
            log(String.format("Imported %s: %d layer(s), %d command(s)",
                    file.getName(), currentOutput.layers().size(), currentOutput.metadata().totalCommands()));
        } catch (IOException ex) {
            log("ERROR: Failed to import " + file.getName() + ": " + ex.getMessage());
        }
    }

    private void onSaveCommands() {
        if (currentOutput == null) {
            log("ERROR: Nothing to save. Load or import commands first.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Command files (*.json)", "json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            ProcessorOutputIO.save(currentOutput, file);
            log("Saved " + file.getName());
        } catch (IOException ex) {
            log("ERROR: Failed to save " + file.getName() + ": " + ex.getMessage());
        }
    }

    private void onOptimize() {
        if (currentOutput == null) {
            log("ERROR: Load a commands file first.");
            return;
        }
        double tolerance = ((Number) simplifyToleranceSpinner.getValue()).doubleValue();
        boolean reorder = reorderStrokesCheckBox.isSelected();

        OptimizeStage.Stats before = OptimizeStage.computeStats(currentOutput);
        currentOutput = OptimizeStage.optimize(currentOutput, tolerance, reorder);
        OptimizeStage.Stats after = OptimizeStage.computeStats(currentOutput);

        visPanel.loadFromOutput(currentOutput);
        refreshPositionFields();

        double travelSavedPct = before.travelDistanceMm() <= 0 ? 0
                : 100.0 * (before.travelDistanceMm() - after.travelDistanceMm()) / before.travelDistanceMm();
        log(String.format(
                "Optimized: travel %.1fmm -> %.1fmm (%.1f%% saved), points %d -> %d",
                before.travelDistanceMm(), after.travelDistanceMm(), travelSavedPct,
                before.pointCount(), after.pointCount()));
    }

    private void onOpenSettings() {
        SettingsPanel settingsPanel = new SettingsPanel();
        settingsPanel.loadConfig(config);
        int result = JOptionPane.showConfirmDialog(this, new JScrollPane(settingsPanel), "Settings",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
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
                gcode.setSpeedCallback(percent -> SwingUtilities.invokeLater(() -> speedLabel.setText(percent + "%")));
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
                        statusLabel.setText("Connected");
                        log("Connected.");
                    } else {
                        statusLabel.setText("Connection failed");
                        log("ERROR: Connection failed.");
                    }
                });
            }, "backend-connect").start();
        } else {
            PlotterBackend toClose = backend;
            backend = null;
            connectBtn.setText("Connect");
            statusLabel.setText("Disconnected");
            new Thread(toClose::disconnect, "backend-disconnect").start();
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

        confirmGate.drainPermits();
        activeService = service;
        setPlottingState(true);

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
    }

    private void setPlottingState(boolean plotting) {
        startBtn.setEnabled(!plotting);
        confirmBtn.setEnabled(plotting);
        pauseBtn.setEnabled(plotting);
        stopBtn.setEnabled(plotting);
        if (!plotting) {
            paused = false;
            pauseBtn.setText("Pause");
        }
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

    /** Applies overlay baking and the configured multipass count to {@link #currentOutput} for plotting/export. */
    private ProcessorOutput preparePlotOutput() {
        ProcessorOutput result = currentOutput;
        if (visPanel.hasOverlayTransform()) {
            result = bakeOverlay(result);
        }
        int passes = ((Number) multipassSpinner.getValue()).intValue();
        result = MultipassStage.apply(result, passes);
        return result;
    }

    private void onExportGcode() {
        if (currentOutput == null) {
            log("ERROR: Load a commands file first.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("G-code files (*.gcode)", "gcode"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();

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
                log("ERROR: Failed to open " + file.getName() + " for writing.");
            }
        }, "gcode-export").start();
    }

    private void onReplayGcode() {
        if (!(backend instanceof GcodeBackend gcodeBackend)) {
            log("ERROR: Connect to a real G-code backend first (not available in mock mode).");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("G-code files (*.gcode)", "gcode"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
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
            bakedLayers.add(new Layer(layer.id(), layer.stationId(), bakedCommands));
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
}
