package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.pipeline.svgimport.PaperFormat;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;

import javax.swing.*;
import java.awt.*;

/**
 * Modal dialog collecting {@link SvgImportOptions} for "Import SVG...": the GUI equivalent of
 * the legacy "Process SVG" (with refill) and "Draw SVG" (no refill, max draw distance = 0) tabs,
 * unified into one preset-driven dialog per the roadmap.
 */
public final class SvgImportDialog extends JDialog {

    private final JSpinner maxDrawDistanceSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 100000.0, 10.0));
    private final JTextField stationField = new JTextField("default_station", 14);
    private final JSpinner curveStepSpinner = new JSpinner(new SpinnerNumberModel(0.5, 0.01, 10.0, 0.1));
    private final JComboBox<String> fitToCombo = new JComboBox<>(new String[] {"None", "A5", "A4", "A3", "XL", "Custom"});
    private final JTextField customSizeField = new JTextField("210x297", 10);
    private final JSpinner paddingSpinner = new JSpinner(new SpinnerNumberModel(10.0, 0.0, 500.0, 1.0));
    private final JCheckBox keepAspectRatioCheck = new JCheckBox("Keep aspect ratio", true);
    private final JCheckBox mirrorCheck = new JCheckBox("Mirror");

    private SvgImportOptions result;

    public SvgImportDialog(Window owner) {
        super(owner, "Import SVG", ModalityType.APPLICATION_MODAL);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;

        addRow(form, gbc, "Max draw distance (mm, 0 = no refill)", maxDrawDistanceSpinner);
        addRow(form, gbc, "Default station ID", stationField);
        addRow(form, gbc, "Curve step (mm)", curveStepSpinner);
        addRow(form, gbc, "Fit to", fitToCombo);
        addRow(form, gbc, "Custom size (WxH mm)", customSizeField);
        addRow(form, gbc, "Padding (mm)", paddingSpinner);

        gbc.gridx = 0;
        gbc.gridwidth = 2;
        form.add(keepAspectRatioCheck, gbc);
        gbc.gridy++;
        form.add(mirrorCheck, gbc);

        customSizeField.setEnabled(false);
        fitToCombo.addActionListener(e -> customSizeField.setEnabled("Custom".equals(fitToCombo.getSelectedItem())));

        JButton okBtn = new JButton("Import");
        JButton cancelBtn = new JButton("Cancel");
        okBtn.addActionListener(e -> onOk());
        cancelBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(okBtn);
        buttons.add(cancelBtn);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(okBtn);
        pack();
        setLocationRelativeTo(owner);
    }

    private void addRow(JPanel form, GridBagConstraints gbc, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        form.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(field, gbc);
        gbc.gridy++;
    }

    private void onOk() {
        double maxDrawDistance = ((Number) maxDrawDistanceSpinner.getValue()).doubleValue();
        String station = stationField.getText().trim();
        if (station.isEmpty()) {
            station = "default_station";
        }
        double curveStep = ((Number) curveStepSpinner.getValue()).doubleValue();
        double padding = ((Number) paddingSpinner.getValue()).doubleValue();
        boolean keepAspect = keepAspectRatioCheck.isSelected();
        boolean mirror = mirrorCheck.isSelected();

        String fitToSelection = (String) fitToCombo.getSelectedItem();
        PaperFormat format = "Custom".equals(fitToSelection)
                ? PaperFormat.fromString(customSizeField.getText().trim())
                : PaperFormat.fromString("None".equals(fitToSelection) ? null : fitToSelection);

        if ("Custom".equals(fitToSelection) && format == null) {
            JOptionPane.showMessageDialog(this, "Custom size must be 'WxH' in mm, e.g. 210x297.",
                    "Invalid size", JOptionPane.ERROR_MESSAGE);
            return;
        }

        result = format != null
                ? SvgImportOptions.fitToFormat(maxDrawDistance, station, curveStep, format, padding, mirror)
                : new SvgImportOptions(maxDrawDistance, station, curveStep, 0, 0, keepAspect, 0, 0, mirror);

        dispose();
    }

    /** Shows the dialog and returns the chosen options, or {@code null} if cancelled. */
    public SvgImportOptions showDialog() {
        setVisible(true);
        return result;
    }
}
