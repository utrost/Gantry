package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.AxisDirectionSolver;
import org.trostheide.gantry.app.plot.CommandFile;
import org.trostheide.gantry.app.plot.ConfigStore;
import org.trostheide.gantry.app.plot.GantryConfig;
import org.trostheide.gantry.app.plot.PlotService;
import org.trostheide.gantry.app.plot.PlotJobController;
import org.trostheide.gantry.app.plot.PlotProgressState;
import org.trostheide.gantry.app.plot.PlotSettings;
import org.trostheide.gantry.app.plot.StationConfig;
import org.trostheide.gantry.app.plot.TimeEstimator;
import org.trostheide.gantry.app.session.DocumentSession;
import org.trostheide.gantry.model.CoordinateTransform;
import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.pipeline.io.ProcessorOutputIO;
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
    private final JLabel statusLabel = new JLabel("Disconnected");
    private final JLabel guidanceLabel = new JLabel();
    private JPanel guidancePanel;
    private boolean guidanceDismissed;
    private PlotControlsPanel plotControls;
    private DocumentEditor documentEditor;
    private DocumentFileWorkflow fileWorkflow;
    private BusyTaskRunner busyTasks;
    private GcodeFileWorkflow gcodeFiles;
    private JogPanel jogPanel;
    private BackendConnectionCoordinator connections;
    private OverlayControlsPanel overlayControls;
    private ApplicationDialogs dialogs;
    private RawCommandPanel rawCommands;

    private final PlotJobController plotJobController = new PlotJobController();
    /** Authoritative Swing-free state for the currently loaded drawing. */
    private final DocumentSession documentSession = new DocumentSession();
    private JMenuItem reVectorizeMenuItem;
    private JCheckBoxMenuItem hatchRegionModeItem;
    private JCheckBoxMenuItem deleteStrokeModeItem;
    private JCheckBoxMenuItem addLineModeItem;
    private JCheckBoxMenuItem moveStrokeModeItem;
    private JMenuItem undoMenuItem;
    private final Semaphore confirmGate = new Semaphore(0);
    private JMenuItem replotMenuItem;
    private JCheckBoxMenuItem showTravelItem;
    /** True after a plot has completed at least once for the current drawing; enables Re-plot. */
    private final PlotProgressState plotProgress = new PlotProgressState();
    private volatile int speedPercent = 100;
    private javax.swing.Timer plotClockTimer;
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

        plotControls = new PlotControlsPanel(new PlotControlsPanel.Actions(
                () -> { if (config.preflightBeforeStart) onPreflightWizard(); else onStartPlot(); },
                this::onPreflightWizard, () -> confirmGate.release(), this::onPauseToggle,
                this::onStopPlot, this::onLayerSelectionChanged, visPanel::setColorByLayer));
        documentEditor = new DocumentEditor(documentSession, this, this::onDocumentEdited,
                this::setUndoAvailable, this::log);
        busyTasks = new BusyTaskRunner(this, this::log, this::error);
        fileWorkflow = new DocumentFileWorkflow(this, configFile, documentSession, documentEditor,
                visPanel, new DocumentFileWorkflow.Actions(() -> config, this::resetReplot,
                this::refreshDocumentUi, enabled -> { if (reVectorizeMenuItem != null) reVectorizeMenuItem.setEnabled(enabled); },
                this::log, this::error, this::info, this::runBusy));
        gcodeFiles = new GcodeFileWorkflow(this, configFile, () -> config, this::preparePlotOutput,
                () -> !selectedLayerIndices().isEmpty(), plotJobController::backend,
                visPanel::getAlignOffsetX, visPanel::getAlignOffsetY, this::log, this::error, this::info);
        jogPanel = new JogPanel(() -> config, plotJobController, visPanel, this::runOnBackend, this::log, this);
        connections = new BackendConnectionCoordinator(plotJobController, this, new BackendConnectionCoordinator.Listener() {
            public void connectionState(boolean connecting, boolean connected, boolean failed){showConnectionState(connecting,connected,failed);}
            public void position(double x,double y){onJogPositionReport(x,y);SwingUtilities.invokeLater(()->visPanel.updatePosition(x,y));}
            public void speed(int percent){SwingUtilities.invokeLater(()->onSpeedChanged(percent));}
            public void sent(String line){log("> "+line);} public void log(String line){PlotterPanel.this.log(line);}
            public void stopPlot(){onStopPlot();} public void refreshGuidance(){PlotterPanel.this.refreshGuidance();}
        });
        overlayControls = new OverlayControlsPanel(visPanel, new OverlayControlsPanel.Actions() {
            public boolean hasDocument(){return documentSession.currentOutput()!=null;} public void remove(){onRemoveDrawing();}
            public void hatch(java.awt.geom.Path2D r,int l){onHatchRegion(r,l);} public void clearHatch(java.awt.geom.Path2D r){onClearHatchRegion(r);}
            public void hatchStyle(){onHatchStyleDialog();} public void delete(int id){onDeleteStroke(id);}
            public void add(double x1,double y1,double x2,double y2,int l){onAddLine(x1,y1,x2,y2,l);}
            public void move(int id,double[][]p){onMoveStroke(id,p);} public void duplicate(int id){onDuplicateStroke(id);}
            public void mode(VisualizationPanel.InteractionMode mode){syncInteractionMode(mode);}
            public void stationMoved(String n,double x,double y){onStationMovedOnCanvas(n,x,y);} public void stationAdded(double x,double y){onStationAddedOnCanvas(x,y);}
            public void log(String line){PlotterPanel.this.log(line);}
        });
        dialogs = new ApplicationDialogs(this, () -> config, this::saveSettings, this::onSetupWizard,
                this::onOptimize, () -> documentSession.currentOutput() != null);
        rawCommands = new RawCommandPanel(this::runOnBackend, this::log);

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(capHeight(jogPanel));
        right.add(Box.createVerticalStrut(3));
        right.add(capHeight(overlayControls));
        right.add(Box.createVerticalStrut(3));
        right.add(capHeight(plotControls));
        right.add(Box.createVerticalStrut(3));
        right.add(capHeight(rawCommands));
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
        if (!plotJobController.isConnected()) {
            text = "Step 1: Click Connect to talk to the plotter.";
        } else if (documentSession.currentOutput() == null) {
            text = "Step 2: Open Commands (JSON) or Import SVG to load a drawing.";
        } else if (plotting) {
            text = plotJobController.isPaused()
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
        fileMenu.add(accel(tip(menuItem("Import Image (vectorize)...", e -> onImportImage(), true),
                "Trace a raster image (.png/.jpg) into vector paths, then bring it in through the "
                        + "same SVG import. Lets photos, scans and logos enter Gantry."),
                KeyEvent.VK_I, shortcut | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
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
        replotMenuItem = accel(tip(menuItem("Re-plot Last Job", e -> onStartPlot(), false),
                "Start the exact same plot again immediately — same drawing, layer selection, and settings. "
                        + "Available after a plot completes or is stopped."),
                KeyEvent.VK_R, shortcut);
        replotMenuItem.setEnabled(false);
        fileMenu.add(replotMenuItem);
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
        reVectorizeMenuItem = tip(menuItem("Re-vectorize Image...", e -> onReVectorizeImage(), true),
                "Reopen the vectorize studio on the last imported image, pre-loaded with the parameters "
                        + "you used, to re-tune the trace. Available after Import Image (vectorize).");
        reVectorizeMenuItem.setEnabled(false);
        editMenu.add(reVectorizeMenuItem);
        editMenu.add(tip(menuItem("Optimize Commands (JSON)...", e -> onOptimizeDialog(), true),
                "Clean up the current drawing in place — simplify, reorder, and weld touching strokes. "
                        + "Edits the command model; does not touch any SVG or G-code file."));
        editMenu.add(menuItem("Map Layer Colors to Stations", e -> onMapColorsToStations(), true));
        editMenu.addSeparator();
        hatchRegionModeItem = new JCheckBoxMenuItem("Hatch Region (click areas to fill)");
        hatchRegionModeItem.setToolTipText("Click inside a closed traced region to fill it with hatch "
                + "lines that plot as part of that layer (useful for outline traces where filled areas "
                + "became bare strokes). Toggle off when done.");
        hatchRegionModeItem.addActionListener(e -> visPanel.setInteractionMode(
                hatchRegionModeItem.isSelected()
                        ? VisualizationPanel.InteractionMode.HATCH
                        : VisualizationPanel.InteractionMode.NONE));
        editMenu.add(hatchRegionModeItem);
        editMenu.add(tip(menuItem("Hatch Region Style...", e -> onHatchStyleDialog(), true),
                "Choose the pattern (linear/cross/zigzag/wave/dot), spacing and angle used when you "
                        + "click regions in Hatch Region mode. Change it between clicks to fill different "
                        + "areas with different styles."));
        editMenu.addSeparator();
        deleteStrokeModeItem = new JCheckBoxMenuItem("Delete Line (click a line to remove)");
        deleteStrokeModeItem.setToolTipText("Click a line to delete it (it highlights red under the "
                + "cursor). Undoable. Handy for removing stray strokes a trace left behind.");
        deleteStrokeModeItem.addActionListener(e -> visPanel.setInteractionMode(
                deleteStrokeModeItem.isSelected()
                        ? VisualizationPanel.InteractionMode.DELETE_STROKE
                        : VisualizationPanel.InteractionMode.NONE));
        editMenu.add(deleteStrokeModeItem);
        addLineModeItem = new JCheckBoxMenuItem("Add Line (click two points)");
        addLineModeItem.setToolTipText("Click a start point then an end point to add a straight line — "
                + "e.g. to bridge a gap so an area encloses for hatching. Undoable.");
        addLineModeItem.addActionListener(e -> visPanel.setInteractionMode(
                addLineModeItem.isSelected()
                        ? VisualizationPanel.InteractionMode.ADD_LINE
                        : VisualizationPanel.InteractionMode.NONE));
        editMenu.add(addLineModeItem);
        moveStrokeModeItem = new JCheckBoxMenuItem("Move Line (drag to reposition)");
        moveStrokeModeItem.setToolTipText("Drag a line (it highlights cyan under the cursor) to move it. "
                + "Right-click a line for Duplicate. Undoable.");
        moveStrokeModeItem.addActionListener(e -> visPanel.setInteractionMode(
                moveStrokeModeItem.isSelected()
                        ? VisualizationPanel.InteractionMode.MOVE_STROKE
                        : VisualizationPanel.InteractionMode.NONE));
        editMenu.add(moveStrokeModeItem);
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
        machineMenu.add(tip(menuItem("Test Color Stations...", e -> onTestStationsWizard(), true),
                "Drive the head to each refill station to confirm the brush lands over the pot; "
                        + "nudge any that are off and save the corrected position."));
        menuBar.add(machineMenu);

        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);
        showTravelItem = new JCheckBoxMenuItem("Show Travel Overlay");
        showTravelItem.setToolTipText("Draw pen-up moves as dashed lines coloured by distance "
                + "(green=short, orange=medium, red=long). Shows travel efficiency % in the HUD.");
        showTravelItem.addActionListener(e -> visPanel.setShowTravelOverlay(showTravelItem.isSelected()));
        viewMenu.add(showTravelItem);
        menuBar.add(viewMenu);

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
        new PreflightWorkflow(new PreflightWorkflow.Actions(
                () -> documentSession.currentOutput() != null,
                plotJobController::isConnected,
                this::onConnectToggle,
                this::onHome,
                this::frameJob,
                this::onStartPlot,
                config.gcode.penMode))
                .show(SwingUtilities.getWindowAncestor(this), this);
    }

    /** Sends a pen-up rectangle around the job's current bounds, reusing the existing baked overlay. */
    private void frameJob() {
        if (documentSession.currentOutput() == null) {
            log("ERROR: No drawing loaded.");
            return;
        }
        // Frame exactly what Start would plot: same layer selection, same transform/alignment, and
        // the same soft-clamp-to-bed that the plot pipeline applies, so the trace can never command
        // the head outside the machine even if the drawing overhangs the bed.
        ProcessorOutput toFrame = preparePlotOutput();
        PlotSettings settings = config.toPlotSettings();
        settings.alignmentOffsetOverride = new double[] { visPanel.getAlignOffsetX(), visPanel.getAlignOffsetY() };
        double[] bounds = new PlotService(plotJobController.backend(), settings).computeFrameBounds(toFrame);
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
        new SetupWorkflow(config, this::saveSetupConfig, this::onCalibrateAxesWizard)
                .show(SwingUtilities.getWindowAncestor(this));
    }

    private void saveSetupConfig(GantryConfig updated) {
        config = updated;
        try {
            ConfigStore.save(config, configFile);
        } catch (IOException ex) {
            log("WARNING: Failed to save config: " + ex.getMessage());
        }
        applyConfigToVis();
        log("Machine setup saved and applied.");
    }

    /**
     * Axis calibration wizard (roadmap Phase 16). Two halves: a direction sanity-check (jog +X/+Y
     * and, if the machine moved the wrong way, offer to flip the matching invert flag) and a
     * measure-and-correct scale calibration per axis (command a known distance, enter what was
     * actually measured, preview the corrected GRBL {@code $100}/{@code $101} steps/mm, and write
     * it). Requires a live connection — the scale half reads/writes GRBL settings over serial.
     */
    private void onCalibrateAxesWizard() {
        new CalibrationWorkflow(SwingUtilities.getWindowAncestor(this), config,
                plotJobController::isConnected, this::onConnectToggle, this::runOnBackend,
                this::log, this::applyConfigToVis, this::saveCalibrationConfig).show();
    }

    private void saveCalibrationConfig() {
        try {
            ConfigStore.save(config, configFile);
        } catch (IOException ex) {
            log("WARNING: Failed to save config: " + ex.getMessage());
        }
    }

    /**
     * Watercolor station test-run wizard (roadmap Phase 17, Half B). Requires a live connection.
     * Walks each configured refill station: a pen-up dry visit to eyeball alignment, an optional wet
     * dip running the station's real behaviour, and per-axis nudge buttons that move the head <em>and
     * </em> update the station's stored coordinates. Corrected coordinates are written back to
     * {@code config.stations} on Finish, so a misplaced pot found here is fixed for good.
     */
    private void onTestStationsWizard() {
        new StationTestWorkflow(config, plotJobController::isConnected, this::runOnBackend,
                this::persistStationsAndRefresh, this::log)
                .show(SwingUtilities.getWindowAncestor(this), this);
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

    /**
     * Edit > Optimize Loaded Commands...: prompts for simplify tolerance / reorder, then runs
     * {@link #onOptimize()}. Was previously a permanent panel in the command column; moved here
     * since it's an occasional, destructive transform like Process SVG and Map Colors, not
     * something needed at a glance.
     */
    private void onOptimizeDialog() { dialogs.optimize(); }

    private void syncInteractionMode(VisualizationPanel.InteractionMode mode) {
        if (hatchRegionModeItem == null) return;
        hatchRegionModeItem.setSelected(mode == VisualizationPanel.InteractionMode.HATCH);
        deleteStrokeModeItem.setSelected(mode == VisualizationPanel.InteractionMode.DELETE_STROKE);
        addLineModeItem.setSelected(mode == VisualizationPanel.InteractionMode.ADD_LINE);
        moveStrokeModeItem.setSelected(mode == VisualizationPanel.InteractionMode.MOVE_STROKE);
    }

    /**
     * A station marker was dragged on the canvas (Phase 17 Half A). Rewrite that station's x/y in
     * the live {@code config.stations} map — preserving every other field — persist, and re-push so
     * the canvas and the Settings station table stay in lockstep.
     */
    private void onStationMovedOnCanvas(String name, double x, double y) {
        StationConfig s = config.stations.get(name);
        if (s == null) return;
        config.stations.put(name, new StationConfig(round2(x), round2(y), s.zDown(), s.behavior(),
                s.color(), s.dwellMs(), s.swirlRadius()));
        persistStationsAndRefresh();
        log(String.format("Station '%s' moved to (%.1f, %.1f) mm", name, round2(x), round2(y)));
    }

    /** "Add station here" from the canvas context menu: insert a new station at the clicked mm. */
    private void onStationAddedOnCanvas(double x, double y) {
        // Generate a unique default name so it never collides with an existing station.
        String base = "station";
        int n = config.stations.size() + 1;
        String name = base + n;
        while (config.stations.containsKey(name)) {
            n++;
            name = base + n;
        }
        config.stations.put(name, new StationConfig(round2(x), round2(y), 30, "simple_dip",
                null, StationConfig.DEFAULT_DWELL_MS, StationConfig.DEFAULT_SWIRL_RADIUS));
        persistStationsAndRefresh();
        log(String.format("Added station '%s' at (%.1f, %.1f) mm — set its colour/behaviour in Settings",
                name, round2(x), round2(y)));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private void persistStationsAndRefresh() {
        try {
            ConfigStore.save(config, configFile);
        } catch (IOException ex) {
            log("WARNING: Failed to save station change: " + ex.getMessage());
        }
        applyConfigToVis();
    }

    private void refreshLayerSelector() {
        plotControls.rebuild(documentSession.currentOutput(), visPanel, plotting);
    }

    private void onLayerSelectionChanged() {
        applyLayerFilter();
        refreshTimeEstimate();
    }

    private void applyLayerFilter() {
        visPanel.setSelectedLayers(selectedLayerIndices());
    }

    private List<Integer> selectedLayerIndices() {
        return plotControls.selectedLayers();
    }

    /** Recomputes and displays the pre-plot time estimate (total + per-layer, via tooltip). */
    private void refreshTimeEstimate() {
        if (documentSession.currentOutput() == null) {
            plotProgress.setEstimate(null);
            plotControls.setTime("Est: --:--");
            return;
        }
        TimeEstimator.PlotEstimate currentEstimate =
                TimeEstimator.estimate(preparePlotOutput(), config.gcode, config.stations);
        plotProgress.setEstimate(currentEstimate);
        double speedFactor = 100.0 / speedPercent;
        String estimateText = "Est: " + TimeEstimator.format(currentEstimate.totalSeconds() * speedFactor);
        StringBuilder tooltip = new StringBuilder("<html>Per-layer estimate:<br>");
        for (TimeEstimator.LayerEstimate le : currentEstimate.layers()) {
            tooltip.append(le.layerId()).append(": ").append(TimeEstimator.format(le.estimatedSeconds() * speedFactor)).append("<br>");
        }
        tooltip.append("Total: ").append(TimeEstimator.format(currentEstimate.totalSeconds() * speedFactor));
        if (speedPercent != 100) {
            tooltip.append(" (at ").append(speedPercent).append("% speed)");
        }
        tooltip.append("</html>");
        plotControls.setTime(estimateText, tooltip.toString());
    }

    /** Updates the plot view with live elapsed/remaining time while a plot is running. */
    private void updateTimeLabelDuringPlot() {
        plotControls.setTime(plotProgress.liveText(System.currentTimeMillis(), speedPercent));
    }

    private static TitledBorder section(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD, 11f));
        return border;
    }

    // --- Actions ---------------------------------------------------------

    /** Creates a file chooser starting in the last directory a file was opened/saved in. */
    private void onLoadCommands() { fileWorkflow.loadCommands(); }
    private void onImportSvg() { fileWorkflow.importSvg(); }
    private void onImportImage() { fileWorkflow.importImage(); }
    private void onReVectorizeImage() { fileWorkflow.revectorize(); }
    private void onEditProcessSvg() { fileWorkflow.reprocessSvg(); }
    private void onMapColorsToStations() { fileWorkflow.mapColors(); }
    private void onSaveCommands() { fileWorkflow.saveCommands(); }

    private void onOptimize(ApplicationDialogs.OptimizeOptions options) {
        if (documentSession.currentOutput() == null) {
            info("Open a Commands (JSON) file or Import SVG first.");
            return;
        }
        double tolerance = options.tolerance();
        boolean reorder = options.reorder();
        double mergeTolerance = options.mergeTolerance();

        snapshotForUndo();
        OptimizeStage.Stats before = OptimizeStage.computeStats(documentSession.currentOutput());
        updateDocument(OptimizeStage.optimize(documentSession.currentOutput(), tolerance, reorder, mergeTolerance));
        OptimizeStage.Stats after = OptimizeStage.computeStats(documentSession.currentOutput());

        visPanel.loadPathsPreservingOverlay(documentSession.currentOutput());
        refreshLayerSelector();
        overlayControls.refreshPosition();
        refreshTimeEstimate();

        double travelSavedPct = before.travelDistanceMm() <= 0 ? 0
                : 100.0 * (before.travelDistanceMm() - after.travelDistanceMm()) / before.travelDistanceMm();
        log(String.format(
                "Optimized: travel %.1fmm -> %.1fmm (%.1f%% saved), points %d -> %d, strokes %d -> %d",
                before.travelDistanceMm(), after.travelDistanceMm(), travelSavedPct,
                before.pointCount(), after.pointCount(),
                before.strokeCount(), after.strokeCount()));
    }

    private void onOpenSettings() { dialogs.settings(); }

    private void saveSettings(GantryConfig updated) {
        config = updated;
        try { ConfigStore.save(config, configFile); }
        catch (IOException ex) { log("WARNING: Failed to save config: " + ex.getMessage()); }
        applyConfigToVis();
    }

    private void onConnectToggle() { connections.toggle(config, plotting); }

    private void showConnectionState(boolean connecting, boolean connected, boolean failed) {
        connectBtn.setEnabled(!connecting);
        connectBtn.setText(connected ? "Disconnect" : "Connect");
        if (connectMenuItem != null) connectMenuItem.setText(connected ? "Disconnect" : "Connect");
        setConnectButtonColor(!connected);
        setConnectionRequiredControlsEnabled(connected);
        statusLabel.setText(connecting ? "Connecting..." : failed ? "Connection failed" : connected ? "Connected" : "Disconnected");
    }

    private void onSpeedChanged(int percent) {
        jogPanel.setSpeed(percent); visPanel.setSpeedPercent(percent); speedPercent = percent;
        if (plotClockTimer == null) refreshTimeEstimate(); else updateTimeLabelDuringPlot();
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
        plotControls.setConnected(enabled);
        jogPanel.setConnected(enabled);
        rawCommands.setConnected(enabled);
        updateReplotMenuItem();
    }

    private void updateReplotMenuItem() {
        if (replotMenuItem != null) {
            replotMenuItem.setEnabled(plotJobController.canReplot() && !plotting && plotJobController.isConnected()
                    && documentSession.currentOutput() != null);
        }
    }

    /** Called whenever a new drawing replaces the current one; invalidates the stored re-plot offer. */
    private void resetReplot() {
        plotJobController.resetReplot();
        updateReplotMenuItem();
    }

    private void onStartPlot() {
        if (plotJobController.isPlotting()) {
            log("Plot start ignored: a plot is already active.");
            return;
        }
        if (documentSession.currentOutput() == null) {
            log("ERROR: Load a commands file first.");
            return;
        }
        PlotterBackend backend = plotJobController.backend();
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
        service.setCommandedPositionCallback((x, y) -> {
            onJogPositionReport(x, y);
            SwingUtilities.invokeLater(() -> visPanel.updatePosition(x, y));
        });
        service.setLayerGate(layer -> {
            log("Layer '" + layer.id() + "' ready. Click Confirm Layer to start.");
            confirmGate.acquire();
        });
        int totalLayersCount = toPlot.layers().size();
        service.setLayerStartedCallback(layer -> plotProgress.layerStarted());
        service.setProgressCallback((done, total) -> {
            plotProgress.update(done, total);
            int pct = plotProgress.percent();
            SwingUtilities.invokeLater(() -> {
                plotControls.setProgress(pct);
            });
        });

        confirmGate.drainPermits();
        plotProgress.setEstimate(TimeEstimator.estimate(toPlot, config.gcode, config.stations));
        plotProgress.start(totalLayersCount, System.currentTimeMillis());
        setPlottingState(true);
        plotClockTimer = new javax.swing.Timer(500, e -> updateTimeLabelDuringPlot());
        plotClockTimer.start();

        plotJobController.startPlot(service, toPlot, (completed, failure) -> {
            if (completed) {
                double actualSec = plotProgress.elapsedSeconds(System.currentTimeMillis());
                TimeEstimator.PlotEstimate est = plotProgress.estimate();
                if (est != null) {
                    String estFmt = TimeEstimator.format(est.totalSeconds() * (100.0 / speedPercent));
                    log("--- Plot finished: actual " + TimeEstimator.format(actualSec)
                            + " / estimated " + estFmt + " ---");
                } else {
                    log("--- Plot finished: " + TimeEstimator.format(actualSec) + " ---");
                }
                SwingUtilities.invokeLater(this::updateReplotMenuItem);
            } else if (failure != null) {
                log("ERROR: Plot failed: " + failure.getMessage());
            }
            SwingUtilities.invokeLater(() -> setPlottingState(false));
        });
    }

    private void onStopPlot() {
        plotJobController.cancelPlot();
        confirmGate.release();
        // Cancelling only stops further commands from being sent; the backend may already have
        // queued motion in flight (e.g. GRBL's planner buffer), so halt it immediately too.
        runOnBackend(PlotterBackend::haltMotion);
        resetGuidance();
    }

    private void onPauseToggle() {
        if (!plotJobController.isPlotting()) {
            return;
        }
        if (plotJobController.togglePause()) {
            plotControls.setPaused(true);
        } else {
            plotControls.setPaused(false);
        }
        refreshGuidance();
    }

    private void setPlottingState(boolean plotting) {
        this.plotting = plotting;
        plotControls.setConnected(plotJobController.isConnected());
        plotControls.setPlotting(plotting);
        jogPanel.setPlotting(plotting);
        overlayControls.setPlotting(plotting);
        rawCommands.setPlotting(plotting);
        // Jog/pen/edit controls are unsafe to use mid-plot — disable them while plotting.
        for (JComponent c : plotDisabledControls) {
            c.setEnabled(!plotting);
        }
        // The per-layer checkboxes are rebuilt on every load, so they aren't in the list above;
        // toggle them here so the layer selection can't change mid-plot.
        if (!plotting) {
            plotControls.setPaused(false);
            if (plotClockTimer != null) {
                plotClockTimer.stop();
                plotClockTimer = null;
            }
            plotProgress.resetProgress();
            refreshTimeEstimate();
        }
        updateReplotMenuItem();
        refreshGuidance();
    }

    private void onJogPositionReport(double x, double y) { jogPanel.reportPosition(x, y); }
    private void onHome() { jogPanel.home(); }
    private void jog(int dx, int dy) { jogPanel.jog(dx, dy); }

    private void runOnBackend(java.util.function.Consumer<PlotterBackend> action) {
        if (!plotJobController.isConnected()) {
            log("ERROR: Not connected.");
            return;
        }
        new Thread(() -> plotJobController.withBackend(action), "backend-action").start();
    }

    /**
     * The output that will actually be plotted/exported: the session document, narrowed to the
     * layers ticked in the layer checklist. This is what lets the operator plot a chosen subset of
     * layers (e.g. one pen's worth of strokes), swap the pen, then plot the next.
     */
    private ProcessorOutput selectedOutput() {
        documentSession.selectLayers(selectedLayerIndices());
        return documentSession.selectedOutput();
    }

    /** Applies overlay baking and the configured multipass count to the selected output for plotting/export. */
    private ProcessorOutput preparePlotOutput() {
        selectedOutput(); // Synchronize the session's selection with the layer checkboxes.
        int passes = plotControls.passes();
        return documentSession.prepareOutput(
                output -> visPanel.hasOverlayTransform() ? bakeOverlay(output) : output,
                passes);
    }

    private void onExportGcode() { gcodeFiles.export(); }
    private void onReplayGcode() { gcodeFiles.replay(); }

    /**
     * Returns a copy of {@code output} with the interactive overlay transform
     * (drag/resize/rotate/mirror) baked into raw content coordinates. Does not mutate the
     * input, so the session document stays in its original (un-baked) frame and can be
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

    private void runBusy(String description, Callable<ProcessorOutput> task,
            Consumer<ProcessorOutput> onSuccess) {
        busyTasks.run(description, task, onSuccess);
    }

    /**
     * Drops the loaded drawing (triggered from the canvas "Remove Drawing" context menu). The
     * visualization has already cleared itself; here we discard the backing output and reset the
     * dependent state so a subsequent plot/export has nothing stale to act on.
     */
    private void onRemoveDrawing() {
        if (documentSession.currentOutput() == null) return;
        documentEditor.clear();
        resetReplot();
        refreshLayerSelector(); overlayControls.refreshPosition(); refreshTimeEstimate(); refreshGuidance();
        log("Removed the loaded drawing.");
    }

    private void onHatchRegion(java.awt.geom.Path2D region, int layerIndex) { documentEditor.hatch(region, layerIndex); }
    private void onDeleteStroke(int commandId) { documentEditor.delete(commandId); }
    private void onAddLine(double x1, double y1, double x2, double y2, int layerIndex) {
        documentEditor.addLine(x1, y1, x2, y2, layerIndex);
    }
    private void onMoveStroke(int commandId, double[][] points) { documentEditor.move(commandId, points); }
    private void onDuplicateStroke(int commandId) { documentEditor.duplicate(commandId); }
    private void onClearHatchRegion(java.awt.geom.Path2D region) { documentEditor.clearHatch(region); }
    private void onHatchStyleDialog() { documentEditor.chooseHatchStyle(); }
    private void snapshotForUndo() { documentEditor.snapshot(); }
    private void onUndo() { documentEditor.undo(); }

    private void onDocumentEdited() {
        visPanel.loadPathsPreservingOverlay(documentSession.currentOutput());
        refreshDocumentUi();
    }

    private void refreshDocumentUi() {
        refreshLayerSelector(); overlayControls.refreshPosition(); refreshTimeEstimate(); refreshGuidance();
    }

    private void setUndoAvailable(boolean available) {
        if (undoMenuItem != null) undoMenuItem.setEnabled(available);
    }

    /** Starts a new document history. */
    private void replaceDocument(ProcessorOutput output) {
        documentEditor.replace(output);
    }

    /** Updates an edited document without discarding its source provenance or undo snapshot. */
    private void updateDocument(ProcessorOutput output) {
        documentEditor.update(output);
    }

    private void clearDocument() {
        documentEditor.clear();
    }
}
