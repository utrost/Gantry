package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.pipeline.svgimport.PaperFormat;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;
import org.trostheide.gantry.svgtoolbox.Config;

import javax.swing.*;
import java.awt.*;

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
    /** The full toolbox option set, shared verbatim with {@link EditProcessDialog}. */
    private final ToolboxOptionsPanel optionsPanel;

    private Result result;

    /** Turns green once a valid size is chosen, signalling the import is ready to run. */
    private static final Color READY_GREEN = new Color(46, 125, 50);
    private final JButton okBtn = new JButton("Import");

    public SvgImportDialog(Window owner) {
        this(owner, null);
    }

    public SvgImportDialog(Window owner, java.io.File sourceSvg) {
        super(owner, "Import SVG", ModalityType.APPLICATION_MODAL);
        optionsPanel = new ToolboxOptionsPanel(SvgFillColors.read(sourceSvg));

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

    private JComponent buildToolboxPanel() {
        JPanel form = new JPanel(new BorderLayout(0, 6));
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        form.add(toolboxEnableCheck, BorderLayout.NORTH);
        form.add(optionsPanel, BorderLayout.CENTER);

        // Hatching only runs as part of the SVGToolBox pipeline; reflect that in
        // the UI so the master toggle matches what will actually happen.
        optionsPanel.addHatchActionListener(e -> {
            if (optionsPanel.isHatchEnabled()) {
                toolboxEnableCheck.setSelected(true);
            }
        });

        return new JScrollPane(form);
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

        // Enabling hatching implies running the SVGToolBox pipeline — otherwise
        // the hatch settings would be silently dropped. Treat hatch-on as
        // toolbox-on so importing actually applies the chosen hatching.
        Config toolboxConfig = null;
        if (toolboxEnableCheck.isSelected() || optionsPanel.isHatchEnabled()) {
            try {
                toolboxConfig = optionsPanel.buildConfig();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid SVGToolBox option",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        result = new Result(importOptions, toolboxConfig);
        dispose();
    }

    /** Shows the dialog and returns the chosen options, or {@code null} if cancelled. */
    public Result showDialog() {
        setVisible(true);
        return result;
    }
}
