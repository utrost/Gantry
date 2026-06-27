package org.trostheide.gantry.vectorize.gui;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

public class ControlsPanel extends JPanel {

    public interface ParameterChangeListener {
        void onParameterChanged();
    }

    private ParameterChangeListener listener;

    private JComboBox<String> strategyCombo;

    // Polyline Params
    private JSlider toleranceSlider;
    private JLabel toleranceLabel;

    // Bezier Params — logarithmic slider (positions 1-8 → values 2,4,8,16,32,64,128,256)
    private JSlider colorsSlider;
    private JLabel colorsLabel;

    // Detail, Blur, Speckle
    private JSlider detailSlider;
    private JLabel detailLabel;
    private JSlider blurSlider;
    private JLabel blurLabel;
    private JSlider speckleSlider;
    private JLabel speckleLabel;

    // Centerline Params
    private JSlider clThresholdSlider;
    private JLabel clThresholdLabel;

    // Canny Threshold Params
    private JSlider cannyLowSlider;
    private JLabel cannyLowLabel;
    private JSlider cannyHighSlider;
    private JLabel cannyHighLabel;

    // Bezier2-specific params
    private JSlider b2LtresSlider;
    private JLabel b2LtresLabel;
    private JSlider b2QtresSlider;
    private JLabel b2QtresLabel;
    private JCheckBox b2OutlineCheck;

    // Stroke Style Params
    private JComboBox<String> strokeColorCombo;
    private JLabel strokeColorLabel;
    private JSlider strokeWidthSlider;
    private JLabel strokeWidthLabel;

    // Smooth Curves
    private JCheckBox smoothCurvesCheck;

    // Preset selector
    private JComboBox<String> presetCombo;

    // Flag to suppress change events during preset application
    private boolean suppressChanges = false;

    // Image dimensions for scaling min size
    private int imageWidth = 0;
    private int imageHeight = 0;

    // Grouped panels for visibility control
    private JPanel parametersPanel;
    private JPanel filteringPanel;
    private JPanel stylePanel;

    // Custom stroke color (hex)
    private String customStrokeColor = null;

    // Auto Canny checkbox
    private JCheckBox cannyAutoCheck;

    // Color-aware Canny checkbox
    private JCheckBox colorEdgesCheck;

    // Paint by Numbers controls
    private JButton pbnPaletteButton;
    private JLabel pbnPaletteLabel;
    private JSlider pbnMinAreaSlider;
    private JLabel pbnMinAreaLabel;
    private JSlider pbnFontSizeSlider;
    private JLabel pbnFontSizeLabel;
    private JCheckBox pbnShowNumbers;
    private JCheckBox pbnShowLegend;
    private Color[] pbnPalette;
    private JButton pbnAutoButton;

    private java.awt.image.BufferedImage sourceImage;
    private SnapshotManager snapshotManager;

    public ControlsPanel(ParameterChangeListener listener) {
        this.listener = listener;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // === Preset Section ===
        JPanel presetPanel = createSection("Preset");
        presetCombo = new JComboBox<>(new String[] {
                "(custom)", "Line Art", "Photo - Detailed", "Photo - Simplified", "Logo", "Sketch", "Paint by Numbers"
        });
        presetCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        presetCombo.addActionListener(e -> applyPreset((String) presetCombo.getSelectedItem()));
        presetPanel.add(presetCombo);
        add(presetPanel);
        add(Box.createVerticalStrut(8));

        // === Strategy Section ===
        JPanel strategyPanel = createSection("Strategy");
        strategyCombo = new JComboBox<>(new String[] { "dp", "line", "raw", "convexhull", "bezier", "bezier2", "centerline", "pbn" });
        strategyCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        strategyCombo.addActionListener(e -> updateControls());
        strategyCombo.addActionListener(e -> fireChange());
        strategyPanel.add(strategyCombo);
        add(strategyPanel);
        add(Box.createVerticalStrut(8));

        // === Parameters Section ===
        parametersPanel = createSection("Parameters");

        // Tolerance (DP / Centerline smoothness)
        toleranceLabel = new JLabel("Tolerance: 2.0");
        parametersPanel.add(toleranceLabel);
        toleranceSlider = new JSlider(5, 100, 20);
        toleranceSlider.addChangeListener(e -> {
            boolean isCenterline = "centerline".equals(getStrategy());
            String label = isCenterline ? "Smoothness" : "Tolerance";
            toleranceLabel.setText(String.format("%s: %.1f", label, toleranceSlider.getValue() / 10.0));
            fireChange();
        });
        parametersPanel.add(toleranceSlider);

        // Colors — logarithmic slider with tick labels
        colorsLabel = new JLabel("Colors: 16");
        parametersPanel.add(colorsLabel);
        colorsSlider = new JSlider(1, 8, 4); // position 4 = 2^4 = 16
        colorsSlider.setMajorTickSpacing(1);
        colorsSlider.setPaintTicks(true);
        colorsSlider.setPaintLabels(true);
        colorsSlider.setSnapToTicks(true);
        Hashtable<Integer, JLabel> colorLabels = new Hashtable<>();
        for (int i = 1; i <= 8; i++) {
            colorLabels.put(i, new JLabel(String.valueOf(1 << i)));
        }
        colorsSlider.setLabelTable(colorLabels);
        colorsSlider.addChangeListener(e -> {
            int colors = 1 << colorsSlider.getValue();
            colorsLabel.setText("Colors: " + colors);
            fireChange();
        });
        parametersPanel.add(colorsSlider);

        // Detail / Smoothness
        detailLabel = new JLabel("Detail/Smoothness");
        parametersPanel.add(detailLabel);
        detailSlider = new JSlider(0, 100, 10);
        detailSlider.addChangeListener(e -> {
            String strat = getStrategy();
            if ("bezier".equals(strat)) {
                detailLabel.setText("Smoothness: " + detailSlider.getValue());
            } else {
                // Contour: 0-10 maps to 0.0-1.0 (clamped)
                double factor = Math.min(1.0, detailSlider.getValue() / 10.0);
                detailLabel.setText(String.format("Detail: %.1f", factor));
            }
            fireChange();
        });
        parametersPanel.add(detailSlider);

        // Blur
        blurLabel = new JLabel("Blur: 1.5");
        parametersPanel.add(blurLabel);
        blurSlider = new JSlider(1, 50, 15);
        blurSlider.addChangeListener(e -> {
            blurLabel.setText(String.format("Blur: %.1f", blurSlider.getValue() / 10.0));
            fireChange();
        });
        parametersPanel.add(blurSlider);

        // Bezier2: Line threshold
        b2LtresLabel = new JLabel("Line Threshold: 1.0");
        parametersPanel.add(b2LtresLabel);
        b2LtresSlider = new JSlider(1, 50, 10); // 0.1 to 5.0 (scaled by 10)
        b2LtresSlider.addChangeListener(e -> {
            b2LtresLabel.setText(String.format("Line Threshold: %.1f", b2LtresSlider.getValue() / 10.0));
            fireChange();
        });
        parametersPanel.add(b2LtresSlider);

        // Bezier2: Quadratic threshold
        b2QtresLabel = new JLabel("Curve Threshold: 1.0");
        parametersPanel.add(b2QtresLabel);
        b2QtresSlider = new JSlider(1, 50, 10); // 0.1 to 5.0 (scaled by 10)
        b2QtresSlider.addChangeListener(e -> {
            b2QtresLabel.setText(String.format("Curve Threshold: %.1f", b2QtresSlider.getValue() / 10.0));
            fireChange();
        });
        parametersPanel.add(b2QtresSlider);

        // Centerline Threshold
        clThresholdLabel = new JLabel("Threshold: 128");
        parametersPanel.add(clThresholdLabel);
        clThresholdSlider = new JSlider(0, 255, 128);
        clThresholdSlider.addChangeListener(e -> {
            clThresholdLabel.setText("Threshold: " + clThresholdSlider.getValue());
            fireChange();
        });
        parametersPanel.add(clThresholdSlider);

        // PBN: Palette button
        pbnPalette = new Color[6];
        for (int i = 0; i < 6; i++) {
            pbnPalette[i] = Color.getHSBColor(i / 6f, 0.8f, 0.9f);
        }
        pbnPaletteLabel = new JLabel("Palette: 6 colors");
        parametersPanel.add(pbnPaletteLabel);
        pbnPaletteButton = new JButton("Edit Palette...");
        pbnPaletteButton.addActionListener(e -> {
            Color[] result = PaletteEditorDialog.showDialog(
                    SwingUtilities.getWindowAncestor(this), pbnPalette, sourceImage);
            if (result != null) {
                pbnPalette = result;
                pbnPaletteLabel.setText("Palette: " + pbnPalette.length + " colors");
                fireChange();
            }
        });
        parametersPanel.add(pbnPaletteButton);

        pbnAutoButton = new JButton("Auto-detect Palette");
        pbnAutoButton.setToolTipText("Extract palette colors from the loaded image using k-means clustering");
        pbnAutoButton.addActionListener(e -> {
            if (sourceImage == null) {
                JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                        "Load an image first.", "No Image", JOptionPane.WARNING_MESSAGE);
                return;
            }
            pbnPalette = org.trostheide.gantry.vectorize.PaintByNumbersProcessor.extractPalette(
                    sourceImage, pbnPalette.length);
            pbnPaletteLabel.setText("Palette: " + pbnPalette.length + " colors (auto)");
            fireChange();
        });
        parametersPanel.add(pbnAutoButton);

        // PBN: Min Area
        pbnMinAreaLabel = new JLabel("Min Area: 100");
        parametersPanel.add(pbnMinAreaLabel);
        pbnMinAreaSlider = new JSlider(10, 5000, 100);
        pbnMinAreaSlider.addChangeListener(e -> {
            pbnMinAreaLabel.setText("Min Area: " + pbnMinAreaSlider.getValue());
            fireChange();
        });
        parametersPanel.add(pbnMinAreaSlider);

        // PBN: Font Size
        pbnFontSizeLabel = new JLabel("Font Size: 14");
        parametersPanel.add(pbnFontSizeLabel);
        pbnFontSizeSlider = new JSlider(6, 48, 14);
        pbnFontSizeSlider.addChangeListener(e -> {
            pbnFontSizeLabel.setText("Font Size: " + pbnFontSizeSlider.getValue());
            fireChange();
        });
        parametersPanel.add(pbnFontSizeSlider);

        // PBN: Show Numbers
        pbnShowNumbers = new JCheckBox("Show Numbers", true);
        pbnShowNumbers.addActionListener(e -> fireChange());
        parametersPanel.add(pbnShowNumbers);

        // PBN: Show Legend
        pbnShowLegend = new JCheckBox("Show Legend", true);
        pbnShowLegend.addActionListener(e -> fireChange());
        parametersPanel.add(pbnShowLegend);

        add(parametersPanel);
        add(Box.createVerticalStrut(8));

        // === Filtering Section ===
        filteringPanel = createSection("Filtering");

        // Min Size / Speckle
        speckleLabel = new JLabel("Min Size: 5");
        filteringPanel.add(speckleLabel);
        speckleSlider = new JSlider(0, 100, 5);
        speckleSlider.addChangeListener(e -> {
            updateSpeckleLabel();
            fireChange();
        });
        filteringPanel.add(speckleSlider);

        // Auto Canny checkbox
        cannyAutoCheck = new JCheckBox("Auto Canny Thresholds", false);
        cannyAutoCheck.addActionListener(e -> {
            boolean auto = cannyAutoCheck.isSelected();
            cannyLowSlider.setEnabled(!auto);
            cannyHighSlider.setEnabled(!auto);
            fireChange();
        });
        filteringPanel.add(cannyAutoCheck);

        // Color-aware Canny checkbox
        colorEdgesCheck = new JCheckBox("Color-Aware Edges", false);
        colorEdgesCheck.setToolTipText("Run Canny on R/G/B channels separately to catch hue-boundary edges");
        colorEdgesCheck.addActionListener(e -> fireChange());
        filteringPanel.add(colorEdgesCheck);

        // Canny Low
        cannyLowLabel = new JLabel("Canny Low: 0.020");
        filteringPanel.add(cannyLowLabel);
        cannyLowSlider = new JSlider(1, 500, 20);
        cannyLowSlider.addChangeListener(e -> {
            cannyLowLabel.setText(String.format("Canny Low: %.3f", cannyLowSlider.getValue() / 1000.0));
            fireChange();
        });
        filteringPanel.add(cannyLowSlider);

        // Canny High
        cannyHighLabel = new JLabel("Canny High: 0.100");
        filteringPanel.add(cannyHighLabel);
        cannyHighSlider = new JSlider(1, 500, 100);
        cannyHighSlider.addChangeListener(e -> {
            cannyHighLabel.setText(String.format("Canny High: %.3f", cannyHighSlider.getValue() / 1000.0));
            fireChange();
        });
        filteringPanel.add(cannyHighSlider);

        add(filteringPanel);
        add(Box.createVerticalStrut(8));

        // === Output Style Section ===
        stylePanel = createSection("Output Style");

        b2OutlineCheck = new JCheckBox("Outline Mode", false);
        b2OutlineCheck.addActionListener(e -> fireChange());
        stylePanel.add(b2OutlineCheck);

        smoothCurvesCheck = new JCheckBox("Smooth Curves", false);
        smoothCurvesCheck.addActionListener(e -> fireChange());
        stylePanel.add(smoothCurvesCheck);

        strokeColorLabel = new JLabel("Stroke Color:");
        stylePanel.add(strokeColorLabel);
        strokeColorCombo = new JComboBox<>(new String[] { "black", "white", "red", "blue", "green", "gray", "Custom..." });
        strokeColorCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        strokeColorCombo.addActionListener(e -> {
            if ("Custom...".equals(strokeColorCombo.getSelectedItem())) {
                Color chosen = JColorChooser.showDialog(this, "Choose Stroke Color", Color.BLACK);
                if (chosen != null) {
                    customStrokeColor = String.format("#%02x%02x%02x", chosen.getRed(), chosen.getGreen(), chosen.getBlue());
                } else {
                    // User cancelled — revert to first item
                    suppressChanges = true;
                    strokeColorCombo.setSelectedIndex(0);
                    suppressChanges = false;
                    return;
                }
            } else {
                customStrokeColor = null;
            }
            fireChange();
        });
        stylePanel.add(strokeColorCombo);

        strokeWidthLabel = new JLabel("Stroke Width: 1.0");
        stylePanel.add(strokeWidthLabel);
        strokeWidthSlider = new JSlider(1, 50, 10);
        strokeWidthSlider.addChangeListener(e -> {
            strokeWidthLabel.setText(String.format("Stroke Width: %.1f", strokeWidthSlider.getValue() / 10.0));
            fireChange();
        });
        stylePanel.add(strokeWidthSlider);

        add(stylePanel);
        add(Box.createVerticalGlue());

        snapshotManager = new SnapshotManager(this::getSnapshot, this::applySnapshot);
        updateControls();
    }

    private JPanel createSection(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true),
                        " " + title + " ",
                        javax.swing.border.TitledBorder.LEADING,
                        javax.swing.border.TitledBorder.TOP,
                        UIManager.getFont("Label.font").deriveFont(Font.BOLD, 11f)),
                BorderFactory.createEmptyBorder(6, 8, 8, 8)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    public void setSourceImage(java.awt.image.BufferedImage image) {
        this.sourceImage = image;
    }

    public void setImageSize(int width, int height) {
        this.imageWidth = width;
        this.imageHeight = height;

        // Auto-set sensible min size default based on image diagonal
        double diagonal = Math.sqrt((double) width * width + (double) height * height);
        int autoMin = Math.max(1, (int) (diagonal / 200.0));
        autoMin = Math.min(autoMin, 100); // clamp to slider max

        suppressChanges = true;
        speckleSlider.setValue(autoMin);
        suppressChanges = false;
        updateSpeckleLabel();
    }

    private void updateSpeckleLabel() {
        int value = speckleSlider.getValue();
        if (imageWidth > 0 && imageHeight > 0) {
            double diagonal = Math.sqrt((double) imageWidth * imageWidth + (double) imageHeight * imageHeight);
            double pct = (value / diagonal) * 100.0;
            speckleLabel.setText(String.format("Min Size: %d (%.1f%%)", value, pct));
        } else {
            speckleLabel.setText("Min Size: " + value);
        }
    }

    private void updateControls() {
        String strategy = (String) strategyCombo.getSelectedItem();
        boolean isDp = "dp".equals(strategy);
        boolean isLine = "line".equals(strategy);
        boolean isRaw = "raw".equals(strategy);
        boolean isHull = "convexhull".equals(strategy);
        boolean isBezier = "bezier".equals(strategy);
        boolean isBezier2 = "bezier2".equals(strategy);
        boolean isCenterline = "centerline".equals(strategy);
        boolean isPbn = "pbn".equals(strategy);
        boolean isContourBase = isDp || isLine || isRaw || isHull;

        // --- Parameters section ---
        toleranceLabel.setVisible(isDp || isCenterline || isPbn);
        toleranceSlider.setVisible(isDp || isCenterline || isPbn);

        colorsLabel.setVisible(isBezier || isBezier2);
        colorsSlider.setVisible(isBezier || isBezier2);

        detailLabel.setVisible(isBezier || isContourBase);
        detailSlider.setVisible(isBezier || isContourBase);
        if (isBezier) {
            detailSlider.setValue(10);
            detailLabel.setText("Smoothness: " + detailSlider.getValue());
        } else if (isContourBase) {
            detailSlider.setValue(10); // 10/10 = 1.0
            detailLabel.setText("Detail: 1.0");
        }

        blurLabel.setVisible(isContourBase);
        blurSlider.setVisible(isContourBase);

        b2LtresLabel.setVisible(isBezier2);
        b2LtresSlider.setVisible(isBezier2);
        b2QtresLabel.setVisible(isBezier2);
        b2QtresSlider.setVisible(isBezier2);

        clThresholdLabel.setVisible(isCenterline);
        clThresholdSlider.setVisible(isCenterline);

        // PBN controls
        pbnPaletteLabel.setVisible(isPbn);
        pbnPaletteButton.setVisible(isPbn);
        pbnAutoButton.setVisible(isPbn);
        pbnMinAreaLabel.setVisible(isPbn);
        pbnMinAreaSlider.setVisible(isPbn);
        pbnFontSizeLabel.setVisible(isPbn);
        pbnFontSizeSlider.setVisible(isPbn);
        pbnShowNumbers.setVisible(isPbn);
        pbnShowLegend.setVisible(isPbn);

        // Show parameters section if any child is visible
        parametersPanel.setVisible(isDp || isCenterline || isBezier || isBezier2 || isContourBase || isPbn);

        if (isCenterline) {
            toleranceLabel.setText(String.format("Smoothness: %.1f", toleranceSlider.getValue() / 10.0));
        } else if (isPbn) {
            toleranceLabel.setText(String.format("Tolerance: %.1f", toleranceSlider.getValue() / 10.0));
        }

        // --- Filtering section ---
        speckleLabel.setVisible(isContourBase || isBezier2);
        speckleSlider.setVisible(isContourBase || isBezier2);

        cannyAutoCheck.setVisible(isContourBase);
        colorEdgesCheck.setVisible(isContourBase);
        cannyLowLabel.setVisible(isContourBase);
        cannyLowSlider.setVisible(isContourBase);
        cannyHighLabel.setVisible(isContourBase);
        cannyHighSlider.setVisible(isContourBase);

        filteringPanel.setVisible(isContourBase || isBezier2);

        if (isBezier2) {
            if (imageWidth > 0) {
                // Keep scaled default
            } else {
                speckleSlider.setValue(8);
            }
            updateSpeckleLabel();
        } else if (isContourBase) {
            if (imageWidth == 0) {
                speckleSlider.setValue(5);
            }
            updateSpeckleLabel();
        }

        // --- Style section ---
        b2OutlineCheck.setVisible(isBezier2);
        smoothCurvesCheck.setVisible(isContourBase);

        boolean showStroke = isContourBase || isBezier;
        strokeColorLabel.setVisible(showStroke);
        strokeColorCombo.setVisible(showStroke);
        strokeWidthLabel.setVisible(showStroke);
        strokeWidthSlider.setVisible(showStroke);

        stylePanel.setVisible(isContourBase || isBezier || isBezier2);

        revalidate();
        repaint();
    }

    /**
     * Programmatically selects a preset by combo box index. Used by keyboard shortcuts.
     */
    public void selectPreset(int index) {
        if (index >= 0 && index < presetCombo.getItemCount()) {
            presetCombo.setSelectedIndex(index);
        }
    }

    private void applyPreset(String preset) {
        if ("(custom)".equals(preset)) return;
        Map<String, Object> values = PresetManager.getPresetValues(preset);
        if (values == null) return;

        suppressChanges = true;
        try {
            applyPartialValues(values);
            updateControls();
        } finally {
            suppressChanges = false;
        }
        fireChange();
    }

    private void applyPartialValues(Map<String, Object> values) {
        if (values.containsKey("strategy")) strategyCombo.setSelectedItem(values.get("strategy"));
        if (values.containsKey("tolerance")) toleranceSlider.setValue((int) values.get("tolerance"));
        if (values.containsKey("colors")) colorsSlider.setValue((int) values.get("colors"));
        if (values.containsKey("detail")) detailSlider.setValue((int) values.get("detail"));
        if (values.containsKey("blur")) blurSlider.setValue((int) values.get("blur"));
        if (values.containsKey("speckle")) speckleSlider.setValue((int) values.get("speckle"));
        if (values.containsKey("cannyLow")) cannyLowSlider.setValue((int) values.get("cannyLow"));
        if (values.containsKey("cannyHigh")) cannyHighSlider.setValue((int) values.get("cannyHigh"));
        if (values.containsKey("smoothCurves")) smoothCurvesCheck.setSelected((boolean) values.get("smoothCurves"));
        if (values.containsKey("strokeColor")) strokeColorCombo.setSelectedItem(values.get("strokeColor"));
        if (values.containsKey("strokeWidth")) strokeWidthSlider.setValue((int) values.get("strokeWidth"));
        if (values.containsKey("pbnPalette")) {
            pbnPalette = (Color[]) values.get("pbnPalette");
            pbnPaletteLabel.setText("Palette: " + pbnPalette.length + " colors");
        }
        if (values.containsKey("pbnMinArea")) pbnMinAreaSlider.setValue((int) values.get("pbnMinArea"));
        if (values.containsKey("pbnFontSize")) pbnFontSizeSlider.setValue((int) values.get("pbnFontSize"));
        if (values.containsKey("pbnShowNumbers")) pbnShowNumbers.setSelected((boolean) values.get("pbnShowNumbers"));
        if (values.containsKey("pbnShowLegend")) pbnShowLegend.setSelected((boolean) values.get("pbnShowLegend"));
    }

    private void fireChange() {
        if (suppressChanges) return;
        if (listener != null) {
            listener.onParameterChanged();
        }
    }

    // Getters for vectorization logic
    public String getStrategy() {
        return (String) strategyCombo.getSelectedItem();
    }

    public double getTolerance() {
        return toleranceSlider.getValue() / 10.0;
    }

    public int getColors() {
        return 1 << colorsSlider.getValue();
    }

    public int getDetailRaw() {
        return detailSlider.getValue();
    }

    /** Detail factor for contour strategies, clamped to 0.0–1.0 (matching CLI). */
    public double getDetailFactor() {
        return Math.max(0.0, Math.min(1.0, detailSlider.getValue() / 10.0));
    }

    public double getBlur() {
        return blurSlider.getValue() / 10.0;
    }

    public int getSpeckle() {
        return speckleSlider.getValue();
    }

    public int getClThreshold() {
        return clThresholdSlider.getValue();
    }

    public float getCannyLow() {
        return cannyLowSlider.getValue() / 1000.0f;
    }

    public float getCannyHigh() {
        return cannyHighSlider.getValue() / 1000.0f;
    }

    public String getStrokeColor() {
        if (customStrokeColor != null && "Custom...".equals(strokeColorCombo.getSelectedItem())) {
            return customStrokeColor;
        }
        return (String) strokeColorCombo.getSelectedItem();
    }

    public double getStrokeWidth() {
        return strokeWidthSlider.getValue() / 10.0;
    }

    public boolean isSmoothCurves() {
        return smoothCurvesCheck.isSelected();
    }

    public double getB2Ltres() {
        return b2LtresSlider.getValue() / 10.0;
    }

    public double getB2Qtres() {
        return b2QtresSlider.getValue() / 10.0;
    }

    public boolean isB2Outline() {
        return b2OutlineCheck.isSelected();
    }

    public boolean isCannyAuto() {
        return cannyAutoCheck.isSelected();
    }

    public boolean isColorEdges() {
        return colorEdgesCheck.isSelected();
    }

    // --- Paint by Numbers Getters ---
    public Color[] getPbnPalette() {
        return pbnPalette;
    }

    public int getPbnMinArea() {
        return pbnMinAreaSlider.getValue();
    }

    public int getPbnFontSize() {
        return pbnFontSizeSlider.getValue();
    }

    public boolean isPbnShowNumbers() {
        return pbnShowNumbers.isSelected();
    }

    public boolean isPbnShowLegend() {
        return pbnShowLegend.isSelected();
    }

    /**
     * Updates the Canny threshold labels to display auto-computed values.
     * Called after auto canny computation so the user can see the effective thresholds.
     */
    public void updateCannyDisplay(float low, float high) {
        cannyLowLabel.setText(String.format("Canny Low: %.3f", low));
        cannyHighLabel.setText(String.format("Canny High: %.3f", high));
    }

    // --- Undo/Redo Support ---

    /**
     * Captures the current state of all controls as a snapshot map.
     */
    public Map<String, Object> getSnapshot() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("strategy", strategyCombo.getSelectedItem());
        snap.put("tolerance", toleranceSlider.getValue());
        snap.put("colors", colorsSlider.getValue());
        snap.put("detail", detailSlider.getValue());
        snap.put("blur", blurSlider.getValue());
        snap.put("speckle", speckleSlider.getValue());
        snap.put("clThreshold", clThresholdSlider.getValue());
        snap.put("cannyLow", cannyLowSlider.getValue());
        snap.put("cannyHigh", cannyHighSlider.getValue());
        snap.put("b2Ltres", b2LtresSlider.getValue());
        snap.put("b2Qtres", b2QtresSlider.getValue());
        snap.put("b2Outline", b2OutlineCheck.isSelected());
        snap.put("smoothCurves", smoothCurvesCheck.isSelected());
        snap.put("strokeColor", strokeColorCombo.getSelectedItem());
        snap.put("strokeWidth", strokeWidthSlider.getValue());
        snap.put("cannyAuto", cannyAutoCheck.isSelected());
        snap.put("colorEdges", colorEdgesCheck.isSelected());
        snap.put("customStrokeColor", customStrokeColor);
        snap.put("pbnPalette", pbnPalette != null ? pbnPalette.clone() : null);
        snap.put("pbnMinArea", pbnMinAreaSlider.getValue());
        snap.put("pbnFontSize", pbnFontSizeSlider.getValue());
        snap.put("pbnShowNumbers", pbnShowNumbers.isSelected());
        snap.put("pbnShowLegend", pbnShowLegend.isSelected());
        return snap;
    }

    /**
     * Restores controls from a snapshot map.
     */
    public void applySnapshot(Map<String, Object> snap) {
        suppressChanges = true;
        try {
            strategyCombo.setSelectedItem(snap.get("strategy"));
            toleranceSlider.setValue((Integer) snap.get("tolerance"));
            colorsSlider.setValue((Integer) snap.get("colors"));
            detailSlider.setValue((Integer) snap.get("detail"));
            blurSlider.setValue((Integer) snap.get("blur"));
            speckleSlider.setValue((Integer) snap.get("speckle"));
            clThresholdSlider.setValue((Integer) snap.get("clThreshold"));
            cannyLowSlider.setValue((Integer) snap.get("cannyLow"));
            cannyHighSlider.setValue((Integer) snap.get("cannyHigh"));
            b2LtresSlider.setValue((Integer) snap.get("b2Ltres"));
            b2QtresSlider.setValue((Integer) snap.get("b2Qtres"));
            b2OutlineCheck.setSelected((Boolean) snap.get("b2Outline"));
            smoothCurvesCheck.setSelected((Boolean) snap.get("smoothCurves"));
            strokeColorCombo.setSelectedItem(snap.get("strokeColor"));
            strokeWidthSlider.setValue((Integer) snap.get("strokeWidth"));
            cannyAutoCheck.setSelected((Boolean) snap.get("cannyAuto"));
            colorEdgesCheck.setSelected(snap.containsKey("colorEdges") ? (Boolean) snap.get("colorEdges") : false);
            customStrokeColor = (String) snap.get("customStrokeColor");
            if (snap.containsKey("pbnPalette") && snap.get("pbnPalette") != null) {
                pbnPalette = ((Color[]) snap.get("pbnPalette")).clone();
                pbnPaletteLabel.setText("Palette: " + pbnPalette.length + " colors");
            }
            if (snap.containsKey("pbnMinArea")) pbnMinAreaSlider.setValue((Integer) snap.get("pbnMinArea"));
            if (snap.containsKey("pbnFontSize")) pbnFontSizeSlider.setValue((Integer) snap.get("pbnFontSize"));
            if (snap.containsKey("pbnShowNumbers")) pbnShowNumbers.setSelected((Boolean) snap.get("pbnShowNumbers"));
            if (snap.containsKey("pbnShowLegend")) pbnShowLegend.setSelected((Boolean) snap.get("pbnShowLegend"));
            updateControls();
        } finally {
            suppressChanges = false;
        }
        fireChange();
    }

    public void pushUndoState() {
        snapshotManager.pushUndoState();
    }

    public boolean undo() {
        return snapshotManager.undo();
    }

    public boolean redo() {
        return snapshotManager.redo();
    }

    public boolean canUndo() {
        return snapshotManager.canUndo();
    }

    public boolean canRedo() {
        return snapshotManager.canRedo();
    }
}
