package org.trostheide.svgtoolbox.ui;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.SvgToolboxRunner;
import org.trostheide.svgtoolbox.core.SvgStatistics;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.io.File;

public class ControlPanel extends JPanel {

    private static final float DEFAULT_STROKE_WIDTH = 1.0f;
    private static final String DEFAULT_PATTERN = "none";
    private static final double DEFAULT_ANGLE = 45.0;
    private static final double DEFAULT_GAP = 5.0;
    private static final int DEBOUNCE_DELAY_MS = 600;

    private static final String[] PATTERN_OPTIONS = {
            "none", "empty", "linear", "cross", "zigzag", "wave", "dot"
    };

    private JCheckBox chkEnableHatching;
    private JCheckBox chkOptimize;
    private JCheckBox chkLinesimplify;
    private JCheckBox chkLinemerge;
    private JCheckBox chkLinesort;
    private JCheckBox chkLinesortTwoOpt;
    private JCheckBox chkReloop;
    private JSpinner spinLinesimplifyTol;
    private JSpinner spinLinemergeTol;
    private MainWindow parent;

    private File currentInputFile;

    private JButton btnProcess;
    private JButton btnSave;
    private JButton btnLoad;
    private JProgressBar progressBar;
    private JLabel lblStats;
    private JSpinner spinSimplify;

    // Debounce timer for auto-preview
    private Timer debounceTimer;

    public ControlPanel(MainWindow parent) {
        this.parent = parent;
        setLayout(new BorderLayout(0, 0));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setPreferredSize(new Dimension(560, 0));

        // Initialize debounce timer (single-shot, fires updatePreview)
        debounceTimer = new Timer(DEBOUNCE_DELAY_MS, e -> updatePreview());
        debounceTimer.setRepeats(false);

        JPanel topSection = new JPanel();
        topSection.setLayout(new BoxLayout(topSection, BoxLayout.Y_AXIS));
        topSection.add(createOptionsBar());
        topSection.add(Box.createVerticalStrut(4));
        topSection.add(createGeometrySettings());
        topSection.add(Box.createVerticalStrut(4));
        topSection.add(createPathOptimizationSettings());

        add(topSection, BorderLayout.NORTH);

        // Layer settings in a scroll pane (center, takes remaining space)
        JScrollPane scroll = new JScrollPane(createLayerSettings());
        scroll.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(4, 0, 4, 0),
                BorderFactory.createTitledBorder("Layer Settings")));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        // Bottom section
        JPanel bottomSection = new JPanel();
        bottomSection.setLayout(new BoxLayout(bottomSection, BoxLayout.Y_AXIS));
        bottomSection.setBorder(new EmptyBorder(4, 0, 0, 0));

        btnProcess = new JButton("Update Preview");
        btnProcess.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnProcess.putClientProperty("JButton.buttonType", "default");
        btnProcess.addActionListener(e -> updatePreview());
        bottomSection.add(btnProcess);
        bottomSection.add(Box.createVerticalStrut(4));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(0, 16));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        bottomSection.add(progressBar);
        bottomSection.add(Box.createVerticalStrut(4));

        // Statistics label
        lblStats = new JLabel(" ");
        lblStats.setFont(lblStats.getFont().deriveFont(Font.PLAIN, 10f));
        lblStats.setForeground(UIManager.getColor("Label.disabledForeground"));
        lblStats.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblStats.setHorizontalAlignment(SwingConstants.CENTER);
        bottomSection.add(lblStats);
        bottomSection.add(Box.createVerticalStrut(4));

        JPanel pnlButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnLoad = new JButton("Load SVG...");
        btnLoad.addActionListener(e -> loadSvg());
        btnSave = new JButton("Save As...");
        btnSave.addActionListener(e -> saveOutput());
        pnlButtons.add(btnLoad);
        pnlButtons.add(btnSave);
        bottomSection.add(pnlButtons);

        add(bottomSection, BorderLayout.SOUTH);

        setupKeyboardShortcuts();
    }

    /** Schedule a debounced preview update. */
    private void schedulePreviewUpdate() {
        debounceTimer.restart();
    }

    private void setupKeyboardShortcuts() {
        // Ctrl+O: Load SVG
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("control O"), "loadSvg");
        getActionMap().put("loadSvg", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) { loadSvg(); }
        });

        // Ctrl+S: Save As
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("control S"), "saveSvg");
        getActionMap().put("saveSvg", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) { saveOutput(); }
        });

        // Ctrl+Enter: Update Preview
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("control ENTER"), "updatePreview");
        getActionMap().put("updatePreview", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) { updatePreview(); }
        });
    }

    private void setControlsEnabled(boolean b) {
        btnProcess.setEnabled(b);
        btnSave.setEnabled(b);
        btnProcess.setText(b ? "Update Preview" : "Processing...");
        progressBar.setVisible(!b);
    }

    private void setProgress(int percent, String label) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setValue(percent);
            progressBar.setString(label);
        });
    }

    private JPanel createOptionsBar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        p.setBorder(new TitledBorder("Options"));
        chkEnableHatching = new JCheckBox("Enable Hatching", true);
        chkEnableHatching.addItemListener(e -> schedulePreviewUpdate());
        p.add(chkEnableHatching);
        chkOptimize = new JCheckBox("Optimize Path Travel", false);
        chkOptimize.addItemListener(e -> schedulePreviewUpdate());
        p.add(chkOptimize);
        return p;
    }

    private JComboBox<String> cmbCrop;
    private JButton btnRotate90;
    private double currentRotation = 0;

    private JPanel createGeometrySettings() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        p.setBorder(new TitledBorder("Layout & Geometry"));

        p.add(new JLabel("Rotate:"));
        btnRotate90 = new JButton("90\u00B0");
        btnRotate90.addActionListener(e -> {
            currentRotation = (currentRotation + 90) % 360;
            updatePreview();
        });
        p.add(btnRotate90);

        p.add(Box.createHorizontalStrut(10));
        p.add(new JLabel("Crop:"));
        cmbCrop = new JComboBox<>(new String[] { "None", "Current View", "A4", "Letter", "500x500" });
        cmbCrop.addActionListener(e -> schedulePreviewUpdate());
        p.add(cmbCrop);

        p.add(Box.createHorizontalStrut(10));
        p.add(new JLabel("Simplify:"));
        spinSimplify = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 10.0, 0.1));
        spinSimplify.setPreferredSize(new Dimension(60, 24));
        spinSimplify.setToolTipText("Path simplification tolerance (0 = off)");
        spinSimplify.addChangeListener(e -> schedulePreviewUpdate());
        p.add(spinSimplify);

        return p;
    }

    private JPanel createPathOptimizationSettings() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(new TitledBorder("Path Optimization"));

        chkLinesimplify = new JCheckBox("Linesimplify", false);
        chkLinesimplify.addItemListener(e -> schedulePreviewUpdate());
        p.add(chkLinesimplify);

        p.add(new JLabel("Tol:"));
        spinLinesimplifyTol = new JSpinner(new SpinnerNumberModel(0.378, 0.01, 5.0, 0.05));
        spinLinesimplifyTol.setPreferredSize(new Dimension(62, 24));
        spinLinesimplifyTol.setToolTipText("Linesimplify tolerance");
        spinLinesimplifyTol.addChangeListener(e -> schedulePreviewUpdate());
        p.add(spinLinesimplifyTol);

        chkLinemerge = new JCheckBox("Linemerge", false);
        chkLinemerge.addItemListener(e -> schedulePreviewUpdate());
        p.add(chkLinemerge);

        p.add(new JLabel("Tol:"));
        spinLinemergeTol = new JSpinner(new SpinnerNumberModel(1.89, 0.1, 10.0, 0.1));
        spinLinemergeTol.setPreferredSize(new Dimension(62, 24));
        spinLinemergeTol.setToolTipText("Linemerge tolerance");
        spinLinemergeTol.addChangeListener(e -> schedulePreviewUpdate());
        p.add(spinLinemergeTol);

        chkLinesort = new JCheckBox("Linesort", false);
        chkLinesort.addItemListener(e -> {
            chkLinesortTwoOpt.setEnabled(chkLinesort.isSelected());
            schedulePreviewUpdate();
        });
        p.add(chkLinesort);

        chkLinesortTwoOpt = new JCheckBox("2-opt", false);
        chkLinesortTwoOpt.setEnabled(false);
        chkLinesortTwoOpt.addItemListener(e -> schedulePreviewUpdate());
        p.add(chkLinesortTwoOpt);

        chkReloop = new JCheckBox("Reloop", false);
        chkReloop.addItemListener(e -> schedulePreviewUpdate());
        p.add(chkReloop);

        return p;
    }

    private void loadSvg() {
        java.awt.FileDialog fd = new java.awt.FileDialog(parent, "Load SVG...", java.awt.FileDialog.LOAD);
        fd.setFile("*.svg");
        if (currentInputFile != null) {
            fd.setDirectory(currentInputFile.getParent());
        }

        fd.setVisible(true);
        if (fd.getFile() != null) {
            File selected = new File(fd.getDirectory(), fd.getFile());
            parent.setInputFile(selected);
        }
    }

    public void setInputFile(File f) {
        this.currentInputFile = f;
        refreshLayerSettings();
        updatePreview();
    }

    private JPanel layerSettingsPanel;

    // UI elements per layer: layerId -> LayerWidgets
    private java.util.Map<String, LayerWidgets> layerWidgets = new java.util.LinkedHashMap<>();
    // Map layerId -> LayerInfo for resolving colors in buildConfigFromGui
    private java.util.Map<String, org.trostheide.svgtoolbox.core.SvgAnalyzer.LayerInfo> layerInfoMap = new java.util.LinkedHashMap<>();

    private static class LayerWidgets {
        JCheckBox chkExport;
        JComboBox<String> cmbPattern;
        JSpinner spinAngle;
        JSpinner spinGap;
        JSpinner spinStrokeWidth;
    }

    private JPanel createLayerSettings() {
        layerSettingsPanel = new JPanel();
        layerSettingsPanel.setLayout(new BoxLayout(layerSettingsPanel, BoxLayout.Y_AXIS));
        return layerSettingsPanel;
    }

    private void refreshLayerSettings() {
        layerSettingsPanel.removeAll();
        layerWidgets.clear();
        layerInfoMap.clear();

        if (currentInputFile == null) return;

        try {
            java.util.List<org.trostheide.svgtoolbox.core.SvgAnalyzer.LayerInfo> layers =
                org.trostheide.svgtoolbox.core.SvgAnalyzer.extractLayers(currentInputFile);

            if (layers.isEmpty()) {
                JLabel lblEmpty = new JLabel("No layers found.");
                lblEmpty.setBorder(new EmptyBorder(12, 12, 12, 12));
                layerSettingsPanel.add(lblEmpty);
            } else {
                // Column header
                layerSettingsPanel.add(createLayerHeader());
                layerSettingsPanel.add(new JSeparator(SwingConstants.HORIZONTAL));

                int index = 0;
                for (org.trostheide.svgtoolbox.core.SvgAnalyzer.LayerInfo layer : layers) {
                    JPanel row = createLayerRow(layer, index);
                    layerSettingsPanel.add(row);
                    index++;
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting layers: " + e.getMessage());
            JLabel lblErr = new JLabel("Error loading layers");
            lblErr.setBorder(new EmptyBorder(12, 12, 12, 12));
            layerSettingsPanel.add(lblErr);
        }

        layerSettingsPanel.revalidate();
        layerSettingsPanel.repaint();
    }

    private JPanel createLayerHeader() {
        JPanel header = new JPanel(new GridBagLayout());
        header.setBorder(new EmptyBorder(4, 6, 2, 6));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 2, 0, 2);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;

        Font headerFont = UIManager.getFont("Label.font").deriveFont(Font.BOLD, 10f);

        gbc.gridx = 0; gbc.weightx = 0;
        header.add(makeHeaderLabel("", headerFont, 20), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        header.add(makeHeaderLabel("Layer", headerFont, 0), gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        header.add(makeHeaderLabel("Vis", headerFont, 0), gbc);
        gbc.gridx = 3;
        header.add(makeHeaderLabel("Pattern", headerFont, 0), gbc);
        gbc.gridx = 4;
        header.add(makeHeaderLabel("Angle", headerFont, 0), gbc);
        gbc.gridx = 5;
        header.add(makeHeaderLabel("Gap", headerFont, 0), gbc);
        gbc.gridx = 6;
        header.add(makeHeaderLabel("Width", headerFont, 0), gbc);

        return header;
    }

    private JLabel makeHeaderLabel(String text, Font font, int fixedWidth) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(font);
        lbl.setForeground(UIManager.getColor("Label.disabledForeground"));
        if (fixedWidth > 0) {
            lbl.setPreferredSize(new Dimension(fixedWidth, 14));
        }
        return lbl;
    }

    private JPanel createLayerRow(org.trostheide.svgtoolbox.core.SvgAnalyzer.LayerInfo layer, int index) {
        String colorHex = layer.primaryColor;

        JPanel row = new JPanel(new GridBagLayout());
        row.setBorder(new EmptyBorder(3, 6, 3, 6));

        // Zebra striping
        if (index % 2 == 1) {
            Color stripe = UIManager.getColor("Table.alternateRowColor");
            if (stripe == null) {
                stripe = new Color(0, 0, 0, 12);
            }
            row.setOpaque(true);
            row.setBackground(stripe);
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(1, 2, 1, 2);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridy = 0;

        // Color swatch
        gbc.gridx = 0; gbc.weightx = 0;
        JPanel colorBox = new JPanel();
        colorBox.setPreferredSize(new Dimension(20, 20));
        colorBox.setMinimumSize(new Dimension(20, 20));
        colorBox.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1));
        try {
            colorBox.setBackground(Color.decode(colorHex));
        } catch (Exception e) { /* ignore */ }
        row.add(colorBox, gbc);

        // Layer name
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        String tooltip = layer.id + " \u2014 colors: " + String.join(", ", layer.colors);
        JLabel lblName = new JLabel(layer.label);
        lblName.setToolTipText(tooltip);
        lblName.setFont(lblName.getFont().deriveFont(Font.BOLD));
        row.add(lblName, gbc);
        gbc.fill = GridBagConstraints.NONE;

        // Export Checkbox
        gbc.gridx = 2; gbc.weightx = 0;
        JCheckBox chkExport = new JCheckBox();
        chkExport.setSelected(true);
        chkExport.setToolTipText("Include this layer in output");
        chkExport.addItemListener(e -> schedulePreviewUpdate());
        row.add(chkExport, gbc);

        // Pattern
        gbc.gridx = 3;
        JComboBox<String> cmbPat = new JComboBox<>(PATTERN_OPTIONS);
        cmbPat.setSelectedItem(DEFAULT_PATTERN);
        cmbPat.setPreferredSize(new Dimension(82, 24));
        cmbPat.addActionListener(e -> schedulePreviewUpdate());
        row.add(cmbPat, gbc);

        // Angle
        gbc.gridx = 4;
        JSpinner spinAngle = new JSpinner(new SpinnerNumberModel(DEFAULT_ANGLE, 0.0, 360.0, 5.0));
        spinAngle.setPreferredSize(new Dimension(62, 24));
        spinAngle.setToolTipText("Hatch angle (\u00B0)");
        spinAngle.addChangeListener(e -> schedulePreviewUpdate());
        row.add(spinAngle, gbc);

        // Gap
        gbc.gridx = 5;
        JSpinner spinGap = new JSpinner(new SpinnerNumberModel(DEFAULT_GAP, 0.1, 50.0, 0.5));
        spinGap.setPreferredSize(new Dimension(60, 24));
        spinGap.setToolTipText("Hatch gap (px)");
        spinGap.addChangeListener(e -> schedulePreviewUpdate());
        row.add(spinGap, gbc);

        // Stroke Width
        gbc.gridx = 6;
        JSpinner spinWidth = new JSpinner(new SpinnerNumberModel(
                (double) DEFAULT_STROKE_WIDTH, 0.1, 5.0, 0.1));
        spinWidth.setPreferredSize(new Dimension(60, 24));
        spinWidth.setToolTipText("Stroke width (px)");
        spinWidth.addChangeListener(e -> schedulePreviewUpdate());
        row.add(spinWidth, gbc);

        // Store widgets
        LayerWidgets widgets = new LayerWidgets();
        widgets.chkExport = chkExport;
        widgets.cmbPattern = cmbPat;
        widgets.spinAngle = spinAngle;
        widgets.spinGap = spinGap;
        widgets.spinStrokeWidth = spinWidth;
        layerWidgets.put(layer.id, widgets);
        layerInfoMap.put(layer.id, layer);

        return row;
    }

    private Config buildConfigFromGui(File tempOut) {
        String cropVal = (String) cmbCrop.getSelectedItem();
        java.awt.geom.Rectangle2D cropRect = null;

        if ("Current View".equals(cropVal)) {
            cropRect = parent.getPreviewPanel().getViewportBounds();
        } else if (!"None".equals(cropVal)) {
            cropRect = SvgToolboxRunner.parseCrop(cropVal);
        }

        double simplifyTol = ((Number) spinSimplify.getValue()).doubleValue();

        java.util.Map<String, org.trostheide.svgtoolbox.HatchStyle> styleOverrides = new java.util.HashMap<>();
        java.util.Map<String, Float> strokeWidthOverrides = new java.util.HashMap<>();
        java.util.List<String> hiddenLayers = new java.util.ArrayList<>();
        java.util.List<Color> noHatchColors = new java.util.ArrayList<>();

        for (java.util.Map.Entry<String, LayerWidgets> entry : layerWidgets.entrySet()) {
            String layerId = entry.getKey();
            LayerWidgets lw = entry.getValue();

            org.trostheide.svgtoolbox.core.SvgAnalyzer.LayerInfo info = layerInfoMap.get(layerId);
            java.util.Set<String> layerColors = (info != null) ? info.colors : java.util.Collections.singleton(layerId);

            if (!lw.chkExport.isSelected()) {
                for (String c : layerColors) { hiddenLayers.add(c); }
                continue;
            }

            float width = ((Number) lw.spinStrokeWidth.getValue()).floatValue();
            for (String c : layerColors) { strokeWidthOverrides.put(c, width); }

            String pat = (String) lw.cmbPattern.getSelectedItem();
            double angle = ((Number) lw.spinAngle.getValue()).doubleValue();
            double gap = ((Number) lw.spinGap.getValue()).doubleValue();

            if ("none".equals(pat)) {
                for (String c : layerColors) {
                    try { noHatchColors.add(Color.decode(c)); } catch(Exception ignored) {}
                }
            } else {
                for (String c : layerColors) {
                    styleOverrides.put(c, new org.trostheide.svgtoolbox.HatchStyle(angle, gap, pat));
                }
            }
        }

        return new Config.Builder()
                .inputPath(currentInputFile.getAbsolutePath())
                .outputPath(tempOut.getAbsolutePath())
                .strokeWidth(DEFAULT_STROKE_WIDTH)
                .enableHatching(chkEnableHatching.isSelected())
                .hatchPattern(DEFAULT_PATTERN)
                .hatchAngle(DEFAULT_ANGLE)
                .hatchGap(DEFAULT_GAP)
                .rotationDegrees(currentRotation)
                .cropBounds(cropRect)
                .optimizePaths(chkOptimize.isSelected())
                .linesimplify(chkLinesimplify.isSelected())
                .linesimplifyTolerance(((Number) spinLinesimplifyTol.getValue()).doubleValue())
                .linemerge(chkLinemerge.isSelected())
                .linemergeTolerance(((Number) spinLinemergeTol.getValue()).doubleValue())
                .linesort(chkLinesort.isSelected())
                .linesortTwoOpt(chkLinesortTwoOpt.isSelected())
                .reloop(chkReloop.isSelected())
                .hiddenLayers(hiddenLayers)
                .strokeWidthOverrides(strokeWidthOverrides)
                .overrides(styleOverrides)
                .noHatchColors(noHatchColors)
                .simplifyTolerance(simplifyTol)
                .globalStyle(new org.trostheide.svgtoolbox.HatchStyle(DEFAULT_ANGLE, DEFAULT_GAP, DEFAULT_PATTERN))
                .build();
    }

    private void updatePreview() {
        if (currentInputFile == null)
            return;

        try {
            final File tempOut = File.createTempFile("preview_", ".svg");
            tempOut.deleteOnExit();
            final Config config = buildConfigFromGui(tempOut);

            setControlsEnabled(false);
            setProgress(0, "Starting...");

            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    SvgToolboxRunner.processPipeline(config, (step, total, name) -> {
                        int pct = (int) ((step * 100.0) / total);
                        ControlPanel.this.setProgress(pct, String.format("Step %d/%d: %s", step, total, name));
                    });
                    return null;
                }

                @Override
                protected void done() {
                    setControlsEnabled(true);
                    try {
                        get();
                        parent.getPreviewPanel().loadFile(tempOut);
                        updateStats(tempOut);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        JOptionPane.showMessageDialog(ControlPanel.this,
                                "Error processing SVG: " + msg,
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error setup: " + e.getMessage());
        }
    }

    private void updateStats(File outputFile) {
        try {
            String parser = org.apache.batik.util.XMLResourceDescriptor.getXMLParserClassName();
            org.apache.batik.anim.dom.SAXSVGDocumentFactory factory =
                    new org.apache.batik.anim.dom.SAXSVGDocumentFactory(parser);
            org.w3c.dom.Document doc = factory.createDocument(outputFile.toURI().toString());
            SvgStatistics.Stats stats = SvgStatistics.analyze(doc);
            lblStats.setText(String.format("%d elements | %.2f m total path length | ~%.0f min @ 50mm/s",
                    stats.elementCount(), stats.totalLengthMeters(),
                    (stats.totalLengthMeters() / 0.05) / 60.0));
        } catch (Exception e) {
            lblStats.setText(" ");
        }
    }

    private void saveOutput() {
        if (currentInputFile == null)
            return;

        JFileChooser fc = new JFileChooser();
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                final Config config = buildConfigFromGui(fc.getSelectedFile());

                setControlsEnabled(false);

                SwingWorker<Void, Void> worker = new SwingWorker<>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        SvgToolboxRunner.processPipeline(config);
                        return null;
                    }

                    @Override
                    protected void done() {
                        setControlsEnabled(true);
                        try {
                            get();
                            JOptionPane.showMessageDialog(ControlPanel.this,
                                    "Saved to " + fc.getSelectedFile().getName());
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            JOptionPane.showMessageDialog(ControlPanel.this,
                                    "Error saving SVG: " + ex.getCause().getMessage(),
                                    "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                };
                worker.execute();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error setup: " + e.getMessage());
            }
        }
    }
}
