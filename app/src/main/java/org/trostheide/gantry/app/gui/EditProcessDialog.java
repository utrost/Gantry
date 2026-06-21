package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.svgtoolbox.Config;
import org.trostheide.gantry.svgtoolbox.HatchStyle;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modal dialog for "Edit &gt; Process SVG...": re-runs a subset of the SVGToolBox processors
 * (Crop, Hatch, Palette, Rotate, Optimize) against the originally imported SVG file, so the user
 * can tweak these without re-importing from scratch.
 */
public final class EditProcessDialog extends JDialog {

    private final JComboBox<String> cropCombo = new JComboBox<>(new String[] {"None", "A4", "Letter", "Custom"});
    private final JTextField cropCustomField = new JTextField("793.7x1122.5", 12);

    private final JCheckBox hatchCheck = new JCheckBox("Enable hatching");
    private final JComboBox<String> hatchPatternCombo =
            new JComboBox<>(new String[] {"linear", "cross", "zigzag", "wave", "dot", "none", "empty"});
    private final JSpinner hatchAngleSpinner = new JSpinner(new SpinnerNumberModel(45.0, -360.0, 360.0, 5.0));
    private final JSpinner hatchGapSpinner = new JSpinner(new SpinnerNumberModel(5.0, 0.1, 1000.0, 0.5));

    private final JTextField paletteField = new JTextField(20);

    private final JSpinner rotateSpinner = new JSpinner(new SpinnerNumberModel(0.0, -360.0, 360.0, 90.0));

    private final JCheckBox optimizeCheck = new JCheckBox("Optimize path order");

    private Config result;

    public EditProcessDialog(Window owner) {
        super(owner, "Process SVG", ModalityType.APPLICATION_MODAL);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;

        addSection(form, gbc, "Crop");
        addRow(form, gbc, "Crop to", cropCombo);
        addRow(form, gbc, "Custom crop (WxH px)", cropCustomField);

        addSection(form, gbc, "Hatch");
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        form.add(hatchCheck, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;
        addRow(form, gbc, "Hatch pattern", hatchPatternCombo);
        addRow(form, gbc, "Hatch angle (deg)", hatchAngleSpinner);
        addRow(form, gbc, "Hatch gap", hatchGapSpinner);

        addSection(form, gbc, "Palette");
        addRow(form, gbc, "Colors (hex, comma-separated)", paletteField);

        addSection(form, gbc, "Rotate");
        addRow(form, gbc, "Rotate (deg)", rotateSpinner);

        addSection(form, gbc, "Optimize");
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        form.add(optimizeCheck, gbc);
        gbc.gridy++;

        cropCustomField.setEnabled(false);
        cropCombo.addActionListener(e -> cropCustomField.setEnabled("Custom".equals(cropCombo.getSelectedItem())));

        JButton okBtn = new JButton("Apply");
        JButton cancelBtn = new JButton("Cancel");
        okBtn.addActionListener(e -> onOk());
        cancelBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(okBtn);
        buttons.add(cancelBtn);

        setLayout(new BorderLayout());
        add(new JScrollPane(form), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(okBtn);
        setSize(420, 560);
        setLocationRelativeTo(owner);
    }

    private static JLabel section(String title) {
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private void addSection(JPanel form, GridBagConstraints gbc, String title) {
        if (gbc.gridy > 0) {
            gbc.insets = new Insets(14, 3, 3, 3);
        }
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        form.add(section(title), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(3, 3, 3, 3);
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
        Rectangle2D cropBounds;
        try {
            cropBounds = parseCrop();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid crop", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<Color> palette;
        try {
            palette = parseColors(paletteField.getText());
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid palette", JOptionPane.ERROR_MESSAGE);
            return;
        }

        double hatchAngle = ((Number) hatchAngleSpinner.getValue()).doubleValue();
        double hatchGap = ((Number) hatchGapSpinner.getValue()).doubleValue();

        result = new Config.Builder()
                .inputPath("")
                .outputPath("")
                .strokeWidth(0f)
                .palette(palette)
                .enableHatching(hatchCheck.isSelected())
                .globalStyle(new HatchStyle(hatchAngle, hatchGap, "linear"))
                .overrides(Collections.emptyMap())
                .strokeWidthOverrides(Collections.emptyMap())
                .hiddenLayers(Collections.emptyList())
                .noHatchColors(Collections.emptyList())
                .simplifyTolerance(0)
                .hatchPattern((String) hatchPatternCombo.getSelectedItem())
                .rotationDegrees(((Number) rotateSpinner.getValue()).doubleValue())
                .printStats(false)
                .cropBounds(cropBounds)
                .optimizePaths(optimizeCheck.isSelected())
                .linesimplify(false)
                .linemerge(false)
                .linesort(false)
                .reloop(false)
                .build();
        dispose();
    }

    private Rectangle2D parseCrop() {
        String selection = (String) cropCombo.getSelectedItem();
        switch (selection) {
            case "A4":
                return new Rectangle2D.Double(0, 0, 793.7, 1122.5);
            case "Letter":
                return new Rectangle2D.Double(0, 0, 816.0, 1056.0);
            case "Custom":
                try {
                    String[] parts = cropCustomField.getText().trim().split("x");
                    double w = Double.parseDouble(parts[0]);
                    double h = Double.parseDouble(parts[1]);
                    return new Rectangle2D.Double(0, 0, w, h);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Custom crop must be 'WxH' in px, e.g. 793.7x1122.5.");
                }
            default:
                return null;
        }
    }

    private List<Color> parseColors(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return new ArrayList<>();
        }
        List<Color> colors = new ArrayList<>();
        for (String part : trimmed.split(",")) {
            try {
                colors.add(Color.decode(part.trim()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid palette color: " + part.trim());
            }
        }
        return colors;
    }

    /** Shows the dialog and returns the chosen config, or {@code null} if cancelled. */
    public Config showDialog() {
        setVisible(true);
        return result;
    }
}
