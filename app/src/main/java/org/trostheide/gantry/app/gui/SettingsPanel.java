package org.trostheide.gantry.app.gui;

import com.fazecast.jSerialComm.SerialPort;
import org.trostheide.gantry.app.plot.GantryConfig;
import org.trostheide.gantry.app.plot.StationConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Editable view of a {@link GantryConfig}: hardware connection, geometry/transform and refill stations. */
public class SettingsPanel extends JPanel {

    private static final String[] ORIGINS = {"Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right"};
    private static final String[] ORIENTATIONS = {"Landscape", "Portrait"};
    private static final String[] ALIGNMENTS = {"None", "Top Left", "Top Right", "Bottom Left", "Bottom Right", "Center"};
    private static final String[] PEN_MODES = {"servo", "zaxis", "m3m5"};
    private static final String[] BEHAVIORS = {"simple_dip", "dip_swirl"};
    private static final Integer[] ROTATIONS = {0, 90, 180, 270};

    private final JComboBox<String> serialPortCombo = new JComboBox<>();
    private final JButton refreshPortsButton = new JButton("Refresh");
    private final JSpinner baudRateSpinner = new JSpinner(new SpinnerNumberModel(115200, 9600, 250000, 100));
    private final JCheckBox mockCheckBox = new JCheckBox("Mock backend (no serial port)");
    private final JCheckBox preflightCheckBox = new JCheckBox("Run Pre-Plot Checklist before Start");

    private final JSpinner machineWidthSpinner = new JSpinner(new SpinnerNumberModel(300.0, 1.0, 5000.0, 1.0));
    private final JSpinner machineHeightSpinner = new JSpinner(new SpinnerNumberModel(200.0, 1.0, 5000.0, 1.0));
    private final JComboBox<String> originCombo = new JComboBox<>(ORIGINS);
    private final JComboBox<String> orientationCombo = new JComboBox<>(ORIENTATIONS);
    private final JComboBox<String> alignmentCombo = new JComboBox<>(ALIGNMENTS);
    private final JComboBox<Integer> rotationCombo = new JComboBox<>(ROTATIONS);
    private final JSpinner paddingXSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1000.0, 1.0));
    private final JSpinner paddingYSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1000.0, 1.0));
    private final JCheckBox invertXCheckBox = new JCheckBox("Extra Invert X");
    private final JCheckBox invertYCheckBox = new JCheckBox("Extra Invert Y");
    private final JCheckBox swapXYCheckBox = new JCheckBox("Extra Swap X/Y");
    private final JCheckBox flipYCheckBox = new JCheckBox("Flip Y");
    private final JCheckBox softLimitsCheckBox = new JCheckBox("Soft limits — clamp jog to the bed (0/0 → width/height)");

    private final JComboBox<String> penModeCombo = new JComboBox<>(PEN_MODES);
    private final JSpinner servoPinSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 16, 1));
    private final JSpinner penUpSpinner = new JSpinner(new SpinnerNumberModel(60, 0, 180, 1));
    private final JSpinner penDownSpinner = new JSpinner(new SpinnerNumberModel(30, 0, 180, 1));
    private final JSpinner zUpSpinner = new JSpinner(new SpinnerNumberModel(5.0, -50.0, 50.0, 0.5));
    private final JSpinner zDownSpinner = new JSpinner(new SpinnerNumberModel(0.0, -50.0, 50.0, 0.5));
    private final JSpinner drawSpeedSpinner = new JSpinner(new SpinnerNumberModel(1000, 1, 20000, 100));
    private final JSpinner travelSpeedSpinner = new JSpinner(new SpinnerNumberModel(3000, 1, 20000, 100));
    private final JSpinner penDownDelaySpinner = new JSpinner(new SpinnerNumberModel(80, 0, 2000, 10));

    private final StationTableModel stationTableModel = new StationTableModel();
    private final JTable stationTable = new JTable(stationTableModel);

    // The section panels are kept as fields (not just added inline) so the guided Setup Wizard can
    // re-parent the real fields into its step cards instead of duplicating every spinner/combo.
    private final JPanel connectionPanel = connectionSection();
    private final JPanel geometryPanel = geometrySection();
    private final JPanel penPanel = penSection();
    private final JPanel stationsPanel = stationsSection();

    public SettingsPanel() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // Tabbed so the dialog stays a sensible height (the old stacked layout grew past the
        // screen and never scrolled). The section panels are the same instances the Setup Wizard
        // re-parents, so its step cards are unaffected.
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Connection", scrollable(connectionPanel));
        tabs.addTab("Geometry", scrollable(geometryPanel));
        tabs.addTab("Pen / Speed", scrollable(penPanel));
        tabs.addTab("Stations", stationsPanel);
        add(tabs, BorderLayout.CENTER);

        serialPortCombo.setEditable(true);
        refreshPortsButton.addActionListener(e -> refreshSerialPorts());
        refreshSerialPorts();
    }

    /** The "Connection" section panel, for the Setup Wizard to host as its own step. */
    JPanel connectionPanel() { return connectionPanel; }

    /** The "Machine Geometry" section panel, for the Setup Wizard to host as its own step. */
    JPanel geometryPanel() { return geometryPanel; }

    /** The "Pen / Speed" section panel, for the Setup Wizard to host as its own step. */
    JPanel penPanel() { return penPanel; }

    /** Repopulates the serial port combo box with currently detected ports (jSerialComm). */
    private void refreshSerialPorts() {
        String current = (String) serialPortCombo.getEditor().getItem();
        serialPortCombo.removeAllItems();
        for (SerialPort port : SerialPort.getCommPorts()) {
            String label = port.getSystemPortName() + " — " + port.getDescriptivePortName();
            serialPortCombo.addItem(label);
        }
        if (current != null && !current.isBlank()) {
            serialPortCombo.getEditor().setItem(current);
        }
    }

    /** Extracts the raw port name (e.g. "COM3") from a combo box entry, stripping any descriptive suffix. */
    private String extractPortName(String text) {
        if (text == null) return "";
        int sep = text.indexOf(" — ");
        return (sep >= 0 ? text.substring(0, sep) : text).trim();
    }

    private JPanel connectionSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(section("Connection"));
        GridBagConstraints gbc = gbc();

        JPanel portPanel = new JPanel(new BorderLayout(4, 0));
        portPanel.add(serialPortCombo, BorderLayout.CENTER);
        portPanel.add(refreshPortsButton, BorderLayout.EAST);
        addRow(panel, gbc, "Serial Port", portPanel);
        addRow(panel, gbc, "Baud Rate", baudRateSpinner);

        gbc.gridx = 0; gbc.gridy++; gbc.gridwidth = 2;
        panel.add(mockCheckBox, gbc);

        gbc.gridx = 0; gbc.gridy++; gbc.gridwidth = 2;
        panel.add(preflightCheckBox, gbc);

        return panel;
    }

    private JPanel geometrySection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(section("Machine Geometry"));
        GridBagConstraints gbc = gbc();

        addRow(panel, gbc, "Machine Width (mm)", machineWidthSpinner);
        addRow(panel, gbc, "Machine Height (mm)", machineHeightSpinner);
        addRow(panel, gbc, "Machine Origin", originCombo);
        addRow(panel, gbc, "Orientation", orientationCombo);
        addRow(panel, gbc, "Canvas Alignment", alignmentCombo);
        addRow(panel, gbc, "Data Rotation (deg)", rotationCombo);
        addRow(panel, gbc, "Padding X (mm)", paddingXSpinner);
        addRow(panel, gbc, "Padding Y (mm)", paddingYSpinner);

        gbc.gridx = 0; gbc.gridy++; gbc.gridwidth = 2;
        JPanel flags = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        flags.add(invertXCheckBox);
        flags.add(invertYCheckBox);
        flags.add(swapXYCheckBox);
        flags.add(flipYCheckBox);
        panel.add(flags, gbc);

        gbc.gridy++;
        panel.add(softLimitsCheckBox, gbc);

        return panel;
    }

    private JPanel penSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(section("Pen / Speed"));
        GridBagConstraints gbc = gbc();

        addRow(panel, gbc, "Pen Mode", penModeCombo);
        addRow(panel, gbc, "Servo Pin", servoPinSpinner);
        addRow(panel, gbc, "Pen Up (servo angle / Z mm)", penUpSpinner);
        addRow(panel, gbc, "Pen Down (servo angle / Z mm)", penDownSpinner);
        addRow(panel, gbc, "Z Up (mm)", zUpSpinner);
        addRow(panel, gbc, "Z Down (mm)", zDownSpinner);
        addRow(panel, gbc, "Draw Feed Rate", drawSpeedSpinner);
        addRow(panel, gbc, "Travel Feed Rate", travelSpeedSpinner);
        penDownDelaySpinner.setToolTipText("Dwell after pen-down (ms). Lower this if lines start "
                + "with an ink blob; raise it only if a slow pen skips the first millimetre.");
        addRow(panel, gbc, "Pen Down Delay (ms)", penDownDelaySpinner);

        return panel;
    }

    private JPanel stationsSection() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(section("Refill Stations"));

        stationTable.setRowHeight(22);
        JScrollPane scroll = new JScrollPane(stationTable);
        scroll.setPreferredSize(new Dimension(0, 140));
        panel.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton addBtn = new JButton("Add Station");
        addBtn.addActionListener(e -> stationTableModel.addRow());
        JButton removeBtn = new JButton("Remove Selected");
        removeBtn.addActionListener(e -> {
            int row = stationTable.getSelectedRow();
            if (row >= 0) {
                stationTableModel.removeRow(row);
            }
        });
        buttons.add(addBtn);
        buttons.add(removeBtn);
        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    private static GridBagConstraints gbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        return gbc;
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, String label, JComponent field) {
        gbc.gridx = 0; gbc.gridwidth = 1; gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(field, gbc);
        gbc.gridy++;
    }

    private static TitledBorder section(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD, 12f));
        return border;
    }

    /** Wraps a tab's content so an overflowing section scrolls within the tab instead of growing it. */
    private static JComponent scrollable(JComponent content) {
        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    /** Loads every field from {@code config} (does not retain a reference to it). */
    public void loadConfig(GantryConfig config) {
        serialPortCombo.getEditor().setItem(config.gcode.serialPort);
        baudRateSpinner.setValue(config.gcode.baudRate);
        mockCheckBox.setSelected(config.mock);
        preflightCheckBox.setSelected(config.preflightBeforeStart);

        machineWidthSpinner.setValue(config.gcode.machineWidth);
        machineHeightSpinner.setValue(config.gcode.machineHeight);
        originCombo.setSelectedItem(config.machineOrigin);
        orientationCombo.setSelectedItem(config.orientation);
        alignmentCombo.setSelectedItem(config.canvasAlignment == null ? "None" : config.canvasAlignment);
        rotationCombo.setSelectedItem(config.dataRotation);
        paddingXSpinner.setValue(config.paddingX);
        paddingYSpinner.setValue(config.paddingY);
        softLimitsCheckBox.setSelected(config.softLimits);
        invertXCheckBox.setSelected(config.invertX);
        invertYCheckBox.setSelected(config.invertY);
        swapXYCheckBox.setSelected(config.swapXY);
        flipYCheckBox.setSelected(config.flipY);

        penModeCombo.setSelectedItem(config.gcode.penMode);
        servoPinSpinner.setValue(config.gcode.servoPin);
        penUpSpinner.setValue(config.gcode.penServoUp);
        penDownSpinner.setValue(config.gcode.penServoDown);
        zUpSpinner.setValue(config.gcode.zUp);
        zDownSpinner.setValue(config.gcode.zDown);
        drawSpeedSpinner.setValue(config.gcode.feedRateDraw);
        travelSpeedSpinner.setValue(config.gcode.feedRateTravel);
        penDownDelaySpinner.setValue(config.gcode.penDownDelayMillis);

        stationTableModel.setStations(config.stations);
    }

    /** Builds a fresh {@link GantryConfig} from the current field values. */
    public GantryConfig toConfig() {
        GantryConfig config = new GantryConfig();
        config.gcode.serialPort = extractPortName((String) serialPortCombo.getEditor().getItem());
        config.gcode.baudRate = (Integer) baudRateSpinner.getValue();
        config.mock = mockCheckBox.isSelected();
        config.preflightBeforeStart = preflightCheckBox.isSelected();

        config.gcode.machineWidth = (Double) machineWidthSpinner.getValue();
        config.gcode.machineHeight = (Double) machineHeightSpinner.getValue();
        config.machineOrigin = (String) originCombo.getSelectedItem();
        config.orientation = (String) orientationCombo.getSelectedItem();
        String align = (String) alignmentCombo.getSelectedItem();
        config.canvasAlignment = "None".equals(align) ? null : align;
        config.dataRotation = (Integer) rotationCombo.getSelectedItem();
        config.paddingX = (Double) paddingXSpinner.getValue();
        config.paddingY = (Double) paddingYSpinner.getValue();
        config.invertX = invertXCheckBox.isSelected();
        config.invertY = invertYCheckBox.isSelected();
        config.swapXY = swapXYCheckBox.isSelected();
        config.flipY = flipYCheckBox.isSelected();
        config.softLimits = softLimitsCheckBox.isSelected();

        config.gcode.penMode = (String) penModeCombo.getSelectedItem();
        config.gcode.servoPin = (Integer) servoPinSpinner.getValue();
        config.gcode.penServoUp = (Integer) penUpSpinner.getValue();
        config.gcode.penServoDown = (Integer) penDownSpinner.getValue();
        config.gcode.zUp = (Double) zUpSpinner.getValue();
        config.gcode.zDown = (Double) zDownSpinner.getValue();
        config.gcode.feedRateDraw = (Integer) drawSpeedSpinner.getValue();
        config.gcode.feedRateTravel = (Integer) travelSpeedSpinner.getValue();
        config.gcode.penDownDelayMillis = (Integer) penDownDelaySpinner.getValue();

        config.stations = stationTableModel.toStations();
        return config;
    }

    /** Table model for the refill-station editor: name, x, y, zDown, behavior, colour, dwell, swirl. */
    private static class StationTableModel extends AbstractTableModel {
        private static final String[] COLUMNS =
                {"Name", "X (mm)", "Y (mm)", "Z Down", "Behavior", "Color", "Dwell (ms)", "Swirl (mm)"};
        private final List<Object[]> rows = new ArrayList<>();

        void setStations(Map<String, StationConfig> stations) {
            rows.clear();
            for (Map.Entry<String, StationConfig> e : stations.entrySet()) {
                StationConfig s = e.getValue();
                rows.add(new Object[] { e.getKey(), s.x(), s.y(), s.zDown(), s.behavior(),
                        s.color() == null ? "" : s.color(), s.dwellMs(), s.swirlRadius() });
            }
            fireTableDataChanged();
        }

        Map<String, StationConfig> toStations() {
            Map<String, StationConfig> result = new LinkedHashMap<>();
            for (Object[] row : rows) {
                String name = String.valueOf(row[0]).trim();
                if (name.isEmpty()) {
                    continue;
                }
                String color = String.valueOf(row[5]).trim();
                result.put(name, new StationConfig(
                        ((Number) row[1]).doubleValue(),
                        ((Number) row[2]).doubleValue(),
                        ((Number) row[3]).intValue(),
                        String.valueOf(row[4]),
                        color.isEmpty() ? null : color,
                        ((Number) row[6]).intValue(),
                        ((Number) row[7]).doubleValue()));
            }
            return result;
        }

        void addRow() {
            rows.add(new Object[] { "station" + (rows.size() + 1), 0.0, 0.0, 30, "simple_dip",
                    "", StationConfig.DEFAULT_DWELL_MS, StationConfig.DEFAULT_SWIRL_RADIUS });
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        void removeRow(int index) {
            rows.remove(index);
            fireTableRowsDeleted(index, index);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 1, 2, 7 -> Double.class;
                case 3, 6 -> Integer.class;
                default -> String.class;
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return rows.get(rowIndex)[columnIndex];
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columnIndex == 4) {
                String behavior = String.valueOf(value);
                if (!behavior.equals("simple_dip") && !behavior.equals("dip_swirl") && !behavior.equals("rinse")) {
                    behavior = "simple_dip";
                }
                rows.get(rowIndex)[columnIndex] = behavior;
            } else {
                rows.get(rowIndex)[columnIndex] = value;
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }
}
