package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.pipeline.svgimport.PaperFormat;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;
import org.trostheide.gantry.svgtoolbox.Config;
import org.trostheide.gantry.svgtoolbox.HatchStyle;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Modal dialog collecting {@link SvgImportOptions} (and, optionally, an SVGToolBox
 * {@link Config}) for "Import SVG...": the GUI equivalent of the legacy "Process SVG"
 * (with refill) and "Draw SVG" (no refill, max draw distance = 0) tabs, unified into one
 * preset-driven dialog per the roadmap.
 */
public final class SvgImportDialog extends JDialog {

    /** The dialog's result: import options plus an optional SVGToolBox pre-processing config. */
    public record Result(SvgImportOptions importOptions, Config toolboxConfig) {
    }

    private final JSpinner maxDrawDistanceSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 100000.0, 10.0));
    private final JTextField stationField = new JTextField("default_station", 14);
    private final JSpinner curveStepSpinner = new JSpinner(new SpinnerNumberModel(0.1, 0.01, 10.0, 0.1));
    /** Shown as the initial, non-committal selection so the user must consciously pick a size. */
    private static final String FIT_TO_PROMPT = "-- Select size --";
    private final JComboBox<String> fitToCombo = new JComboBox<>(
            new String[] {FIT_TO_PROMPT, "A6", "A5", "A4", "A3", "A2", "A1", "XL", "Custom"});
    private final JTextField customSizeField = new JTextField("210x297", 10);
    private final JSpinner paddingSpinner = new JSpinner(new SpinnerNumberModel(10.0, 0.0, 500.0, 1.0));
    private final JCheckBox keepAspectRatioCheck = new JCheckBox("Keep aspect ratio", true);
    private final JCheckBox mirrorCheck = new JCheckBox("Mirror");

    // SVGToolBox pre-processing options
    private final JCheckBox toolboxEnableCheck = new JCheckBox("Run SVGToolBox processing before import");
    private final JTextField strokeWidthField = new JTextField("0", 8);
    private final JTextField paletteField = new JTextField(20);
    private final JCheckBox hatchCheck = new JCheckBox("Enable hatching");
    private final JComboBox<String> hatchPatternCombo =
            new JComboBox<>(new String[] {"linear", "cross", "zigzag", "wave", "dot", "none", "empty"});
    private final JSpinner hatchAngleSpinner = new JSpinner(new SpinnerNumberModel(45.0, -360.0, 360.0, 5.0));
    private final JSpinner hatchGapSpinner = new JSpinner(new SpinnerNumberModel(5.0, 0.1, 1000.0, 0.5));
    private final JTextField hiddenLayersField = new JTextField(20);
    private final JSpinner simplifyToleranceSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 100.0, 0.1));
    private final JSpinner rotateSpinner = new JSpinner(new SpinnerNumberModel(0.0, -360.0, 360.0, 90.0));
    private final JComboBox<String> toolboxCropCombo = new JComboBox<>(new String[] {"None", "A4", "Letter", "Custom"});
    private final JTextField toolboxCropCustomField = new JTextField("793.7x1122.5", 12);
    private final JCheckBox optimizeCheck = new JCheckBox("Optimize path order");
    private final JCheckBox linesimplifyCheck = new JCheckBox("Linesimplify (RDP)");
    private final JSpinner linesimplifyToleranceSpinner = new JSpinner(new SpinnerNumberModel(0.378, 0.0, 100.0, 0.01));
    private final JCheckBox linemergeCheck = new JCheckBox("Linemerge");
    private final JSpinner linemergeToleranceSpinner = new JSpinner(new SpinnerNumberModel(1.89, 0.0, 100.0, 0.01));
    private final JCheckBox linesortCheck = new JCheckBox("Linesort");
    private final JCheckBox linesortTwoOptCheck = new JCheckBox("Linesort 2-opt");
    private final JCheckBox reloopCheck = new JCheckBox("Reloop closed paths");
    private final JCheckBox printStatsCheck = new JCheckBox("Print statistics");

    private Result result;

    /** Turns green once a valid size is chosen, signalling the import is ready to run. */
    private static final Color READY_GREEN = new Color(46, 125, 50);
    private final JButton okBtn = new JButton("Import");

    public SvgImportDialog(Window owner) {
        super(owner, "Import SVG", ModalityType.APPLICATION_MODAL);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Import", buildImportPanel());
        tabs.addTab("Process SVG (optional)", buildToolboxPanel());

        JButton cancelBtn = new JButton("Cancel");
        okBtn.addActionListener(e -> onOk());
        cancelBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(okBtn);
        buttons.add(cancelBtn);

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(okBtn);

        // The import can't proceed until a size is chosen, so start disabled and re-evaluate
        // whenever the size selection or custom-size text changes.
        fitToCombo.addActionListener(e -> updateImportButtonState());
        customSizeField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateImportButtonState(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateImportButtonState(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateImportButtonState(); }
        });
        updateImportButtonState();

        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Enables (and greens) the Import button only once a usable size is selected: a concrete
     * preset, or "Custom" with a parseable WxH value. Otherwise it stays disabled and neutral.
     */
    private void updateImportButtonState() {
        String selection = (String) fitToCombo.getSelectedItem();
        boolean ready;
        if (FIT_TO_PROMPT.equals(selection)) {
            ready = false;
        } else if ("Custom".equals(selection)) {
            ready = PaperFormat.fromString(customSizeField.getText().trim()) != null;
        } else {
            ready = true;
        }
        okBtn.setEnabled(ready);
        okBtn.setBackground(ready ? READY_GREEN : null);
        okBtn.setForeground(ready ? Color.WHITE : null);
        okBtn.setOpaque(ready);
    }

    private JPanel buildImportPanel() {
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

        return form;
    }

    private JPanel buildToolboxPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;

        gbc.gridwidth = 2;
        form.add(toolboxEnableCheck, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;

        addRow(form, gbc, "Stroke width override (px, 0 = none)", strokeWidthField);
        addRow(form, gbc, "Palette (hex colors, comma-separated)", paletteField);

        gbc.gridx = 0;
        gbc.gridwidth = 2;
        form.add(hatchCheck, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;

        addRow(form, gbc, "Hatch pattern", hatchPatternCombo);
        addRow(form, gbc, "Hatch angle (deg)", hatchAngleSpinner);
        addRow(form, gbc, "Hatch gap", hatchGapSpinner);
        addRow(form, gbc, "Hidden layers (hex colors, comma-separated)", hiddenLayersField);
        addRow(form, gbc, "Simplify tolerance (0 = off)", simplifyToleranceSpinner);
        addRow(form, gbc, "Rotate (deg)", rotateSpinner);
        addRow(form, gbc, "Crop", toolboxCropCombo);
        addRow(form, gbc, "Custom crop (WxH px)", toolboxCropCustomField);

        gbc.gridx = 0;
        gbc.gridwidth = 2;
        form.add(optimizeCheck, gbc);
        gbc.gridy++;
        form.add(linesimplifyCheck, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;
        addRow(form, gbc, "Linesimplify tolerance", linesimplifyToleranceSpinner);

        gbc.gridx = 0;
        gbc.gridwidth = 2;
        form.add(linemergeCheck, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;
        addRow(form, gbc, "Linemerge tolerance", linemergeToleranceSpinner);

        gbc.gridx = 0;
        gbc.gridwidth = 2;
        form.add(linesortCheck, gbc);
        gbc.gridy++;
        form.add(linesortTwoOptCheck, gbc);
        gbc.gridy++;
        form.add(reloopCheck, gbc);
        gbc.gridy++;
        form.add(printStatsCheck, gbc);

        toolboxCropCustomField.setEnabled(false);
        toolboxCropCombo.addActionListener(e ->
                toolboxCropCustomField.setEnabled("Custom".equals(toolboxCropCombo.getSelectedItem())));

        return form;
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
        if (FIT_TO_PROMPT.equals(fitToSelection)) {
            JOptionPane.showMessageDialog(this,
                    "Please choose a 'Fit to' size before importing (e.g. A4, A3, or a custom size).",
                    "Size required", JOptionPane.ERROR_MESSAGE);
            return;
        }
        PaperFormat format = "Custom".equals(fitToSelection)
                ? PaperFormat.fromString(customSizeField.getText().trim())
                : PaperFormat.fromString(fitToSelection);

        if (format == null) {
            JOptionPane.showMessageDialog(this, "Custom size must be 'WxH' in mm, e.g. 210x297.",
                    "Invalid size", JOptionPane.ERROR_MESSAGE);
            return;
        }

        SvgImportOptions importOptions =
                SvgImportOptions.fitToFormat(maxDrawDistance, station, curveStep, format, padding, mirror, keepAspect);

        Config toolboxConfig = null;
        if (toolboxEnableCheck.isSelected()) {
            try {
                toolboxConfig = buildToolboxConfig();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid SVGToolBox option",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        result = new Result(importOptions, toolboxConfig);
        dispose();
    }

    private Config buildToolboxConfig() {
        float strokeWidth;
        try {
            strokeWidth = Float.parseFloat(strokeWidthField.getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Stroke width must be a number.");
        }

        List<java.awt.Color> palette = parseColors(paletteField.getText());
        List<String> hiddenLayers = Arrays.stream(hiddenLayersField.getText().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        double hatchAngle = ((Number) hatchAngleSpinner.getValue()).doubleValue();
        double hatchGap = ((Number) hatchGapSpinner.getValue()).doubleValue();
        HatchStyle globalStyle = new HatchStyle(hatchAngle, hatchGap, "linear");

        Rectangle2D cropBounds = parseToolboxCrop();

        return new Config.Builder()
                .inputPath("")
                .outputPath("")
                .strokeWidth(strokeWidth)
                .palette(palette)
                .enableHatching(hatchCheck.isSelected())
                .globalStyle(globalStyle)
                .overrides(Collections.emptyMap())
                .strokeWidthOverrides(Collections.emptyMap())
                .hiddenLayers(hiddenLayers)
                .noHatchColors(Collections.emptyList())
                .simplifyTolerance(((Number) simplifyToleranceSpinner.getValue()).doubleValue())
                .hatchPattern((String) hatchPatternCombo.getSelectedItem())
                .rotationDegrees(((Number) rotateSpinner.getValue()).doubleValue())
                .printStats(printStatsCheck.isSelected())
                .cropBounds(cropBounds)
                .optimizePaths(optimizeCheck.isSelected())
                .linesimplify(linesimplifyCheck.isSelected())
                .linesimplifyTolerance(((Number) linesimplifyToleranceSpinner.getValue()).doubleValue())
                .linemerge(linemergeCheck.isSelected())
                .linemergeTolerance(((Number) linemergeToleranceSpinner.getValue()).doubleValue())
                .linesort(linesortCheck.isSelected())
                .linesortTwoOpt(linesortTwoOptCheck.isSelected())
                .reloop(reloopCheck.isSelected())
                .build();
    }

    private Rectangle2D parseToolboxCrop() {
        return PaperSizes.resolve((String) toolboxCropCombo.getSelectedItem(), toolboxCropCustomField.getText());
    }

    private List<java.awt.Color> parseColors(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return new ArrayList<>();
        }
        List<java.awt.Color> colors = new ArrayList<>();
        for (String part : trimmed.split(",")) {
            try {
                colors.add(java.awt.Color.decode(part.trim()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid palette color: " + part.trim());
            }
        }
        return colors;
    }

    /** Shows the dialog and returns the chosen options, or {@code null} if cancelled. */
    public Result showDialog() {
        setVisible(true);
        return result;
    }
}
