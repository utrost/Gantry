package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Modal dialog collecting raster-vectorization options for "Import Image...": the GUI
 * front end of the {@code vectorize} module. It does not run anything itself — it returns
 * the vectorizer's CLI-style argument list (strategy plus the core parameters), which the
 * caller combines with {@code -i <image>}/{@code -o <svg>} and hands to
 * {@link org.trostheide.gantry.vectorize.Main#runSingleFile(String[])}. The resulting SVG
 * then flows through the existing "Import SVG" path unchanged.
 *
 * <p>Deliberately a focused subset of the standalone Vectorize GUI — strategy choice and
 * the parameters that matter most — with controls enabled per the selected strategy.
 */
public final class VectorizeDialog extends JDialog {

    /** A selectable strategy: a human label, the CLI code, and which control groups apply. */
    private enum Strategy {
        DP("Line art / contours (Douglas–Peucker)", "dp", Group.CANNY),
        LINE("Straight-line fit", "line", Group.CANNY),
        RAW("Raw contours", "raw", Group.CANNY),
        CONVEXHULL("Convex hull", "convexhull", Group.CANNY),
        CENTERLINE("Centerline / single-stroke (skeleton)", "centerline", Group.CENTERLINE),
        BEZIER("Bézier outlines (DrPTrace)", "bezier", Group.BEZIER),
        BEZIER2("Colour fills (ImageTracer)", "bezier2", Group.BEZIER2),
        PBN("Paint by Numbers", "pbn", Group.PBN);

        final String label;
        final String code;
        final Group group;

        Strategy(String label, String code, Group group) {
            this.label = label;
            this.code = code;
            this.group = group;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Which parameter family a strategy uses, so the dialog can enable the right controls. */
    private enum Group {
        CANNY, CENTERLINE, BEZIER, BEZIER2, PBN
    }

    /** The dialog's result: the vectorizer argument list (no {@code -i}/{@code -o}). */
    public record Result(List<String> vectorizeArgs, String strategyLabel) {
    }

    private final JComboBox<Strategy> strategyCombo = new JComboBox<>(Strategy.values());

    // Canny family (dp/line/raw/convexhull) + shared output style
    private final JSpinner toleranceSpinner = new JSpinner(new SpinnerNumberModel(2.0, 0.0, 50.0, 0.5));
    private final JSpinner detailSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 1.0, 0.1));
    private final JCheckBox cannyAutoCheck = new JCheckBox("Auto Canny thresholds", true);
    private final JSpinner cannyLowSpinner = new JSpinner(new SpinnerNumberModel(0.05, 0.0, 1.0, 0.01));
    private final JSpinner cannyHighSpinner = new JSpinner(new SpinnerNumberModel(0.15, 0.0, 1.0, 0.01));
    private final JCheckBox colorEdgesCheck = new JCheckBox("Colour-aware edges");
    private final JTextField strokeColorField = new JTextField("black", 10);
    private final JSpinner strokeWidthSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 5.0, 0.1));
    private final JCheckBox smoothCurvesCheck = new JCheckBox("Smooth curves");

    // Centerline
    private final JSpinner clThresholdSpinner = new JSpinner(new SpinnerNumberModel(128, 0, 255, 1));

    // Bézier (DrPTrace)
    private final JSpinner bezierColorsSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 32, 1));
    private final JSpinner bezierDetailSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 20, 1));

    // Bézier2 (ImageTracer)
    private final JSpinner b2ColorsSpinner = new JSpinner(new SpinnerNumberModel(16, 2, 64, 1));
    private final JCheckBox b2OutlineCheck = new JCheckBox("Outline mode (fills → strokes)");

    // Paint by Numbers
    private final JSpinner pbnNumColorsSpinner = new JSpinner(new SpinnerNumberModel(6, 2, 32, 1));

    private final JButton okBtn = new JButton("Vectorize");
    private Result result;

    public VectorizeDialog(Window owner) {
        super(owner, "Import Image — vectorize", ModalityType.APPLICATION_MODAL);

        JButton cancelBtn = new JButton("Cancel");
        okBtn.addActionListener(e -> onOk());
        cancelBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(okBtn);
        buttons.add(cancelBtn);

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(okBtn);

        strategyCombo.addActionListener(e -> updateEnabledState());
        cannyAutoCheck.addActionListener(e -> updateEnabledState());
        updateEnabledState();

        pack();
        setLocationRelativeTo(owner);
    }

    private JComponent buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;

        addRow(form, gbc, "Strategy", strategyCombo);
        addRow(form, gbc, "Tolerance (px)", toleranceSpinner);
        addRow(form, gbc, "Detail (0–1)", detailSpinner);
        addSpan(form, gbc, cannyAutoCheck);
        addRow(form, gbc, "Canny low", cannyLowSpinner);
        addRow(form, gbc, "Canny high", cannyHighSpinner);
        addSpan(form, gbc, colorEdgesCheck);
        addRow(form, gbc, "Centerline threshold", clThresholdSpinner);
        addRow(form, gbc, "Bézier colours", bezierColorsSpinner);
        addRow(form, gbc, "Bézier detail (px)", bezierDetailSpinner);
        addRow(form, gbc, "ImageTracer colours", b2ColorsSpinner);
        addSpan(form, gbc, b2OutlineCheck);
        addRow(form, gbc, "Paint-by-Numbers colours", pbnNumColorsSpinner);
        addRow(form, gbc, "Stroke colour", strokeColorField);
        addRow(form, gbc, "Stroke width", strokeWidthSpinner);
        addSpan(form, gbc, smoothCurvesCheck);

        return new JScrollPane(form);
    }

    /** Enables exactly the controls the selected strategy uses. */
    private void updateEnabledState() {
        Group g = ((Strategy) strategyCombo.getSelectedItem()).group;

        boolean canny = g == Group.CANNY;
        boolean centerline = g == Group.CENTERLINE;
        boolean bezier = g == Group.BEZIER;
        boolean bezier2 = g == Group.BEZIER2;
        boolean pbn = g == Group.PBN;

        // Tolerance is used by the canny family, centerline and PBN.
        toleranceSpinner.setEnabled(canny || centerline || pbn);
        detailSpinner.setEnabled(canny);
        cannyAutoCheck.setEnabled(canny);
        boolean manualCanny = canny && !cannyAutoCheck.isSelected();
        cannyLowSpinner.setEnabled(manualCanny);
        cannyHighSpinner.setEnabled(manualCanny);
        colorEdgesCheck.setEnabled(canny);

        clThresholdSpinner.setEnabled(centerline);
        bezierColorsSpinner.setEnabled(bezier);
        bezierDetailSpinner.setEnabled(bezier);
        b2ColorsSpinner.setEnabled(bezier2);
        b2OutlineCheck.setEnabled(bezier2);
        pbnNumColorsSpinner.setEnabled(pbn);

        // Stroke style applies to the polyline/centerline/bézier outputs, not the
        // colour-fill tracers (bezier2/pbn carry their own colours).
        boolean stroked = canny || centerline || bezier;
        strokeColorField.setEnabled(stroked);
        strokeWidthSpinner.setEnabled(stroked);
        smoothCurvesCheck.setEnabled(canny);
    }

    private void onOk() {
        Strategy strategy = (Strategy) strategyCombo.getSelectedItem();
        List<String> a = new ArrayList<>();
        a.add("-s");
        a.add(strategy.code);

        switch (strategy.group) {
            case CANNY -> {
                addNum(a, "-t", toleranceSpinner);
                addNum(a, "--detail", detailSpinner);
                if (cannyAutoCheck.isSelected()) {
                    a.add("--canny-auto");
                } else {
                    addNum(a, "--canny-low", cannyLowSpinner);
                    addNum(a, "--canny-high", cannyHighSpinner);
                }
                if (colorEdgesCheck.isSelected()) {
                    a.add("--color-edges");
                }
                addStrokeStyle(a, true);
            }
            case CENTERLINE -> {
                addNum(a, "--cl-threshold", clThresholdSpinner);
                addNum(a, "-t", toleranceSpinner);
                addStrokeStyle(a, false);
            }
            case BEZIER -> {
                addNum(a, "--bezier-colors", bezierColorsSpinner);
                addNum(a, "--bezier-detail", bezierDetailSpinner);
                addStrokeStyle(a, false);
            }
            case BEZIER2 -> {
                addNum(a, "--b2-colors", b2ColorsSpinner);
                if (b2OutlineCheck.isSelected()) {
                    a.add("--b2-outline");
                }
            }
            case PBN -> {
                addNum(a, "--pbn-num-colors", pbnNumColorsSpinner);
                addNum(a, "-t", toleranceSpinner);
            }
        }

        result = new Result(a, strategy.label);
        dispose();
    }

    /** Adds stroke colour/width and, for the canny family, the smooth-curves flag. */
    private void addStrokeStyle(List<String> a, boolean withSmooth) {
        String color = strokeColorField.getText().trim();
        if (!color.isEmpty()) {
            a.add("--stroke-color");
            a.add(color);
        }
        addNum(a, "--stroke-width", strokeWidthSpinner);
        if (withSmooth && smoothCurvesCheck.isSelected()) {
            a.add("--smooth-curves");
        }
    }

    private static void addNum(List<String> a, String flag, JSpinner spinner) {
        a.add(flag);
        Object v = spinner.getValue();
        if (v instanceof Integer i) {
            a.add(Integer.toString(i));
        } else {
            a.add(Double.toString(((Number) v).doubleValue()));
        }
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

    private void addSpan(JPanel form, GridBagConstraints gbc, JComponent field) {
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        form.add(field, gbc);
        gbc.gridwidth = 1;
        gbc.gridy++;
    }

    /** Shows the dialog and returns the chosen options, or {@code null} if cancelled. */
    public Result showDialog() {
        setVisible(true);
        return result;
    }
}
