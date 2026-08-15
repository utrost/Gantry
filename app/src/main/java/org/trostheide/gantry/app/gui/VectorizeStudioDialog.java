package org.trostheide.gantry.app.gui;

import org.apache.batik.swing.JSVGCanvas;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;
import org.trostheide.gantry.pipeline.svgimport.SvgImportStage;
import org.trostheide.gantry.vectorize.gui.ImagePanel;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.TitledBorder;
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
        SQUIGGLE("Tonal squiggle / portrait", "squiggle", Group.SQUIGGLE),
        NEEDLES("Oriented needles / texture", "needles", Group.NEEDLES),
        ISOLINES("Tonal isolines / contours", "isolines", Group.ISOLINES),
        SKETCH("Sketch / blueprint trace", "sketch", Group.SKETCH),
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

    private enum Group { CANNY, CENTERLINE, BEZIER, BEZIER2, SQUIGGLE, NEEDLES, ISOLINES, SKETCH, PBN }

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
    private final JSpinner bezierColorsSpinner = new JSpinner(new SpinnerNumberModel(16, 1, 64, 1));
    private final JSpinner bezierDetailSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 20, 1));
    private final JSpinner b2ColorsSpinner = new JSpinner(new SpinnerNumberModel(16, 2, 64, 1));
    private final JCheckBox b2OutlineCheck = new JCheckBox("Outline mode (fills → strokes)");
    private final JSpinner pbnNumColorsSpinner = new JSpinner(new SpinnerNumberModel(6, 2, 32, 1));
    private final JSpinner squiggleDensitySpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.25, 3.0, 0.1));
    private final JSpinner squiggleAmplitudeSpinner = new JSpinner(new SpinnerNumberModel(3.5, 0.0, 20.0, 0.5));
    private final JSpinner squiggleToneSpinner = new JSpinner(new SpinnerNumberModel(0.72, 0.0, 1.0, 0.05));
    private final JSpinner squiggleBackgroundSpinner = new JSpinner(new SpinnerNumberModel(0.85, 0.0, 1.0, 0.05));
    private final JSpinner needleSpacingSpinner = new JSpinner(new SpinnerNumberModel(6.0, 2.0, 40.0, 0.5));
    private final JSpinner needleLengthSpinner = new JSpinner(new SpinnerNumberModel(10.0, 1.0, 80.0, 0.5));
    private final JSpinner needleThresholdSpinner = new JSpinner(new SpinnerNumberModel(0.12, 0.0, 1.0, 0.02));
    private final JSpinner needleGradientSpinner = new JSpinner(new SpinnerNumberModel(0.04, 0.0, 1.0, 0.01));
    private final JSpinner needleToneSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 4.0, 0.1));
    private final JSpinner isolineLevelsSpinner = new JSpinner(new SpinnerNumberModel(6, 1, 24, 1));
    private final JSpinner isolineSmoothingSpinner = new JSpinner(new SpinnerNumberModel(3.0, 0.0, 20.0, 0.5));
    private final JSpinner isolineMinLengthSpinner = new JSpinner(new SpinnerNumberModel(8, 2, 200, 1));
    private final JSpinner isolineThresholdSpinner = new JSpinner(new SpinnerNumberModel(0.06, 0.0, 1.0, 0.01));
    private final JSpinner sketchWindowSpinner = new JSpinner(new SpinnerNumberModel(21, 3, 99, 2));
    private final JSpinner sketchOffsetSpinner = new JSpinner(new SpinnerNumberModel(0.04, 0.0, 0.5, 0.01));
    private final JSpinner sketchMinLengthSpinner = new JSpinner(new SpinnerNumberModel(6, 2, 200, 1));
    private final JCheckBox sketchSkeletonCheck = new JCheckBox("Sketch skeleton", true);
    private final JToggleButton cropToggle = new JToggleButton("Crop");

    // --- preview machinery ---
    private final ImagePanel sourcePanel = new ImagePanel();
    private final JSVGCanvas previewCanvas = new JSVGCanvas();
    private final JLabel readout = new JLabel(" ");
    private final JLabel hint = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar();
    private final JButton vectorizeBtn = new JButton("Vectorize");
    private final Timer debounce;

    private TitledBorder previewBorder;
    private JComponent previewPanel;
    private Font readoutPlainFont;

    private final File imageFile;
    private final BufferedImage sourceImage;
    private SwingWorker<TraceResult, Void> worker;
    private boolean applyingPreset;
    private Result result;

    public VectorizeStudioDialog(Window owner, File imageFile) throws IOException {
        this(owner, imageFile, null);
    }

    /**
     * @param initialArgs vectorize CLI-style arguments to pre-populate the controls with (the
     *                    {@link Result#vectorizeArgs()} of a previous run), or {@code null} for
     *                    defaults. Used by "Re-vectorize Image…".
     */
    public VectorizeStudioDialog(Window owner, File imageFile, List<String> initialArgs) throws IOException {
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
        if (initialArgs != null) {
            applyArgs(initialArgs);
        }
        updateEnabledState();

        setSize(1240, 800);
        setMinimumSize(new Dimension(1000, 640));
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
        previewBorder = BorderFactory.createTitledBorder("Vector preview");
        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(previewBorder);
        right.add(previewCanvas, BorderLayout.CENTER);
        previewPanel = right;
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
        addRow(form, gbc, "Squiggle density", squiggleDensitySpinner);
        addRow(form, gbc, "Squiggle amplitude", squiggleAmplitudeSpinner);
        addRow(form, gbc, "Squiggle tone", squiggleToneSpinner);
        addRow(form, gbc, "Background suppression", squiggleBackgroundSpinner);
        addRow(form, gbc, "Needle spacing", needleSpacingSpinner);
        addRow(form, gbc, "Needle length", needleLengthSpinner);
        addRow(form, gbc, "Needle dark threshold", needleThresholdSpinner);
        addRow(form, gbc, "Needle gradient", needleGradientSpinner);
        addRow(form, gbc, "Needle tone", needleToneSpinner);
        addRow(form, gbc, "Isoline levels", isolineLevelsSpinner);
        addRow(form, gbc, "Isoline smoothing", isolineSmoothingSpinner);
        addRow(form, gbc, "Isoline min length", isolineMinLengthSpinner);
        addRow(form, gbc, "Isoline tone threshold", isolineThresholdSpinner);
        addRow(form, gbc, "Sketch window", sketchWindowSpinner);
        addRow(form, gbc, "Sketch offset", sketchOffsetSpinner);
        addRow(form, gbc, "Sketch min length", sketchMinLengthSpinner);
        addSpan(form, gbc, sketchSkeletonCheck);
        addRow(form, gbc, "Stroke colour", strokeColorField);
        addRow(form, gbc, "Stroke width", strokeWidthSpinner);
        addSpan(form, gbc, smoothCurvesCheck);
        addSpan(form, gbc, cropToggle);

        // Cap the value column so a long combo label (e.g. "Bézier outlines (DrPTrace)") or a wide
        // spinner can't stretch the form past the scroll viewport and push fields off the edge.
        for (JComponent c : new JComponent[] {presetCombo, strategyCombo, toleranceSpinner,
                detailSpinner, cannyLowSpinner, cannyHighSpinner, clThresholdSpinner,
                bezierColorsSpinner, bezierDetailSpinner, b2ColorsSpinner, pbnNumColorsSpinner,
                squiggleDensitySpinner, squiggleAmplitudeSpinner, squiggleToneSpinner,
                squiggleBackgroundSpinner, needleSpacingSpinner, needleLengthSpinner,
                needleThresholdSpinner, needleGradientSpinner, needleToneSpinner,
                isolineLevelsSpinner, isolineSmoothingSpinner, isolineMinLengthSpinner,
                isolineThresholdSpinner, sketchWindowSpinner, sketchOffsetSpinner,
                sketchMinLengthSpinner, strokeColorField, strokeWidthSpinner}) {
            Dimension pref = c.getPreferredSize();
            c.setPreferredSize(new Dimension(170, pref.height));
            c.setMinimumSize(new Dimension(80, pref.height));
        }

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(form, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(wrap);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(360, 0));
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

        hint.setForeground(new Color(120, 120, 120));
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, hint.getFont().getSize() - 1f));
        readoutPlainFont = readout.getFont();

        // Animated indeterminate bar shown only while a trace runs — motion is what catches the eye,
        // so the user can't miss that the preview is recomputing.
        progress.setIndeterminate(true);
        progress.setVisible(false);
        Dimension barSize = new Dimension(110, 14);
        progress.setPreferredSize(barSize);
        progress.setMaximumSize(barSize);

        JPanel readoutRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        readoutRow.add(progress);
        readoutRow.add(readout);
        readoutRow.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        hint.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JPanel status = new JPanel();
        status.setLayout(new BoxLayout(status, BoxLayout.Y_AXIS));
        status.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        status.add(readoutRow);
        status.add(hint);

        JPanel south = new JPanel(new BorderLayout());
        south.add(status, BorderLayout.WEST);
        south.add(buttons, BorderLayout.EAST);
        return south;
    }

    /** Toggles the visible "tracing in progress" state: the animated bar, an accented readout, and
     *  an "updating…" preview title — so a running trace is obvious, not a glance-and-miss label. */
    private void setTracing(boolean tracing) {
        progress.setVisible(tracing);
        if (tracing) {
            readout.setText("Tracing…");
            readout.setForeground(new Color(0, 90, 180));
            readout.setFont(readoutPlainFont.deriveFont(Font.BOLD));
            if (previewBorder != null) {
                previewBorder.setTitle("Vector preview — updating…");
            }
        } else {
            readout.setForeground(UIManager.getColor("Label.foreground"));
            readout.setFont(readoutPlainFont);
            if (previewBorder != null) {
                previewBorder.setTitle("Vector preview");
            }
        }
        if (previewPanel != null) {
            previewPanel.repaint();
        }
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
        presetCombo.addItem(new Preset("Tonal squiggle portrait", () -> {
            strategyCombo.setSelectedItem(Strategy.SQUIGGLE);
            toleranceSpinner.setValue(2.0);
            detailSpinner.setValue(0.7);
            squiggleDensitySpinner.setValue(1.2);
            squiggleAmplitudeSpinner.setValue(3.5);
            squiggleToneSpinner.setValue(0.72);
            squiggleBackgroundSpinner.setValue(0.85);
            strokeWidthSpinner.setValue(1.0);
        }));
        presetCombo.addItem(new Preset("Oriented needles texture", () -> {
            strategyCombo.setSelectedItem(Strategy.NEEDLES);
            needleSpacingSpinner.setValue(6.0);
            needleLengthSpinner.setValue(10.0);
            needleThresholdSpinner.setValue(0.12);
            needleGradientSpinner.setValue(0.04);
            needleToneSpinner.setValue(1.0);
            strokeWidthSpinner.setValue(1.0);
        }));
        presetCombo.addItem(new Preset("Tonal isolines", () -> {
            strategyCombo.setSelectedItem(Strategy.ISOLINES);
            isolineLevelsSpinner.setValue(7);
            isolineSmoothingSpinner.setValue(3.0);
            isolineMinLengthSpinner.setValue(8);
            isolineThresholdSpinner.setValue(0.06);
            strokeWidthSpinner.setValue(0.8);
        }));
        presetCombo.addItem(new Preset("Sketch / blueprint trace", () -> {
            strategyCombo.setSelectedItem(Strategy.SKETCH);
            sketchWindowSpinner.setValue(21);
            sketchOffsetSpinner.setValue(0.04);
            sketchMinLengthSpinner.setValue(6);
            sketchSkeletonCheck.setSelected(true);
            strokeWidthSpinner.setValue(0.8);
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
        for (JCheckBox c : new JCheckBox[] {colorEdgesCheck, smoothCurvesCheck, b2OutlineCheck, sketchSkeletonCheck}) {
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
                b2ColorsSpinner, pbnNumColorsSpinner, squiggleDensitySpinner,
                squiggleAmplitudeSpinner, squiggleToneSpinner, squiggleBackgroundSpinner,
                needleSpacingSpinner, needleLengthSpinner, needleThresholdSpinner,
                needleGradientSpinner, needleToneSpinner, isolineLevelsSpinner,
                isolineSmoothingSpinner, isolineMinLengthSpinner, isolineThresholdSpinner,
                sketchWindowSpinner, sketchOffsetSpinner, sketchMinLengthSpinner,
                strokeWidthSpinner}) {
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
        boolean squiggle = g == Group.SQUIGGLE;
        boolean needles = g == Group.NEEDLES;
        boolean isolines = g == Group.ISOLINES;
        boolean sketch = g == Group.SKETCH;
        boolean pbn = g == Group.PBN;

        toleranceSpinner.setEnabled(canny || centerline || pbn || squiggle);
        detailSpinner.setEnabled(canny || squiggle);
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
        squiggleDensitySpinner.setEnabled(squiggle);
        squiggleAmplitudeSpinner.setEnabled(squiggle);
        squiggleToneSpinner.setEnabled(squiggle);
        squiggleBackgroundSpinner.setEnabled(squiggle);
        needleSpacingSpinner.setEnabled(needles);
        needleLengthSpinner.setEnabled(needles);
        needleThresholdSpinner.setEnabled(needles);
        needleGradientSpinner.setEnabled(needles);
        needleToneSpinner.setEnabled(needles);
        isolineLevelsSpinner.setEnabled(isolines);
        isolineSmoothingSpinner.setEnabled(isolines);
        isolineMinLengthSpinner.setEnabled(isolines);
        isolineThresholdSpinner.setEnabled(isolines);
        sketchWindowSpinner.setEnabled(sketch);
        sketchOffsetSpinner.setEnabled(sketch);
        sketchMinLengthSpinner.setEnabled(sketch);
        sketchSkeletonCheck.setEnabled(sketch);

        boolean stroked = canny || centerline || bezier || squiggle || needles || isolines || sketch;
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
            case SQUIGGLE -> {
                addNum(a, "-t", toleranceSpinner);
                addNum(a, "--detail", detailSpinner);
                addNum(a, "--squiggle-density", squiggleDensitySpinner);
                addNum(a, "--squiggle-amplitude", squiggleAmplitudeSpinner);
                addNum(a, "--squiggle-tone", squiggleToneSpinner);
                addNum(a, "--squiggle-background", squiggleBackgroundSpinner);
                addStrokeStyle(a, false);
            }
            case NEEDLES -> {
                addNum(a, "--needle-spacing", needleSpacingSpinner);
                addNum(a, "--needle-length", needleLengthSpinner);
                addNum(a, "--needle-threshold", needleThresholdSpinner);
                addNum(a, "--needle-gradient", needleGradientSpinner);
                addNum(a, "--needle-tone", needleToneSpinner);
                addStrokeStyle(a, false);
            }
            case ISOLINES -> {
                addNum(a, "--isoline-levels", isolineLevelsSpinner);
                addNum(a, "--isoline-smoothing", isolineSmoothingSpinner);
                addNum(a, "--isoline-min-length", isolineMinLengthSpinner);
                addNum(a, "--isoline-threshold", isolineThresholdSpinner);
                addStrokeStyle(a, false);
            }
            case SKETCH -> {
                addNum(a, "--sketch-window", sketchWindowSpinner);
                addNum(a, "--sketch-offset", sketchOffsetSpinner);
                addNum(a, "--sketch-min-length", sketchMinLengthSpinner);
                a.add("--sketch-skeleton");
                a.add(Boolean.toString(sketchSkeletonCheck.isSelected()));
                addStrokeStyle(a, false);
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

    // ----- restoring controls from a previous run (re-vectorize) -----

    /** Inverse of {@link #buildParams()}: set the controls from a prior run's argument list. */
    private void applyArgs(List<String> args) {
        applyingPreset = true; // suppress per-control re-trace churn while we set many at once
        try {
            int i = 0;
            while (i < args.size()) {
                String a = args.get(i);
                String v = (i + 1 < args.size()) ? args.get(i + 1) : null;
                switch (a) {
                    case "-s" -> { selectStrategy(v); i += 2; }
                    case "-t" -> { setDouble(toleranceSpinner, v); i += 2; }
                    case "--detail" -> { setDouble(detailSpinner, v); i += 2; }
                    case "--canny-low" -> { setDouble(cannyLowSpinner, v); cannyAutoCheck.setSelected(false); i += 2; }
                    case "--canny-high" -> { setDouble(cannyHighSpinner, v); cannyAutoCheck.setSelected(false); i += 2; }
                    case "--stroke-color" -> { if (v != null) strokeColorField.setText(v); i += 2; }
                    case "--stroke-width" -> { setDouble(strokeWidthSpinner, v); i += 2; }
                    case "--cl-threshold" -> { setInt(clThresholdSpinner, v); i += 2; }
                    case "--bezier-colors" -> { setInt(bezierColorsSpinner, v); i += 2; }
                    case "--bezier-detail" -> { setInt(bezierDetailSpinner, v); i += 2; }
                    case "--b2-colors" -> { setInt(b2ColorsSpinner, v); i += 2; }
                    case "--pbn-num-colors" -> { setInt(pbnNumColorsSpinner, v); i += 2; }
                    case "--squiggle-density" -> { setDouble(squiggleDensitySpinner, v); i += 2; }
                    case "--squiggle-amplitude" -> { setDouble(squiggleAmplitudeSpinner, v); i += 2; }
                    case "--squiggle-tone" -> { setDouble(squiggleToneSpinner, v); i += 2; }
                    case "--squiggle-background" -> { setDouble(squiggleBackgroundSpinner, v); i += 2; }
                    case "--needle-spacing" -> { setDouble(needleSpacingSpinner, v); i += 2; }
                    case "--needle-length" -> { setDouble(needleLengthSpinner, v); i += 2; }
                    case "--needle-threshold" -> { setDouble(needleThresholdSpinner, v); i += 2; }
                    case "--needle-gradient" -> { setDouble(needleGradientSpinner, v); i += 2; }
                    case "--needle-tone" -> { setDouble(needleToneSpinner, v); i += 2; }
                    case "--isoline-levels" -> { setInt(isolineLevelsSpinner, v); i += 2; }
                    case "--isoline-smoothing" -> { setDouble(isolineSmoothingSpinner, v); i += 2; }
                    case "--isoline-min-length" -> { setInt(isolineMinLengthSpinner, v); i += 2; }
                    case "--isoline-threshold" -> { setDouble(isolineThresholdSpinner, v); i += 2; }
                    case "--sketch-window" -> { setInt(sketchWindowSpinner, v); i += 2; }
                    case "--sketch-offset" -> { setDouble(sketchOffsetSpinner, v); i += 2; }
                    case "--sketch-min-length" -> { setInt(sketchMinLengthSpinner, v); i += 2; }
                    case "--sketch-skeleton" -> { sketchSkeletonCheck.setSelected(Boolean.parseBoolean(v)); i += 2; }
                    case "--crop" -> {
                        if (v != null) {
                            String[] parts = v.split(",");
                            if (parts.length == 4) {
                                try {
                                    sourcePanel.setRoi(new Rectangle(Integer.parseInt(parts[0]),
                                            Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                                            Integer.parseInt(parts[3])));
                                    cropToggle.setSelected(true);
                                    sourcePanel.setRoiMode(true);
                                } catch (NumberFormatException ignored) {
                                    // Ignore a stale malformed crop argument and restore the rest.
                                }
                            }
                        }
                        i += 2;
                    }
                    case "--canny-auto" -> { cannyAutoCheck.setSelected(true); i += 1; }
                    case "--color-edges" -> { colorEdgesCheck.setSelected(true); i += 1; }
                    case "--smooth-curves" -> { smoothCurvesCheck.setSelected(true); i += 1; }
                    case "--b2-outline" -> { b2OutlineCheck.setSelected(true); i += 1; }
                    default -> i += 1;
                }
            }
            presetCombo.setSelectedIndex(0); // these are explicit values, not a named preset
        } finally {
            applyingPreset = false;
        }
    }

    private void selectStrategy(String code) {
        if (code == null) {
            return;
        }
        for (Strategy s : Strategy.values()) {
            if (s.code.equals(code)) {
                strategyCombo.setSelectedItem(s);
                return;
            }
        }
    }

    private static void setDouble(JSpinner spinner, String v) {
        if (v == null) {
            return;
        }
        try {
            spinner.setValue(Double.parseDouble(v));
        } catch (NumberFormatException ignored) {
            // leave the default
        }
    }

    private static void setInt(JSpinner spinner, String v) {
        if (v == null) {
            return;
        }
        try {
            spinner.setValue((int) Math.round(Double.parseDouble(v)));
        } catch (NumberFormatException ignored) {
            // leave the default
        }
    }

    // ----- the live trace -----

    private record TraceResult(File svg, int paths, StudioMetrics metrics) {
    }

    private void retrace() {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        setTracing(true);
        hint.setText(" ");
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
                // Import the traced SVG to the command model for plotter-aware metrics (Tier 2);
                // best-effort — fall back to the raw path count if import fails.
                StudioMetrics metrics = null;
                try {
                    ProcessorOutput out = SvgImportStage.importSvg(svg, SvgImportOptions.defaults());
                    metrics = StudioMetrics.of(out);
                } catch (Exception ignored) {
                    // keep metrics null
                }
                return new TraceResult(svg, paths, metrics);
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    // A newer trace is already running (it re-armed the tracing state); leave it.
                    return;
                }
                setTracing(false);
                vectorizeBtn.setEnabled(true);
                try {
                    TraceResult tr = get();
                    previewCanvas.setURI(tr.svg.toURI().toString());
                    if (tr.metrics != null) {
                        readout.setText(currentStrategy().label + " · " + tr.metrics.summary());
                        hint.setText(plottabilityHint(currentStrategy().group, tr.metrics));
                    } else {
                        readout.setText(String.format("%s · %d path(s)", currentStrategy().label, tr.paths));
                        hint.setText(" ");
                    }
                } catch (Exception ex) {
                    readout.setText("Trace failed: " + rootMessage(ex));
                    hint.setText(" ");
                }
            }
        };
        worker.execute();
    }

    /** A short, data-driven nudge about how efficiently this trace will pen-plot. */
    private static String plottabilityHint(Group group, StudioMetrics m) {
        if (group == Group.CENTERLINE) {
            return "Single-stroke paths — efficient for pen plotting.";
        }
        int travelPct = (int) Math.round(m.travelRatio() * 100);
        if (travelPct > 50) {
            return "High pen-up travel (" + travelPct + "%) — try Centerline or fewer colours.";
        }
        return " ";
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
