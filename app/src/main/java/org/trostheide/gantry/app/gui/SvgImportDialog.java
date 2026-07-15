package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.pipeline.svgimport.PaperFormat;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;
import org.trostheide.gantry.svgtoolbox.Config;

import javax.swing.*;
import java.awt.*;

/**
 * Modal dialog collecting {@link SvgImportOptions} (and, optionally, an SVGToolBox
 * {@link Config}) for "Add SVG or vector drawing...": the GUI equivalent of the legacy "Process SVG"
 * (with refill) and "Draw SVG" (no refill, max draw distance = 0) tabs, unified into one
 * preset-driven dialog per the roadmap.
 */
public final class SvgImportDialog extends JDialog {

    private static final Dimension BASIC_DIALOG_SIZE = new Dimension(620, 260);
    private static final Dimension ADVANCED_IMPORT_DIALOG_SIZE = new Dimension(620, 560);
    private static final Dimension SVG_PROCESSING_DIALOG_SIZE = new Dimension(980, 720);

    /** The dialog's result: import options plus an optional SVGToolBox pre-processing config. */
    public record Result(SvgImportOptions importOptions, Config toolboxConfig) {
    }

    private final JSpinner maxDrawDistanceSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 100000.0, 10.0));
    private final JTextField stationField = new JTextField("default_station", 14);
    private final JSpinner curveStepSpinner = new JSpinner(new SpinnerNumberModel(0.1, 0.01, 10.0, 0.1));
    private static final String FIT_TO_MACHINE = "Machine bed";
    private final JComboBox<String> fitToCombo = new JComboBox<>(
            new String[] {FIT_TO_MACHINE, "A6", "A5", "A4", "A3", "A2", "A1", "XL", "Custom"});
    private final JTextField customSizeField = new JTextField("210x297", 10);
    private final JSpinner paddingSpinner = new JSpinner(new SpinnerNumberModel(
            ArtworkImportPolicy.DEFAULT_PADDING_MM, 0.0, 500.0, 1.0));
    private final JCheckBox keepAspectRatioCheck = new JCheckBox("Keep aspect ratio", true);
    private final JCheckBox mirrorCheck = new JCheckBox("Mirror");
    private final PaperFormat machineFormat;
    private final JLabel safeFitSummary = new JLabel();
    private boolean advancedImportVisible;

    // SVGToolBox pre-processing options
    /** The full toolbox option set, shared verbatim with {@link EditProcessDialog}. */
    private final ToolboxOptionsPanel optionsPanel;

    private Result result;

    /** Turns green once a valid size is chosen, signalling the import is ready to run. */
    private static final Color READY_GREEN = new Color(46, 125, 50);
    private final JButton okBtn = new JButton("Import");

    public SvgImportDialog(Window owner) {
        this(owner, null, 300, 200);
    }

    public SvgImportDialog(Window owner, java.io.File sourceSvg) {
        this(owner, sourceSvg, 300, 200);
    }

    public SvgImportDialog(Window owner, java.io.File sourceSvg,
            double machineWidth, double machineHeight) {
        super(owner, "Add SVG or vector drawing", ModalityType.APPLICATION_MODAL);
        machineFormat = ArtworkImportPolicy.machineBed(machineWidth, machineHeight);
        optionsPanel = new ToolboxOptionsPanel(SvgFillColors.read(sourceSvg));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Add artwork", buildImportPanel());
        tabs.addTab("Process artwork (optional)", buildToolboxPanel(sourceSvg));
        tabs.addChangeListener(e -> resizeDialog(tabs.getSelectedIndex() == 0
                ? advancedImportVisible ? ADVANCED_IMPORT_DIALOG_SIZE : BASIC_DIALOG_SIZE
                : SVG_PROCESSING_DIALOG_SIZE));

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

        fitToCombo.addActionListener(e -> {
            updateSafeFitSummary();
            updateImportButtonState();
        });
        paddingSpinner.addChangeListener(e -> updateSafeFitSummary());
        customSizeField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateImportButtonState(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateImportButtonState(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateImportButtonState(); }
        });
        updateSafeFitSummary();
        updateImportButtonState();

        pack();
        resizeDialog(BASIC_DIALOG_SIZE);
    }

    /**
     * The safe machine-bed default is immediately ready. Custom sizes still require a valid WxH
     * value before import can proceed.
     */
    private void updateImportButtonState() {
        String selection = (String) fitToCombo.getSelectedItem();
        boolean ready;
        if ("Custom".equals(selection)) {
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
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel basic = new JPanel();
        basic.setLayout(new BoxLayout(basic, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel("Fit artwork safely to your plotter");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 16f));
        safeFitSummary.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        basic.add(heading);
        basic.add(safeFitSummary);

        JCheckBox advancedToggle = new JCheckBox("Advanced options");
        advancedToggle.setToolTipText("Show paper formats, refill settings, curve detail, and mirroring");
        JPanel advanced = buildAdvancedOptionsPanel();
        advanced.setVisible(false);
        advancedToggle.addActionListener(e -> {
            advancedImportVisible = advancedToggle.isSelected();
            advanced.setVisible(advancedImportVisible);
            resizeDialog(advancedImportVisible ? ADVANCED_IMPORT_DIALOG_SIZE : BASIC_DIALOG_SIZE);
        });
        basic.add(advancedToggle);
        root.add(basic, BorderLayout.NORTH);
        root.add(advanced, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildAdvancedOptionsPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Advanced import options"),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
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

    private void updateSafeFitSummary() {
        double padding = ((Number) paddingSpinner.getValue()).doubleValue();
        String selection = (String) fitToCombo.getSelectedItem();
        if (FIT_TO_MACHINE.equals(selection)) {
            safeFitSummary.setText(ArtworkImportPolicy.summary(machineFormat, padding));
        } else {
            safeFitSummary.setText("Advanced target: " + selection + " with a "
                    + formatNumber(padding) + " mm safety margin.");
        }
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : Double.toString(value);
    }

    /** Keeps every import view inside the supported 1024x800 screen; long expert forms scroll. */
    private void resizeDialog(Dimension requested) {
        Rectangle bounds = getGraphicsConfiguration() == null
                ? GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds()
                : getGraphicsConfiguration().getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
        int availableWidth = bounds.width - insets.left - insets.right - 40;
        int availableHeight = bounds.height - insets.top - insets.bottom - 40;
        setSize(Math.min(requested.width, availableWidth), Math.min(requested.height, availableHeight));
        setLocationRelativeTo(getOwner());
    }

    private JComponent buildToolboxPanel(java.io.File sourceSvg) {
        JPanel form = new JPanel(new BorderLayout(8, 6));
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel intro = new JLabel("Choose a goal, then check the preview. Keep artwork unchanged is always available.");
        form.add(intro, BorderLayout.NORTH);
        JScrollPane controls = new JScrollPane(optionsPanel);
        controls.setBorder(null);
        controls.getVerticalScrollBar().setUnitIncrement(12);
        controls.setPreferredSize(new Dimension(430, 560));
        form.add(controls, BorderLayout.WEST);
        form.add(new ProcessingPreviewPanel(sourceSvg, optionsPanel), BorderLayout.CENTER);

        // These features only run as part of the SVGToolBox pipeline; reflect that
        // in the UI so the master toggle matches what will actually happen.
        optionsPanel.addHatchActionListener(e -> {
            // Kept as an explicit listener seam for dialog tests and accessibility automation.
        });
        optionsPanel.addHanddrawnActionListener(e -> {
            // Processing features are applied directly; there is no hidden master switch.
        });

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
        PaperFormat format = FIT_TO_MACHINE.equals(fitToSelection)
                ? machineFormat
                : "Custom".equals(fitToSelection)
                    ? PaperFormat.fromString(customSizeField.getText().trim())
                    : PaperFormat.fromString(fitToSelection);

        if (format == null) {
            JOptionPane.showMessageDialog(this, "Custom size must be 'WxH' in mm, e.g. 210x297.",
                    "Invalid size", JOptionPane.ERROR_MESSAGE);
            return;
        }

        SvgImportOptions importOptions =
                SvgImportOptions.fitToFormat(maxDrawDistance, station, curveStep, format, padding, mirror, keepAspect);

        // Enabling a processing feature implies running the SVGToolBox pipeline — otherwise
        // the visible settings would be silently dropped.
        Config toolboxConfig = null;
        if (optionsPanel.hasProcessingEnabled()) {
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
