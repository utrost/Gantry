package org.trostheide.gantry.app.gui;

import org.apache.batik.swing.JSVGCanvas;
import org.trostheide.gantry.vectorize.gui.ImagePanel;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The vectorize live-preview studio (ROADMAP Phase 19, Tier 1). A single surface where you
 * tune a raster trace against a live preview, then hand off to the existing SVG import.
 *
 * <p>Layout: source image (left) and the traced SVG preview (right) side by side, with
 * preset + strategy + parameter controls on the right. Any control change schedules a
 * debounced re-trace on a cancellable background worker (the engine entry
 * {@link org.trostheide.gantry.vectorize.Main#runSingleFile(String[])}); the result is loaded
 * into a Batik {@link JSVGCanvas} and a path-count readout is updated — so the user sees the
 * trace update as they tune instead of committing blind.
 *
 * <p>On <b>Vectorize</b> it returns the chosen options as the same {@link Result} the old
 * blind dialog produced, so the caller's import flow is unchanged.
 */
public final class VectorizeStudioDialog extends JDialog {

    /** The dialog's result: the vectorizer argument list (no {@code -i}/{@code -o}). */
    public record Result(List<String> vectorizeArgs, String strategyLabel) {
    }

    /** A selectable strategy: a human label, the CLI code, and which controls apply. */
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

    private enum Group { CANNY, CENTERLINE, BEZIER, BEZIER2, PBN }

    /** A quick-start preset: a label and the controls it applies. */
    private record Preset(String label, Runnable apply) {
        @Override
        public String toString() {
            return label;
        }
    }

    // --- controls (mirror VectorizeDialog, but drive a live preview) ---
    private final JComboBox<Preset> presetCombo = new JComboBox<>();
    private final JComboBox<Strategy> strategyCombo = new JComboBox<>(Strategy.values());
    private final JSpinner toleranceSpinner = new JSpinner(new SpinnerNumberModel(2.0, 0.0, 50.0, 0.5));
    private final JSpinner detailSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 1.0, 0.1));
    private final JCheckBox cannyAutoCheck = new JCheckBox("Auto Canny thresholds", true);
    private final JSpinner cannyLowSpinner = new JSpinner(new SpinnerNumberModel(0.05, 0.0, 1.0, 0.01));
    private final JSpinner cannyHighSpinner = new JSpinner(new SpinnerNumberModel(0.15, 0.0, 1.0, 0.01));
    private final JCheckBox colorEdgesCheck = new JCheckBox("Colour-aware edges");
    private final JTextField strokeColorField = new JTextField("black", 10);
    private final JSpinner strokeWidthSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 5.0, 0.1));
    private final JCheckBox smoothCurvesCheck = new JCheckBox("Smooth curves");
    private final JSpinner clThresholdSpinner = new JSpinner(new SpinnerNumberModel(128, 0, 255, 1));
    private final JSpinner bezierColorsSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 32, 1));
    private final JSpinner bezierDetailSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 20, 1));
    private final JSpinner b2ColorsSpinner = new JSpinner(new SpinnerNumberModel(16, 2, 64, 1));
    private final JCheckBox b2OutlineCheck = new JCheckBox("Outline mode (fills → strokes)");
    private final JSpinner pbnNumColorsSpinner = new JSpinner(new SpinnerNumberModel(6, 2, 32, 1));
    private final JToggleButton cropToggle = new JToggleButton("Crop");

    // --- preview machinery ---
    private final ImagePanel sourcePanel = new ImagePanel();
    private final JSVGCanvas previewCanvas = new JSVGCanvas();
    private final JLabel readout = new JLabel(" ");
    private final JButton vectorizeBtn = new JButton("Vectorize");
    private final Timer debounce;

    private final File imageFile;
    private final BufferedImage sourceImage;
    private SwingWorker<TraceResult, Void> worker;
    private boolean applyingPreset;
    private Result result;

    public VectorizeStudioDialog(Window owner, File imageFile) throws IOException {
        super(owner, "Vectorize — live preview", ModalityType.APPLICATION_MODAL);
        this.imageFile = imageFile;
        this.sourceImage = ImageIO.read(imageFile);
        if (sourceImage == null) {
            throw new IOException("Could not read image: " + imageFile.getName());
        }
        sourcePanel.setImage(sourceImage);

        previewCanvas.setDocumentState(JSVGCanvas.ALWAYS_STATIC);

        debounce = new Timer(400, e -> retrace());
        debounce.setRepeats(false);

        buildPresets();
        setLayout(new BorderLayout());
        add(buildPreviewSplit(), BorderLayout.CENTER);
        add(buildControls(), BorderLayout.EAST);
        add(buildSouth(), BorderLayout.SOUTH);
        getRootPane().setDefaultButton(vectorizeBtn);

        wireControls();
        updateEnabledState();

        setSize(1100, 720);
        setLocationRelativeTo(owner);

        // Fit the source once the panel is realised, then kick off the first trace.
        SwingUtilities.invokeLater(() -> {
            sourcePanel.fitToWindow();
            retrace();
        });
    }

    // ----- layout -----

    private JComponent buildPreviewSplit() {
        JPanel left = titled("Source image", sourcePanel);
        JPanel right = titled("Vector preview", previewCanvas);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.5);
        split.setDividerLocation(540);
        return split;
    }

    private static JPanel titled(String title, JComponent body) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private JComponent buildControls() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;

        addRow(form, gbc, "Preset", presetCombo);
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
        addSpan(form, gbc, cropToggle);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(form, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(wrap);
        scroll.setPreferredSize(new Dimension(280, 0));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JComponent buildSouth() {
        JButton cancelBtn = new JButton("Cancel");
        vectorizeBtn.addActionListener(e -> onVectorize());
        cancelBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(vectorizeBtn);
        buttons.add(cancelBtn);

        readout.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

        JPanel south = new JPanel(new BorderLayout());
        south.add(readout, BorderLayout.WEST);
        south.add(buttons, BorderLayout.EAST);
        return south;
    }

    private void buildPresets() {
        presetCombo.addItem(new Preset("Custom", () -> { }));
        presetCombo.addItem(new Preset("Line art", () -> {
            strategyCombo.setSelectedItem(Strategy.DP);
            toleranceSpinner.setValue(2.0);
            detailSpinner.setValue(1.0);
            cannyAutoCheck.setSelected(true);
        }));
        presetCombo.addItem(new Preset("Sketch", () -> {
            strategyCombo.setSelectedItem(Strategy.DP);
            toleranceSpinner.setValue(3.0);
            detailSpinner.setValue(0.7);
            cannyAutoCheck.setSelected(true);
        }));
        presetCombo.addItem(new Preset("Centerline (plotter)", () -> {
            strategyCombo.setSelectedItem(Strategy.CENTERLINE);
            clThresholdSpinner.setValue(128);
        }));
        presetCombo.addItem(new Preset("Logo (outlines)", () -> {
            strategyCombo.setSelectedItem(Strategy.BEZIER);
            bezierColorsSpinner.setValue(2);
            bezierDetailSpinner.setValue(5);
        }));
        presetCombo.addItem(new Preset("Photo — detailed", () -> {
            strategyCombo.setSelectedItem(Strategy.BEZIER2);
            b2ColorsSpinner.setValue(16);
        }));
        presetCombo.addItem(new Preset("Photo — simplified", () -> {
            strategyCombo.setSelectedItem(Strategy.BEZIER2);
            b2ColorsSpinner.setValue(8);
        }));
        presetCombo.addItem(new Preset("Paint by Numbers", () -> {
            strategyCombo.setSelectedItem(Strategy.PBN);
            pbnNumColorsSpinner.setValue(12);
        }));
    }

    // ----- control wiring -----

    private void wireControls() {
        presetCombo.addActionListener(e -> {
            Preset p = (Preset) presetCombo.getSelectedItem();
            if (p == null || "Custom".equals(p.label)) {
                return;
            }
            applyingPreset = true;
            try {
                p.apply.run();
            } finally {
                applyingPreset = false;
            }
            onControlChanged();
        });

        strategyCombo.addActionListener(e -> onControlChanged());
        cannyAutoCheck.addActionListener(e -> onControlChanged());
        for (JCheckBox c : new JCheckBox[] {colorEdgesCheck, smoothCurvesCheck, b2OutlineCheck}) {
            c.addActionListener(e -> onControlChanged());
        }
        cropToggle.addActionListener(e -> {
            sourcePanel.setRoiMode(cropToggle.isSelected());
            if (!cropToggle.isSelected()) {
                sourcePanel.clearRoi();
            }
        });
        sourcePanel.setRoiListener(roi -> onControlChanged());
        for (JSpinner s : new JSpinner[] {toleranceSpinner, detailSpinner, cannyLowSpinner,
                cannyHighSpinner, clThresholdSpinner, bezierColorsSpinner, bezierDetailSpinner,
                b2ColorsSpinner, pbnNumColorsSpinner, strokeWidthSpinner}) {
            s.addChangeListener(e -> onControlChanged());
        }
        strokeColorField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onControlChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { onControlChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onControlChanged(); }
        });
    }

    /** Any control change: refresh enablement, mark preset Custom, and schedule a re-trace. */
    private void onControlChanged() {
        if (applyingPreset) {
            return;
        }
        updateEnabledState();
        debounce.restart();
    }

    private Strategy currentStrategy() {
        return (Strategy) strategyCombo.getSelectedItem();
    }

    private void updateEnabledState() {
        Group g = currentStrategy().group;
        boolean canny = g == Group.CANNY;
        boolean centerline = g == Group.CENTERLINE;
        boolean bezier = g == Group.BEZIER;
        boolean bezier2 = g == Group.BEZIER2;
        boolean pbn = g == Group.PBN;

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

        boolean stroked = canny || centerline || bezier;
        strokeColorField.setEnabled(stroked);
        strokeWidthSpinner.setEnabled(stroked);
        smoothCurvesCheck.setEnabled(canny);
    }

    // ----- argument building (shared by preview traces and the committed result) -----

    private List<String> buildParams() {
        Strategy strategy = currentStrategy();
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

        Rectangle roi = sourcePanel.getRoi();
        if (roi != null && roi.width > 0 && roi.height > 0) {
            a.add("--crop");
            a.add(roi.x + "," + roi.y + "," + roi.width + "," + roi.height);
        }
        return a;
    }

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
        a.add(v instanceof Integer i ? Integer.toString(i) : Double.toString(((Number) v).doubleValue()));
    }

    // ----- the live trace -----

    private record TraceResult(File svg, int paths) {
    }

    private void retrace() {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        readout.setText("Tracing…");
        vectorizeBtn.setEnabled(false);
        List<String> params = buildParams();

        worker = new SwingWorker<>() {
            @Override
            protected TraceResult doInBackground() throws Exception {
                // A fresh temp file per trace, so a still-running cancelled trace can't clobber it.
                File svg = File.createTempFile("gantry-studio-", ".svg");
                svg.deleteOnExit();
                List<String> args = new ArrayList<>(List.of(
                        "-i", imageFile.getAbsolutePath(), "-o", svg.getAbsolutePath()));
                args.addAll(params);
                org.trostheide.gantry.vectorize.Main.runSingleFile(args.toArray(new String[0]));
                String content = Files.readString(svg.toPath());
                int paths = count(content, "<path") + count(content, "<polyline") + count(content, "<line");
                return new TraceResult(svg, paths);
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                vectorizeBtn.setEnabled(true);
                try {
                    TraceResult tr = get();
                    previewCanvas.setURI(tr.svg.toURI().toString());
                    readout.setText(String.format("%s · %d path(s)", currentStrategy().label, tr.paths));
                } catch (Exception ex) {
                    readout.setText("Trace failed: " + rootMessage(ex));
                }
            }
        };
        worker.execute();
    }

    private void onVectorize() {
        result = new Result(buildParams(), currentStrategy().label);
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        dispose();
    }

    /** Shows the studio and returns the chosen options, or {@code null} if cancelled. */
    public Result showDialog() {
        setVisible(true);
        return result;
    }

    // ----- helpers -----

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) {
            c = c.getCause();
        }
        return c.getMessage() != null ? c.getMessage() : c.getClass().getSimpleName();
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
}
