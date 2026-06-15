package org.trostheide.watercolorprocessor.gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class SettingsPanel extends JPanel {

    // General Settings
    private final JTextField pythonPathField;
    private final JSpinner speedDownSpinner;
    private final JSpinner speedUpSpinner;
    private JSpinner zUpSpinner;
    private JSpinner zDownSpinner;
    private final JComboBox<String> machineOriginCombo;
    private final JCheckBox swapXYCheckBox;
    private final JCheckBox invertJogYCheckBox;
    private JRadioButton portraitRadio;
    private JRadioButton landscapeRadio;
    private final JComboBox<String> modelComboBox;
    private final JCheckBox mockCheckBox;
    private final JComboBox<String> canvasAlignmentCombo;
    private final JComboBox<String> viewRotationCombo;
    private final JSpinner paddingXSpinner;
    private final JSpinner paddingYSpinner;

    // Backend Selection
    private final JComboBox<String> backendCombo;
    private final JPanel axidrawSettingsPanel;
    private final JPanel gcodeSettingsPanel;

    // G-code specific fields
    private final JTextField serialPortField;
    private final JComboBox<String> baudRateCombo;
    private final JComboBox<String> penModeCombo;
    private final JSpinner servoPinSpinner;
    private final JSpinner feedRateDrawSpinner;
    private final JSpinner feedRateTravelSpinner;
    private final JSpinner gcodeServoUpSpinner;
    private final JSpinner gcodeServoDownSpinner;
    private final JSpinner zUpPosSpinner;
    private final JSpinner zDownPosSpinner;
    private final JSpinner gcodeWidthSpinner;
    private final JSpinner gcodeHeightSpinner;

    // Station Editor Components
    private final JTable stationTable;
    private final DefaultTableModel tableModel;

    private final JTextField idField;
    private final JSpinner xSpinner;
    private final JSpinner ySpinner;
    private final JSpinner zSpinner;
    private final JComboBox<String> behaviorCombo;

    // In-memory store
    private final Map<String, StationConfig> stations = new LinkedHashMap<>();
    private final File legacyStationFile = new File("stations.json");
    private File currentConfigFile = new File("config.json");
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    // Buttons for mass enable/disable
    private final JButton addBtn;
    private final JButton removeBtn;
    private final JButton saveFileBtn;
    private final JButton loadFileBtn;
    private final JLabel activeConfigLabel;

    private Runnable visualChangeListener;
    private Runnable hardwareChangeListener;

    public void addVisualChangeListener(Runnable listener) {
        this.visualChangeListener = listener;
    }

    public void addHardwareChangeListener(Runnable listener) {
        this.hardwareChangeListener = listener;
    }

    private void fireVisualChange() {
        if (visualChangeListener != null)
            visualChangeListener.run();
    }

    private void fireHardwareChange() {
        if (hardwareChangeListener != null)
            hardwareChangeListener.run();
    }

    public SettingsPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        // === Main content in a vertical box ===
        JPanel mainContent = new JPanel();
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));

        // --- Section 1: Hardware Settings ---
        JPanel hardwarePanel = new JPanel(new BorderLayout(0, 4));
        hardwarePanel.setBorder(createSection("Hardware"));

        // Backend selector row
        JPanel backendRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        backendRow.setOpaque(false);
        backendRow.add(label("Backend"));
        backendCombo = new JComboBox<>(new String[] { "AxiDraw", "G-code (GRBL)" });
        backendCombo.setToolTipText("Select your plotter type");
        backendRow.add(backendCombo);

        landscapeRadio = new JRadioButton("Landscape");
        portraitRadio = new JRadioButton("Portrait");
        ButtonGroup orientationGroup = new ButtonGroup();
        orientationGroup.add(landscapeRadio);
        orientationGroup.add(portraitRadio);
        portraitRadio.setSelected(true);
        landscapeRadio.addActionListener(e -> fireVisualChange());
        portraitRadio.addActionListener(e -> fireVisualChange());
        backendRow.add(Box.createHorizontalStrut(12));
        backendRow.add(label("Orientation"));
        backendRow.add(portraitRadio);
        backendRow.add(landscapeRadio);

        JPanel topHardware = new JPanel();
        topHardware.setLayout(new BoxLayout(topHardware, BoxLayout.Y_AXIS));
        topHardware.setOpaque(false);
        topHardware.add(backendRow);

        JPanel pythonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        pythonRow.setOpaque(false);
        pythonRow.add(label("Python Path"));
        String defaultPython = System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";
        pythonPathField = new JTextField(defaultPython, 20);
        pythonPathField.setToolTipText("Path to the Python interpreter (python or python3)");
        pythonRow.add(pythonPathField);
        topHardware.add(pythonRow);

        hardwarePanel.add(topHardware, BorderLayout.NORTH);

        // -- AxiDraw settings (shown when backend=AxiDraw) --
        axidrawSettingsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 8, 4, 8);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridx = 0; g.gridy = 0; g.weightx = 0;
        axidrawSettingsPanel.add(label("Plotter Size"), g);
        g.gridx = 1; g.weightx = 0.3;
        modelComboBox = new JComboBox<>(new String[] { "Standard (A4 / V3)", "Large (A3 / V3 XL)" });
        modelComboBox.setSelectedIndex(1);
        modelComboBox.setToolTipText("Select your AxiDraw model");
        axidrawSettingsPanel.add(modelComboBox, g);

        g.gridx = 2; g.weightx = 0;
        axidrawSettingsPanel.add(label("Draw Speed (%)"), g);
        g.gridx = 3; g.weightx = 0.3;
        speedDownSpinner = new JSpinner(new SpinnerNumberModel(25, 1, 100, 1));
        speedDownSpinner.setToolTipText("Pen-down drawing speed as percentage of maximum");
        axidrawSettingsPanel.add(speedDownSpinner, g);

        g.gridx = 0; g.gridy = 1; g.weightx = 0;
        axidrawSettingsPanel.add(label("Travel Speed (%)"), g);
        g.gridx = 1; g.weightx = 0.3;
        speedUpSpinner = new JSpinner(new SpinnerNumberModel(75, 1, 100, 1));
        speedUpSpinner.setToolTipText("Pen-up travel speed as percentage of maximum");
        axidrawSettingsPanel.add(speedUpSpinner, g);

        g.gridx = 2; g.weightx = 0;
        axidrawSettingsPanel.add(label("Pen Up (%)"), g);
        g.gridx = 3; g.weightx = 0.3;
        zUpSpinner = new JSpinner(new SpinnerNumberModel(60, 0, 100, 1));
        zUpSpinner.setToolTipText("Servo position when pen is raised");
        axidrawSettingsPanel.add(zUpSpinner, g);

        g.gridx = 0; g.gridy = 2; g.weightx = 0;
        axidrawSettingsPanel.add(label("Pen Down (%)"), g);
        g.gridx = 1; g.weightx = 0.3;
        zDownSpinner = new JSpinner(new SpinnerNumberModel(30, 0, 100, 1));
        zDownSpinner.setToolTipText("Servo position when pen is lowered");
        axidrawSettingsPanel.add(zDownSpinner, g);

        // -- G-code settings (shown when backend=G-code) --
        gcodeSettingsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 8, 4, 8);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        gc.gridx = 0; gc.gridy = 0; gc.weightx = 0;
        gcodeSettingsPanel.add(label("Serial Port"), gc);
        gc.gridx = 1; gc.weightx = 0.3;
        String defaultPort = System.getProperty("os.name").toLowerCase().contains("win") ? "COM3" : "/dev/ttyUSB0";
        serialPortField = new JTextField(defaultPort, 12);
        serialPortField.setToolTipText("Serial port for the GRBL controller");
        gcodeSettingsPanel.add(serialPortField, gc);

        gc.gridx = 2; gc.weightx = 0;
        gcodeSettingsPanel.add(label("Baud Rate"), gc);
        gc.gridx = 3; gc.weightx = 0.3;
        baudRateCombo = new JComboBox<>(new String[] { "9600", "115200", "250000" });
        baudRateCombo.setSelectedItem("115200");
        gcodeSettingsPanel.add(baudRateCombo, gc);

        gc.gridx = 0; gc.gridy = 1; gc.weightx = 0;
        gcodeSettingsPanel.add(label("Pen Mode"), gc);
        gc.gridx = 1; gc.weightx = 0.3;
        penModeCombo = new JComboBox<>(new String[] { "Servo (M280)", "Z-Axis", "M3/M5" });
        penModeCombo.setToolTipText("How pen up/down is controlled");
        gcodeSettingsPanel.add(penModeCombo, gc);

        gc.gridx = 2; gc.weightx = 0;
        gcodeSettingsPanel.add(label("Servo Pin"), gc);
        gc.gridx = 3; gc.weightx = 0.3;
        servoPinSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 10, 1));
        gcodeSettingsPanel.add(servoPinSpinner, gc);

        gc.gridx = 0; gc.gridy = 2; gc.weightx = 0;
        gcodeSettingsPanel.add(label("Draw Feed (mm/min)"), gc);
        gc.gridx = 1; gc.weightx = 0.3;
        feedRateDrawSpinner = new JSpinner(new SpinnerNumberModel(1000, 50, 10000, 50));
        gcodeSettingsPanel.add(feedRateDrawSpinner, gc);

        gc.gridx = 2; gc.weightx = 0;
        gcodeSettingsPanel.add(label("Travel Feed (mm/min)"), gc);
        gc.gridx = 3; gc.weightx = 0.3;
        feedRateTravelSpinner = new JSpinner(new SpinnerNumberModel(3000, 50, 10000, 100));
        gcodeSettingsPanel.add(feedRateTravelSpinner, gc);

        gc.gridx = 0; gc.gridy = 3; gc.weightx = 0;
        gcodeSettingsPanel.add(label("Servo/Z Up"), gc);
        gc.gridx = 1; gc.weightx = 0.3;
        gcodeServoUpSpinner = new JSpinner(new SpinnerNumberModel(60, 0, 180, 1));
        gcodeServoUpSpinner.setToolTipText("Servo angle or Z height for pen up");
        gcodeSettingsPanel.add(gcodeServoUpSpinner, gc);

        gc.gridx = 2; gc.weightx = 0;
        gcodeSettingsPanel.add(label("Servo/Z Down"), gc);
        gc.gridx = 3; gc.weightx = 0.3;
        gcodeServoDownSpinner = new JSpinner(new SpinnerNumberModel(30, 0, 180, 1));
        gcodeServoDownSpinner.setToolTipText("Servo angle or Z height for pen down");
        gcodeSettingsPanel.add(gcodeServoDownSpinner, gc);

        gc.gridx = 0; gc.gridy = 4; gc.weightx = 0;
        gcodeSettingsPanel.add(label("Z Up (mm)"), gc);
        gc.gridx = 1; gc.weightx = 0.3;
        zUpPosSpinner = new JSpinner(new SpinnerNumberModel(5.0, 0.0, 100.0, 0.5));
        gcodeSettingsPanel.add(zUpPosSpinner, gc);

        gc.gridx = 2; gc.weightx = 0;
        gcodeSettingsPanel.add(label("Z Down (mm)"), gc);
        gc.gridx = 3; gc.weightx = 0.3;
        zDownPosSpinner = new JSpinner(new SpinnerNumberModel(0.0, -10.0, 100.0, 0.5));
        gcodeSettingsPanel.add(zDownPosSpinner, gc);

        gc.gridx = 0; gc.gridy = 5; gc.weightx = 0;
        gcodeSettingsPanel.add(label("Machine Width (mm)"), gc);
        gc.gridx = 1; gc.weightx = 0.3;
        gcodeWidthSpinner = new JSpinner(new SpinnerNumberModel(300.0, 10.0, 2000.0, 10.0));
        gcodeWidthSpinner.addChangeListener(e -> { fireVisualChange(); updateStationSpinnerLimits(); });
        gcodeSettingsPanel.add(gcodeWidthSpinner, gc);

        gc.gridx = 2; gc.weightx = 0;
        gcodeSettingsPanel.add(label("Machine Height (mm)"), gc);
        gc.gridx = 3; gc.weightx = 0.3;
        gcodeHeightSpinner = new JSpinner(new SpinnerNumberModel(200.0, 10.0, 2000.0, 10.0));
        gcodeHeightSpinner.addChangeListener(e -> { fireVisualChange(); updateStationSpinnerLimits(); });
        gcodeSettingsPanel.add(gcodeHeightSpinner, gc);

        // CardLayout to swap AxiDraw / G-code panels
        JPanel cardPanel = new JPanel(new CardLayout());
        cardPanel.add(axidrawSettingsPanel, "axidraw");
        cardPanel.add(gcodeSettingsPanel, "gcode");
        hardwarePanel.add(cardPanel, BorderLayout.CENTER);

        backendCombo.addActionListener(e -> {
            CardLayout cl = (CardLayout) cardPanel.getLayout();
            cl.show(cardPanel, backendCombo.getSelectedIndex() == 0 ? "axidraw" : "gcode");
            fireVisualChange();
            updateStationSpinnerLimits();
        });

        modelComboBox.addActionListener(e -> updateStationSpinnerLimits());

        mainContent.add(hardwarePanel);
        mainContent.add(Box.createVerticalStrut(4));

        // --- Section 2: Coordinate Mapping ---
        JPanel coordPanel = new JPanel(new GridBagLayout());
        coordPanel.setBorder(createSection("Coordinate Mapping"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 8, 4, 8);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Machine Origin + Mock + Swap
        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        coordPanel.add(label("Machine Origin"), c);

        c.gridx = 1; c.weightx = 0.3;
        machineOriginCombo = new JComboBox<>(new String[] {
                "Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right"
        });
        machineOriginCombo.setSelectedItem("Top-Right");
        machineOriginCombo.setToolTipText("Corner where the plotter's home position (0,0) is located");
        machineOriginCombo.addActionListener(e -> {
            fireHardwareChange();
            fireVisualChange();
        });
        coordPanel.add(machineOriginCombo, c);

        c.gridx = 2; c.weightx = 0; c.gridwidth = 2;
        JPanel flagsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        flagsPanel.setOpaque(false);

        mockCheckBox = new JCheckBox("Mock Mode", false);
        mockCheckBox.setToolTipText("Simulate plotter without hardware");
        flagsPanel.add(mockCheckBox);

        swapXYCheckBox = new JCheckBox("Swap X/Y", false);
        swapXYCheckBox.setToolTipText("Manual axis swap for unusual hardware wiring. Portrait mode auto-swaps; this is an additional toggle on top.");
        swapXYCheckBox.addActionListener(e -> {
            fireHardwareChange();
            fireVisualChange();
        });
        flagsPanel.add(swapXYCheckBox);

        invertJogYCheckBox = new JCheckBox("Flip Y", false);
        invertJogYCheckBox.setToolTipText("Flip Y axis direction for machines where Y+ goes up (e.g. GRBL/CNC)");
        invertJogYCheckBox.addActionListener(e -> {
            fireHardwareChange();
            fireVisualChange();
        });
        flagsPanel.add(invertJogYCheckBox);

        coordPanel.add(flagsPanel, c);

        // Row 1: Alignment + Rotation
        c.gridx = 0; c.gridy = 1; c.weightx = 0; c.gridwidth = 1;
        coordPanel.add(label("Canvas Align"), c);

        c.gridx = 1; c.weightx = 0.3;
        canvasAlignmentCombo = new JComboBox<>(new String[] {
                "Top Left", "Top Right", "Bottom Left", "Bottom Right", "Center"
        });
        canvasAlignmentCombo.setSelectedItem("Top Right");
        canvasAlignmentCombo.setToolTipText("Align the drawing to a corner of the machine bed");
        canvasAlignmentCombo.addActionListener(e -> fireVisualChange());
        coordPanel.add(canvasAlignmentCombo, c);

        c.gridx = 2; c.weightx = 0;
        coordPanel.add(label("Rotation"), c);

        c.gridx = 3; c.weightx = 0.3;
        viewRotationCombo = new JComboBox<>(new String[] { "0", "90", "180", "270" });
        viewRotationCombo.setToolTipText("Rotate drawing data (counter-clockwise degrees)");
        viewRotationCombo.addActionListener(e -> fireVisualChange());
        coordPanel.add(viewRotationCombo, c);

        // Row 2: Padding
        c.gridx = 0; c.gridy = 2; c.weightx = 0;
        coordPanel.add(label("Padding X (mm)"), c);

        c.gridx = 1; c.weightx = 0.3;
        paddingXSpinner = new JSpinner(new SpinnerNumberModel(0.0, -100.0, 100.0, 1.0));
        paddingXSpinner.setToolTipText("Horizontal offset from alignment edge");
        paddingXSpinner.addChangeListener(e -> fireVisualChange());
        coordPanel.add(paddingXSpinner, c);

        c.gridx = 2; c.weightx = 0;
        coordPanel.add(label("Padding Y (mm)"), c);

        c.gridx = 3; c.weightx = 0.3;
        paddingYSpinner = new JSpinner(new SpinnerNumberModel(0.0, -100.0, 100.0, 1.0));
        paddingYSpinner.setToolTipText("Vertical offset from alignment edge");
        paddingYSpinner.addChangeListener(e -> fireVisualChange());
        coordPanel.add(paddingYSpinner, c);

        mainContent.add(coordPanel);
        mainContent.add(Box.createVerticalStrut(4));

        // --- Section 3: Station Management ---
        JPanel stationPanel = new JPanel(new BorderLayout(0, 6));
        stationPanel.setBorder(createSection("Paint Stations"));

        tableModel = new DefaultTableModel(new String[] { "ID", "X (mm)", "Y (mm)", "Z Down", "Behavior" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        stationTable = new JTable(tableModel);
        stationTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stationTable.setRowHeight(24);
        stationTable.getSelectionModel().addListSelectionListener(e -> loadSelection());

        JScrollPane tableScroll = new JScrollPane(stationTable);
        stationPanel.add(tableScroll, BorderLayout.CENTER);

        // Station editor form
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints fg = new GridBagConstraints();
        fg.insets = new Insets(3, 6, 3, 6);
        fg.fill = GridBagConstraints.HORIZONTAL;

        fg.gridx = 0; fg.gridy = 0; fg.weightx = 0;
        formPanel.add(label("ID"), fg);
        fg.gridx = 1; fg.weightx = 0.5;
        idField = new JTextField(8);
        formPanel.add(idField, fg);

        fg.gridx = 2; fg.weightx = 0;
        formPanel.add(label("X"), fg);
        fg.gridx = 3; fg.weightx = 0.25;
        xSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 2000.0, 1.0));
        formPanel.add(xSpinner, fg);

        fg.gridx = 4; fg.weightx = 0;
        formPanel.add(label("Y"), fg);
        fg.gridx = 5; fg.weightx = 0.25;
        ySpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 2000.0, 1.0));
        formPanel.add(ySpinner, fg);

        fg.gridx = 0; fg.gridy = 1; fg.weightx = 0;
        formPanel.add(label("Z Down (%)"), fg);
        fg.gridx = 1; fg.weightx = 0.25;
        zSpinner = new JSpinner(new SpinnerNumberModel(30, 0, 100, 1));
        formPanel.add(zSpinner, fg);

        fg.gridx = 2; fg.weightx = 0;
        formPanel.add(label("Behavior"), fg);
        fg.gridx = 3; fg.weightx = 0.5; fg.gridwidth = 3;
        behaviorCombo = new JComboBox<>(new String[] { "simple_dip", "dip_swirl" });
        formPanel.add(behaviorCombo, fg);

        fg.gridx = 0; fg.gridy = 2; fg.gridwidth = 3; fg.weightx = 0;
        fg.fill = GridBagConstraints.NONE;
        fg.anchor = GridBagConstraints.CENTER;
        JPanel stationBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        addBtn = new JButton("Add / Update");
        addBtn.putClientProperty("JButton.buttonType", "roundRect");
        addBtn.addActionListener(e -> saveStation());
        removeBtn = new JButton("Remove");
        removeBtn.putClientProperty("JButton.buttonType", "roundRect");
        removeBtn.addActionListener(e -> removeStation());
        stationBtnPanel.add(addBtn);
        stationBtnPanel.add(removeBtn);
        formPanel.add(stationBtnPanel, fg);

        fg.gridx = 3; fg.gridwidth = 3;
        // empty to balance

        stationPanel.add(formPanel, BorderLayout.SOUTH);

        mainContent.add(stationPanel);

        add(mainContent, BorderLayout.CENTER);

        // --- Bottom: Config File Controls ---
        JPanel configBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));

        activeConfigLabel = new JLabel("Config: " + currentConfigFile.getName());
        activeConfigLabel.setFont(activeConfigLabel.getFont().deriveFont(Font.ITALIC, 11f));
        configBar.add(activeConfigLabel);

        saveFileBtn = new JButton("Save Config As...");
        saveFileBtn.putClientProperty("JButton.buttonType", "roundRect");
        saveFileBtn.addActionListener(e -> saveAs());
        configBar.add(saveFileBtn);

        loadFileBtn = new JButton("Load Config...");
        loadFileBtn.putClientProperty("JButton.buttonType", "roundRect");
        loadFileBtn.addActionListener(e -> loadFrom());
        configBar.add(loadFileBtn);

        add(configBar, BorderLayout.SOUTH);

        // Initial Load
        loadConfig();
        updateStationSpinnerLimits();

        javax.swing.event.ChangeListener valueChange = e -> fireHardwareChange();
        speedDownSpinner.addChangeListener(valueChange);
        speedUpSpinner.addChangeListener(valueChange);
        zUpSpinner.addChangeListener(valueChange);
        zDownSpinner.addChangeListener(valueChange);
        feedRateDrawSpinner.addChangeListener(valueChange);
        feedRateTravelSpinner.addChangeListener(valueChange);
        gcodeServoUpSpinner.addChangeListener(valueChange);
        gcodeServoDownSpinner.addChangeListener(valueChange);
        servoPinSpinner.addChangeListener(valueChange);
        zUpPosSpinner.addChangeListener(valueChange);
        zDownPosSpinner.addChangeListener(valueChange);
        penModeCombo.addActionListener(e -> fireHardwareChange());
        baudRateCombo.addActionListener(e -> fireHardwareChange());
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(12f));
        return l;
    }

    private TitledBorder createSection(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD, 12f));
        return border;
    }

    private void updateStationSpinnerLimits() {
        double maxX = getMachineWidth();
        double maxY = getMachineHeight();
        ((SpinnerNumberModel) xSpinner.getModel()).setMaximum(maxX);
        ((SpinnerNumberModel) ySpinner.getModel()).setMaximum(maxY);
        if ((Double) xSpinner.getValue() > maxX) xSpinner.setValue(maxX);
        if ((Double) ySpinner.getValue() > maxY) ySpinner.setValue(maxY);
    }

    // --- Public Accessors for PlotterPanel ---
    public String getPythonPath() { return pythonPathField.getText().trim(); }
    public String getBackend() { return backendCombo.getSelectedIndex() == 0 ? "axidraw" : "gcode"; }
    public String getSerialPort() { return serialPortField.getText().trim(); }
    public int getDrawSpeed() { return (Integer) speedDownSpinner.getValue(); }
    public int getTravelSpeed() { return (Integer) speedUpSpinner.getValue(); }
    public int getPlotterModelIndex() { return modelComboBox.getSelectedIndex(); }
    public int getPenUpHeight() { return (Integer) zUpSpinner.getValue(); }
    public int getPenDownHeight() { return (Integer) zDownSpinner.getValue(); }
    public boolean isMockMode() { return mockCheckBox.isSelected(); }
    public String getMachineOrigin() { return (String) machineOriginCombo.getSelectedItem(); }
    public boolean isInvertX() { return getMachineOrigin().contains("Right"); }
    public boolean isInvertY() { return getMachineOrigin().contains("Bottom"); }
    public boolean isOriginRight() { return getMachineOrigin().contains("Right"); }
    public boolean isOriginBottom() { return getMachineOrigin().contains("Bottom"); }
    public boolean isSwapXY() { return swapXYCheckBox.isSelected(); }
    public boolean isFlipY() { return invertJogYCheckBox.isSelected(); }
    public boolean isPortrait() { return portraitRadio != null && portraitRadio.isSelected(); }
    public String getCanvasAlignment() { return (String) canvasAlignmentCombo.getSelectedItem(); }
    public double getPaddingX() { return (Double) paddingXSpinner.getValue(); }
    public double getPaddingY() { return (Double) paddingYSpinner.getValue(); }

    public String getOrientation() {
        if (portraitRadio != null && portraitRadio.isSelected())
            return "Portrait";
        return "Landscape";
    }

    public int getViewRotation() {
        try {
            return Integer.parseInt((String) viewRotationCombo.getSelectedItem());
        } catch (Exception e) {
            return 0;
        }
    }

    public double getMachineWidth() {
        if ("gcode".equals(getBackend())) {
            return (Double) gcodeWidthSpinner.getValue();
        }
        // AxiDraw machine bed dimensions (must match driver.py)
        // V3 (A4): 300mm x 215mm, V3 XL (A3): 430mm x 297mm
        return getPlotterModelIndex() == 0 ? 300.0 : 430.0;
    }

    public double getMachineHeight() {
        if ("gcode".equals(getBackend())) {
            return (Double) gcodeHeightSpinner.getValue();
        }
        return getPlotterModelIndex() == 0 ? 215.0 : 297.0;
    }

    public void setSettingsEnabled(boolean enabled) {
        pythonPathField.setEnabled(enabled);
        backendCombo.setEnabled(enabled);
        modelComboBox.setEnabled(enabled);
        speedDownSpinner.setEnabled(enabled);
        speedUpSpinner.setEnabled(enabled);
        zUpSpinner.setEnabled(enabled);
        zDownSpinner.setEnabled(enabled);
        mockCheckBox.setEnabled(enabled);
        machineOriginCombo.setEnabled(enabled);
        swapXYCheckBox.setEnabled(enabled);
        invertJogYCheckBox.setEnabled(enabled);
        serialPortField.setEnabled(enabled);
        baudRateCombo.setEnabled(enabled);
        penModeCombo.setEnabled(enabled);
        servoPinSpinner.setEnabled(enabled);
        feedRateDrawSpinner.setEnabled(enabled);
        feedRateTravelSpinner.setEnabled(enabled);
        gcodeServoUpSpinner.setEnabled(enabled);
        gcodeServoDownSpinner.setEnabled(enabled);
        zUpPosSpinner.setEnabled(enabled);
        zDownPosSpinner.setEnabled(enabled);
        gcodeWidthSpinner.setEnabled(enabled);
        gcodeHeightSpinner.setEnabled(enabled);
        stationTable.setEnabled(enabled);
        idField.setEnabled(enabled);
        xSpinner.setEnabled(enabled);
        ySpinner.setEnabled(enabled);
        zSpinner.setEnabled(enabled);
        behaviorCombo.setEnabled(enabled);
        addBtn.setEnabled(enabled);
        removeBtn.setEnabled(enabled);
        saveFileBtn.setEnabled(enabled);
        loadFileBtn.setEnabled(enabled);
    }

    public File getCurrentConfigFile() { return currentConfigFile; }
    public Map<String, StationConfig> getStations() { return stations; }

    // --- Internal Logic ---

    private void loadSelection() {
        int row = stationTable.getSelectedRow();
        if (row >= 0) {
            String id = (String) tableModel.getValueAt(row, 0);
            StationConfig cfg = stations.get(id);
            if (cfg != null) {
                idField.setText(id);
                xSpinner.setValue(cfg.x());
                ySpinner.setValue(cfg.y());
                zSpinner.setValue(cfg.z_down());
                behaviorCombo.setSelectedItem(cfg.behavior());
            }
        }
    }

    private void saveStation() {
        String id = idField.getText().trim();
        if (id.isEmpty()) return;

        StationConfig cfg = new StationConfig(
                (Double) xSpinner.getValue(),
                (Double) ySpinner.getValue(),
                (Integer) zSpinner.getValue(),
                (String) behaviorCombo.getSelectedItem());

        stations.put(id, cfg);
        refreshTable();
        fireVisualChange();
    }

    private void removeStation() {
        String id = idField.getText().trim();
        if (stations.containsKey(id)) {
            stations.remove(id);
            refreshTable();
            idField.setText("");
            fireVisualChange();
        }
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        stations.forEach((id, cfg) -> {
            tableModel.addRow(new Object[] { id, cfg.x(), cfg.y(), cfg.z_down(), cfg.behavior() });
        });
    }

    private void loadConfig() {
        if (currentConfigFile.exists()) {
            try {
                AppConfig config = mapper.readValue(currentConfigFile, AppConfig.class);

                GeneralSettings gen = config.general();
                if (gen != null) {
                    modelComboBox.setSelectedIndex(gen.modelIndex);
                    mockCheckBox.setSelected(gen.mock);
                    // Portrait mode now auto-swaps; old configs had manual swap=true for portrait.
                    // Reset swap to false if loading a portrait config from before auto-swap was added.
                    boolean migratedSwap = gen.swapXY;
                    if ("Portrait".equals(gen.orientation) && gen.swapXY && gen.machineOrigin != null
                            && gen.machineOrigin.contains("Right")) {
                        migratedSwap = false;
                    }
                    swapXYCheckBox.setSelected(migratedSwap);
                    invertJogYCheckBox.setSelected(gen.invertJogY);

                    if (gen.machineOrigin != null) {
                        machineOriginCombo.setSelectedItem(gen.machineOrigin);
                    } else {
                        String origin;
                        if (gen.invertX && gen.invertY) origin = "Bottom-Right";
                        else if (gen.invertX) origin = "Top-Right";
                        else if (gen.invertY) origin = "Bottom-Left";
                        else origin = "Top-Left";
                        machineOriginCombo.setSelectedItem(origin);
                    }
                    speedDownSpinner.setValue(gen.speedDown);
                    speedUpSpinner.setValue(gen.speedUp);
                    zUpSpinner.setValue(gen.penUp);
                    zDownSpinner.setValue(gen.penDown);

                    if ("Portrait".equals(gen.orientation)) {
                        portraitRadio.setSelected(true);
                    } else if (gen.orientation != null) {
                        landscapeRadio.setSelected(true);
                    }

                    if (gen.canvasAlignment != null) {
                        canvasAlignmentCombo.setSelectedItem(gen.canvasAlignment);
                    }

                    if (gen.viewRotation > 0) {
                        viewRotationCombo.setSelectedItem(String.valueOf(gen.viewRotation));
                    }

                    paddingXSpinner.setValue(gen.paddingX);
                    paddingYSpinner.setValue(gen.paddingY);

                    if (gen.pythonPath != null && !gen.pythonPath.isEmpty()) {
                        pythonPathField.setText(gen.pythonPath);
                    }

                    if ("gcode".equals(gen.backend)) {
                        backendCombo.setSelectedIndex(1);
                    } else {
                        backendCombo.setSelectedIndex(0);
                    }

                    if (gen.gcode != null) {
                        GcodeSettings gc = gen.gcode;
                        serialPortField.setText(gc.serial_port != null ? gc.serial_port : "/dev/ttyUSB0");
                        baudRateCombo.setSelectedItem(String.valueOf(gc.baud_rate));
                        if ("zaxis".equals(gc.pen_mode)) penModeCombo.setSelectedIndex(1);
                        else if ("m3m5".equals(gc.pen_mode)) penModeCombo.setSelectedIndex(2);
                        else penModeCombo.setSelectedIndex(0);
                        servoPinSpinner.setValue(gc.servo_pin);
                        feedRateDrawSpinner.setValue(gc.feed_rate_draw);
                        feedRateTravelSpinner.setValue(gc.feed_rate_travel);
                        gcodeServoUpSpinner.setValue(gc.pen_servo_up);
                        gcodeServoDownSpinner.setValue(gc.pen_servo_down);
                        zUpPosSpinner.setValue(gc.z_up);
                        zDownPosSpinner.setValue(gc.z_down);
                        gcodeWidthSpinner.setValue(gc.machine_width);
                        gcodeHeightSpinner.setValue(gc.machine_height);
                    }
                }

                stations.clear();
                if (config.stations() != null) {
                    stations.putAll(config.stations());
                }
                refreshTable();

            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Failed to load config: " + e.getMessage());
            }
        } else if (legacyStationFile.exists()) {
            try {
                Map<String, Object> map = mapper.readValue(legacyStationFile, Map.class);
                stations.clear();
                map.forEach((k, v) -> {
                    Map<String, Object> val = (Map<String, Object>) v;
                    stations.put(k, new StationConfig(
                            getDouble(val.get("x")),
                            getDouble(val.get("y")),
                            getInt(val.get("z_down")),
                            (String) val.get("behavior")));
                });
                refreshTable();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Failed to load legacy stations.json: " + e.getMessage());
            }
        } else {
            stations.put("default_station", new StationConfig(5.0, 50.0, 20, "simple_dip"));
            refreshTable();
        }

        fireVisualChange();
        updateConfigLabel();
    }

    private void updateConfigLabel() {
        if (activeConfigLabel != null) {
            activeConfigLabel.setText("Config: " + currentConfigFile.getName());
            activeConfigLabel.setToolTipText(currentConfigFile.getAbsolutePath());
        }
    }

    private double getDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        return 0.0;
    }

    private int getInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        return 0;
    }

    private void saveAs() {
        JFileChooser fc = new JFileChooser(".");
        fc.setSelectedFile(currentConfigFile);
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentConfigFile = fc.getSelectedFile();
            saveConfig();
            updateConfigLabel();
        }
    }

    private void loadFrom() {
        JFileChooser fc = new JFileChooser(".");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentConfigFile = fc.getSelectedFile();
            loadConfig();
            fireVisualChange();
        }
    }

    public void saveConfigSilent() {
        saveConfig(false);
    }

    private void saveConfig() {
        saveConfig(true);
    }

    private void saveConfig(boolean showMessage) {
        try {
            GeneralSettings gen = new GeneralSettings();
            gen.modelIndex = modelComboBox.getSelectedIndex();
            gen.mock = mockCheckBox.isSelected();
            gen.machineOrigin = getMachineOrigin();
            gen.invertX = isInvertX();
            gen.invertY = isInvertY();
            gen.swapXY = swapXYCheckBox.isSelected();
            gen.invertJogY = invertJogYCheckBox.isSelected();
            gen.visualMirror = false;
            gen.speedDown = (Integer) speedDownSpinner.getValue();
            gen.speedUp = (Integer) speedUpSpinner.getValue();
            gen.penUp = (Integer) zUpSpinner.getValue();
            gen.penDown = (Integer) zDownSpinner.getValue();
            gen.orientation = getOrientation();
            gen.canvasAlignment = getCanvasAlignment();
            gen.viewRotation = getViewRotation();
            gen.paddingX = getPaddingX();
            gen.paddingY = getPaddingY();
            gen.backend = getBackend();
            gen.pythonPath = getPythonPath();

            if ("gcode".equals(gen.backend)) {
                GcodeSettings gc = new GcodeSettings();
                gc.serial_port = serialPortField.getText().trim();
                gc.baud_rate = Integer.parseInt((String) baudRateCombo.getSelectedItem());
                int pmIdx = penModeCombo.getSelectedIndex();
                gc.pen_mode = pmIdx == 0 ? "servo" : pmIdx == 1 ? "zaxis" : "m3m5";
                gc.servo_pin = (Integer) servoPinSpinner.getValue();
                gc.feed_rate_draw = (Integer) feedRateDrawSpinner.getValue();
                gc.feed_rate_travel = (Integer) feedRateTravelSpinner.getValue();
                gc.pen_servo_up = (Integer) gcodeServoUpSpinner.getValue();
                gc.pen_servo_down = (Integer) gcodeServoDownSpinner.getValue();
                gc.z_up = (Double) zUpPosSpinner.getValue();
                gc.z_down = (Double) zDownPosSpinner.getValue();
                gc.machine_width = (Double) gcodeWidthSpinner.getValue();
                gc.machine_height = (Double) gcodeHeightSpinner.getValue();
                gen.gcode = gc;
            }

            AppConfig config = new AppConfig(gen, new LinkedHashMap<>(stations));

            mapper.writeValue(currentConfigFile, config);
            if (showMessage) {
                JOptionPane.showMessageDialog(this,
                        "Configuration saved to " + currentConfigFile.getAbsolutePath());
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (showMessage) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage());
            }
        }
    }

}
