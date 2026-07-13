package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.svgtoolbox.Config;
import org.trostheide.gantry.svgtoolbox.HatchStyle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared editor for the full SVGToolBox pre-processing option set, embedded by both
 * {@link SvgImportDialog} (the "Process SVG" import tab) and {@link EditProcessDialog}
 * ("Edit &gt; Process SVG"). Having one component is the single source of truth for these
 * controls so the two dialogs can't drift apart — they expose exactly the same options and
 * build the same {@link Config}.
 *
 * <p>Field values persist statically for the lifetime of the app, so tweaking one value and
 * reopening either dialog doesn't reset the rest (and settings carry across the two dialogs).
 */
final class ToolboxOptionsPanel extends JPanel {

    // --- Style ---
    private final JTextField strokeWidthField = new JTextField("0", 8);
    private final JTextField paletteField = new JTextField(20);
    private final JTextField hiddenLayersField = new JTextField(20);

    // --- Hatch ---
    private final JCheckBox hatchCheck = new JCheckBox("Enable hatching");
    private final JComboBox<String> hatchPatternCombo =
            new JComboBox<>(new String[] {"linear", "cross", "zigzag", "wave", "dot", "none", "empty"});
    private final JSpinner hatchAngleSpinner = new JSpinner(new SpinnerNumberModel(45.0, -360.0, 360.0, 5.0));
    private final JSpinner hatchGapSpinner = new JSpinner(new SpinnerNumberModel(5.0, 0.1, 1000.0, 0.5));
    private final JSpinner hatchAmplitudeSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1000.0, 0.5));
    private final JSpinner hatchWavelengthSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1000.0, 0.5));
    private final JSpinner dotRadiusSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1000.0, 0.1));
    private final HatchOverridesPanel hatchOverridesPanel;

    // --- Geometry ---
    private final JSpinner simplifyToleranceSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 100.0, 0.1));
    private final JSpinner rotateSpinner = new JSpinner(new SpinnerNumberModel(0.0, -360.0, 360.0, 90.0));
    private final JComboBox<String> cropCombo = new JComboBox<>(new String[] {"None", "A4", "Letter", "Custom"});
    private final JTextField cropCustomField = new JTextField("793.7x1122.5", 12);

    // --- Optimize ---
    private final JCheckBox optimizeCheck = new JCheckBox("Optimize path order");
    private final JCheckBox linesimplifyCheck = new JCheckBox("Linesimplify (RDP)");
    private final JSpinner linesimplifyToleranceSpinner = new JSpinner(new SpinnerNumberModel(0.378, 0.0, 100.0, 0.01));
    private final JCheckBox linemergeCheck = new JCheckBox("Linemerge");
    private final JSpinner linemergeToleranceSpinner = new JSpinner(new SpinnerNumberModel(1.89, 0.0, 100.0, 0.01));
    private final JCheckBox linesortCheck = new JCheckBox("Linesort");
    private final JCheckBox linesortTwoOptCheck = new JCheckBox("Linesort 2-opt");
    private final JCheckBox reloopCheck = new JCheckBox("Reloop closed paths");
    private final JCheckBox printStatsCheck = new JCheckBox("Print statistics");

    /** Remembered values, shared across both dialogs and across reopens. */
    private static State savedState;

    ToolboxOptionsPanel() {
        this(List.of());
    }

    ToolboxOptionsPanel(Collection<String> fillColors) {
        hatchOverridesPanel = new HatchOverridesPanel(fillColors);
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;

        addSection(gbc, "Style");
        addRow(gbc, "Stroke width override (px, 0 = none)", strokeWidthField);
        addRow(gbc, "Palette (hex colors, comma-separated)", paletteField);
        addRow(gbc, "Hidden layers (hex colors, comma-separated)", hiddenLayersField);

        addSection(gbc, "Hatch");
        addWide(gbc, hatchCheck);
        addRow(gbc, "Hatch pattern", hatchPatternCombo);
        addRow(gbc, "Hatch angle (deg)", hatchAngleSpinner);
        addRow(gbc, "Hatch gap", hatchGapSpinner);
        addRow(gbc, "Amplitude — wave/zigzag (0 = auto)", hatchAmplitudeSpinner);
        addRow(gbc, "Wavelength — wave/zigzag (0 = auto)", hatchWavelengthSpinner);
        addRow(gbc, "Dot radius — dot (0 = auto)", dotRadiusSpinner);
        addWide(gbc, hatchOverridesPanel);

        addSection(gbc, "Geometry");
        addRow(gbc, "Simplify tolerance (0 = off)", simplifyToleranceSpinner);
        addRow(gbc, "Rotate (deg)", rotateSpinner);
        addRow(gbc, "Crop", cropCombo);
        addRow(gbc, "Custom crop (WxH px)", cropCustomField);

        addSection(gbc, "Optimize");
        addWide(gbc, optimizeCheck);
        addWide(gbc, linesimplifyCheck);
        addRow(gbc, "Linesimplify tolerance", linesimplifyToleranceSpinner);
        addWide(gbc, linemergeCheck);
        addRow(gbc, "Linemerge tolerance", linemergeToleranceSpinner);
        addWide(gbc, linesortCheck);
        addWide(gbc, linesortTwoOptCheck);
        addWide(gbc, reloopCheck);
        addWide(gbc, printStatsCheck);

        cropCustomField.setEnabled(false);
        cropCombo.addActionListener(e -> cropCustomField.setEnabled("Custom".equals(cropCombo.getSelectedItem())));

        if (savedState != null) {
            savedState.applyTo(this);
            fillColors.forEach(hatchOverridesPanel::addIfAbsent);
        }
    }

    /** True when hatching is enabled (the import dialog uses this to imply "run toolbox"). */
    boolean isHatchEnabled() {
        return hatchCheck.isSelected();
    }

    /** Registers a listener on the hatch toggle (the import dialog ticks its master toolbox flag). */
    void addHatchActionListener(ActionListener l) {
        hatchCheck.addActionListener(l);
    }

    /** Sets or adds a colour-specific style; also provides a small testable GUI-to-Config seam. */
    void setHatchOverride(String color, String pattern, double angle, double gap) {
        hatchOverridesPanel.setOverride(color, pattern, angle, gap);
    }

    /**
     * Builds an SVGToolBox {@link Config} from the current field values, remembering them for the
     * next time either dialog is opened. Throws {@link IllegalArgumentException} on invalid input
     * (stroke width, palette colours, custom crop) — callers surface the message to the user.
     */
    Config buildConfig() {
        float strokeWidth;
        try {
            strokeWidth = Float.parseFloat(strokeWidthField.getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Stroke width must be a number.");
        }

        List<Color> palette = parseColors(paletteField.getText());
        List<String> hiddenLayers = Arrays.stream(hiddenLayersField.getText().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        double hatchAngle = ((Number) hatchAngleSpinner.getValue()).doubleValue();
        double hatchGap = ((Number) hatchGapSpinner.getValue()).doubleValue();
        double hatchAmplitude = ((Number) hatchAmplitudeSpinner.getValue()).doubleValue();
        double hatchWavelength = ((Number) hatchWavelengthSpinner.getValue()).doubleValue();
        double dotRadius = ((Number) dotRadiusSpinner.getValue()).doubleValue();
        String hatchPattern = (String) hatchPatternCombo.getSelectedItem();
        HatchStyle globalStyle = new HatchStyle(hatchAngle, hatchGap, hatchPattern,
                hatchAmplitude, hatchWavelength, dotRadius);
        Map<String, HatchStyle> overrides = hatchOverridesPanel.buildOverrides();

        Rectangle2D cropBounds = PaperSizes.resolve((String) cropCombo.getSelectedItem(), cropCustomField.getText());

        Config config = new Config.Builder()
                .inputPath("")
                .outputPath("")
                .strokeWidth(strokeWidth)
                .palette(palette)
                .enableHatching(hatchCheck.isSelected())
                .globalStyle(globalStyle)
                .overrides(overrides)
                .strokeWidthOverrides(Collections.emptyMap())
                .hiddenLayers(hiddenLayers)
                .noHatchColors(Collections.emptyList())
                .simplifyTolerance(((Number) simplifyToleranceSpinner.getValue()).doubleValue())
                .hatchPattern(hatchPattern)
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

        savedState = State.capture(this);
        return config;
    }

    private static List<Color> parseColors(String text) {
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

    private static JLabel sectionLabel(String title) {
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private void addSection(GridBagConstraints gbc, String title) {
        if (gbc.gridy > 0) {
            gbc.insets = new Insets(14, 3, 3, 3);
        }
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        add(sectionLabel(title), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(3, 3, 3, 3);
    }

    private void addWide(GridBagConstraints gbc, JComponent comp) {
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        add(comp, gbc);
        gbc.gridy++;
    }

    private void addRow(GridBagConstraints gbc, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(field, gbc);
        gbc.gridy++;
    }

    /** Immutable snapshot of every control, used to persist values across dialog reopens. */
    private static final class State {
        final String strokeWidth;
        final String palette;
        final String hiddenLayers;
        final boolean hatch;
        final String hatchPattern;
        final double hatchAngle;
        final double hatchGap;
        final double hatchAmplitude;
        final double hatchWavelength;
        final double dotRadius;
        final List<HatchOverridesPanel.OverrideRow> hatchOverrides;
        final double simplifyTolerance;
        final double rotate;
        final String crop;
        final String cropCustom;
        final boolean optimize;
        final boolean linesimplify;
        final double linesimplifyTolerance;
        final boolean linemerge;
        final double linemergeTolerance;
        final boolean linesort;
        final boolean linesortTwoOpt;
        final boolean reloop;
        final boolean printStats;

        private State(ToolboxOptionsPanel p) {
            strokeWidth = p.strokeWidthField.getText();
            palette = p.paletteField.getText();
            hiddenLayers = p.hiddenLayersField.getText();
            hatch = p.hatchCheck.isSelected();
            hatchPattern = (String) p.hatchPatternCombo.getSelectedItem();
            hatchAngle = ((Number) p.hatchAngleSpinner.getValue()).doubleValue();
            hatchGap = ((Number) p.hatchGapSpinner.getValue()).doubleValue();
            hatchAmplitude = ((Number) p.hatchAmplitudeSpinner.getValue()).doubleValue();
            hatchWavelength = ((Number) p.hatchWavelengthSpinner.getValue()).doubleValue();
            dotRadius = ((Number) p.dotRadiusSpinner.getValue()).doubleValue();
            hatchOverrides = p.hatchOverridesPanel.rows();
            simplifyTolerance = ((Number) p.simplifyToleranceSpinner.getValue()).doubleValue();
            rotate = ((Number) p.rotateSpinner.getValue()).doubleValue();
            crop = (String) p.cropCombo.getSelectedItem();
            cropCustom = p.cropCustomField.getText();
            optimize = p.optimizeCheck.isSelected();
            linesimplify = p.linesimplifyCheck.isSelected();
            linesimplifyTolerance = ((Number) p.linesimplifyToleranceSpinner.getValue()).doubleValue();
            linemerge = p.linemergeCheck.isSelected();
            linemergeTolerance = ((Number) p.linemergeToleranceSpinner.getValue()).doubleValue();
            linesort = p.linesortCheck.isSelected();
            linesortTwoOpt = p.linesortTwoOptCheck.isSelected();
            reloop = p.reloopCheck.isSelected();
            printStats = p.printStatsCheck.isSelected();
        }

        static State capture(ToolboxOptionsPanel p) {
            return new State(p);
        }

        void applyTo(ToolboxOptionsPanel p) {
            p.strokeWidthField.setText(strokeWidth);
            p.paletteField.setText(palette);
            p.hiddenLayersField.setText(hiddenLayers);
            p.hatchCheck.setSelected(hatch);
            p.hatchPatternCombo.setSelectedItem(hatchPattern);
            p.hatchAngleSpinner.setValue(hatchAngle);
            p.hatchGapSpinner.setValue(hatchGap);
            p.hatchAmplitudeSpinner.setValue(hatchAmplitude);
            p.hatchWavelengthSpinner.setValue(hatchWavelength);
            p.dotRadiusSpinner.setValue(dotRadius);
            p.hatchOverridesPanel.restoreRows(hatchOverrides);
            p.simplifyToleranceSpinner.setValue(simplifyTolerance);
            p.rotateSpinner.setValue(rotate);
            p.cropCombo.setSelectedItem(crop);
            p.cropCustomField.setText(cropCustom);
            p.cropCustomField.setEnabled("Custom".equals(crop));
            p.optimizeCheck.setSelected(optimize);
            p.linesimplifyCheck.setSelected(linesimplify);
            p.linesimplifyToleranceSpinner.setValue(linesimplifyTolerance);
            p.linemergeCheck.setSelected(linemerge);
            p.linemergeToleranceSpinner.setValue(linemergeTolerance);
            p.linesortCheck.setSelected(linesort);
            p.linesortTwoOptCheck.setSelected(linesortTwoOpt);
            p.reloopCheck.setSelected(reloop);
            p.printStatsCheck.setSelected(printStats);
        }
    }
}
