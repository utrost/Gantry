package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.pipeline.svgimport.PaperFormat;
import org.trostheide.gantry.pipeline.svgimport.SvgDocumentSize;
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
final class SvgImportOptionsPanel extends JPanel {

    private static final Dimension BASIC_DIALOG_SIZE = new Dimension(620, 260);
    private static final Dimension ADVANCED_IMPORT_DIALOG_SIZE = new Dimension(620, 560);
    private static final Dimension SVG_PROCESSING_DIALOG_SIZE = new Dimension(980, 720);

    /** The dialog's result: import options plus an optional SVGToolBox pre-processing config. */
    public record Result(SvgImportOptions importOptions, Config toolboxConfig) {
    }

    private final JSpinner maxDrawDistanceSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 100000.0, 10.0));
    private final JTextField stationField = new JTextField("default_station", 14);
    private final JSpinner curveStepSpinner = new JSpinner(new SpinnerNumberModel(0.1, 0.01, 10.0, 0.01));
    private static final String FIT_TO_MACHINE = "Machine bed";
    private static final String USE_SVG_SIZE = "SVG document size";
    private static final String[] PAPER_SIZE_OPTIONS = {
            "A6 — 105 × 148 mm",
            "A5 — 148 × 210 mm",
            "A4 — 210 × 297 mm",
            "A3 — 297 × 420 mm",
            "A2 — 420 × 594 mm",
            "A1 — 594 × 841 mm",
            "XL — 430 × 600 mm"
    };
    private final JComboBox<String> fitToCombo = new JComboBox<>(
            new String[] {FIT_TO_MACHINE, PAPER_SIZE_OPTIONS[0], PAPER_SIZE_OPTIONS[1],
                    PAPER_SIZE_OPTIONS[2], PAPER_SIZE_OPTIONS[3], PAPER_SIZE_OPTIONS[4],
                    PAPER_SIZE_OPTIONS[5], PAPER_SIZE_OPTIONS[6], "Custom"});
    private final JTextField customSizeField = new JTextField("210x297", 10);
    private final JSpinner paddingSpinner = new JSpinner(new SpinnerNumberModel(
            ArtworkImportPolicy.DEFAULT_PADDING_MM, 0.0, 500.0, 1.0));
    private final JCheckBox keepAspectRatioCheck = new JCheckBox("Keep aspect ratio", true);
    private final JCheckBox preservePageCheck = new JCheckBox("Preserve page margins");
    private final JCheckBox mirrorCheck = new JCheckBox("Mirror");
    private final PaperFormat machineFormat;
    private PaperFormat svgDocumentFormat;
    private ImportPreviewPanel preview;
    private final JTextArea safeFitSummary = WorkspaceStyle.note("");
    private boolean advancedImportVisible;

    // SVGToolBox pre-processing options
    /** The full toolbox option set, shared verbatim with {@link EditProcessDialog}. */
    private final ToolboxOptionsPanel optionsPanel;

    private Result result;

    /** Turns green once a valid size is chosen, signalling the import is ready to run. */
    private final JButton okBtn = new JButton("Import");

    public SvgImportOptionsPanel(Window owner) {
        this(owner, null, 300, 200);
    }

    public SvgImportOptionsPanel(Window owner, java.io.File sourceSvg) {
        this(owner, sourceSvg, 300, 200);
    }

    public SvgImportOptionsPanel(Window owner, java.io.File sourceSvg,
            double machineWidth, double machineHeight) {
        super(new BorderLayout());
        machineFormat = ArtworkImportPolicy.machineBed(machineWidth, machineHeight);
        svgDocumentFormat = SvgDocumentSize.read(sourceSvg).orElse(null);
        if (svgDocumentFormat != null) {
            fitToCombo.insertItemAt(USE_SVG_SIZE, 1);
        }
        fitToCombo.setSelectedItem(initialFitSelection(svgDocumentFormat));
        optionsPanel = new ToolboxOptionsPanel(SvgFillColors.read(sourceSvg));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Add artwork", buildImportPanel());
        tabs.addTab("Process artwork (optional)", buildToolboxPanel(sourceSvg));
        tabs.addChangeListener(e -> resizeDialog(tabs.getSelectedIndex() == 0
                ? advancedImportVisible ? ADVANCED_IMPORT_DIALOG_SIZE : BASIC_DIALOG_SIZE
                : SVG_PROCESSING_DIALOG_SIZE));

        preview=new ImportPreviewPanel(machineWidth,machineHeight,()->collectResult(false));
        tabs.setMinimumSize(new Dimension(260,180));
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,tabs,preview);
        split.setResizeWeight(.4);split.setDividerLocation(360);split.setBorder(null);
        add(split,BorderLayout.CENTER);
        watch(tabs);
        preview.setSource(sourceSvg);

        fitToCombo.addActionListener(e -> {
            updateSizeControls();
            updateSafeFitSummary();
            updateImportButtonState();
        });
        paddingSpinner.addChangeListener(e -> updateSafeFitSummary());
        preservePageCheck.addActionListener(e -> {
            updateSizeControls();
            updateSafeFitSummary();
        });
        customSizeField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateImportButtonState(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateImportButtonState(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateImportButtonState(); }
        });
        updateSafeFitSummary();
        updateSizeControls();
        updateImportButtonState();


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
        WorkspaceStyle.primary(okBtn);
        okBtn.setOpaque(ready);
    }

    private JPanel buildImportPanel() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel basic = new JPanel();
        basic.setLayout(new BoxLayout(basic, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel("Artwork size");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 16f));
        safeFitSummary.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        basic.add(heading);
        safeFitSummary.setMaximumSize(new Dimension(Integer.MAX_VALUE,70));
        basic.add(safeFitSummary);
        JPanel sizing=new JPanel(new GridLayout(0,2,6,6));
        sizing.add(new JLabel("Size policy"));sizing.add(fitToCombo);
        sizing.add(new JLabel("Custom size (mm)"));sizing.add(customSizeField);
        sizing.add(new JLabel("Margin (mm)"));sizing.add(paddingSpinner);
        basic.add(sizing);basic.add(keepAspectRatioCheck);basic.add(preservePageCheck);

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
        for(Component child:basic.getComponents())if(child instanceof JComponent component)component.setAlignmentX(LEFT_ALIGNMENT);
        root.add(basic, BorderLayout.NORTH);
        JPanel details=new JPanel(new BorderLayout());details.add(advanced,BorderLayout.NORTH);
        JScrollPane scroll=new JScrollPane(details);scroll.setBorder(null);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        root.add(scroll,BorderLayout.CENTER);
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




        gbc.gridx = 0;
        gbc.gridwidth = 2;

        gbc.gridy++;
        preservePageCheck.setToolTipText(
                "Map the complete SVG viewBox to the selected size instead of enlarging only visible artwork");

        gbc.gridy++;
        form.add(mirrorCheck, gbc);

        customSizeField.setEnabled(false);
        return form;
    }

    private void updateSafeFitSummary() {
        double padding = ((Number) paddingSpinner.getValue()).doubleValue();
        String selection = (String) fitToCombo.getSelectedItem();
        if (FIT_TO_MACHINE.equals(selection)) {
            safeFitSummary.setText(preservePageCheck.isSelected()
                    ? "Map the complete SVG page to the machine bed with a "
                            + formatNumber(padding) + " mm safety margin."
                    : ArtworkImportPolicy.summary(machineFormat, padding));
        } else if (USE_SVG_SIZE.equals(selection)) {
            safeFitSummary.setText(String.format(
                    "Keep the SVG document size: %.1f × %.1f mm (no added margin).",
                    svgDocumentFormat.width(), svgDocumentFormat.height()));
        } else {
            safeFitSummary.setText(preservePageCheck.isSelected()
                    ? "Map the complete SVG page to " + selection + " with a "
                            + formatNumber(padding) + " mm safety margin."
                    : "Advanced target: " + selection + " with a "
                            + formatNumber(padding) + " mm safety margin.");
        }
    }

    private void updateSizeControls() {
        String selection = (String) fitToCombo.getSelectedItem();
        customSizeField.setEnabled("Custom".equals(selection));
        paddingSpinner.setEnabled(!USE_SVG_SIZE.equals(selection));
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : Double.toString(value);
    }

    /** Resolves a labelled paper preset such as "A4 — 210 × 297 mm" to its ISO dimensions. */
    static PaperFormat resolvePaperPreset(String selection) {
        if (selection == null) return null;
        int separator = selection.indexOf(' ');
        String name = separator < 0 ? selection : selection.substring(0, separator);
        return PaperFormat.fromString(name);
    }

    static String[] paperSizeOptions() {
        return PAPER_SIZE_OPTIONS.clone();
    }

    /** Prefer the SVG's declared physical dimensions; safely fit to the bed when none exist. */
    static String initialFitSelection(PaperFormat svgSize) {
        return svgSize == null ? FIT_TO_MACHINE : USE_SVG_SIZE;
    }

    /** Keeps every import view inside the supported 1024x800 screen; long expert forms scroll. */
    private void resizeDialog(Dimension requested) {
        revalidate();
    }

    private JComponent buildToolboxPanel(java.io.File sourceSvg) {
        JPanel form = new JPanel(new BorderLayout(8, 6));
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel intro = new JLabel("Choose a goal, then check the preview. Keep artwork unchanged is always available.");
        form.add(WorkspaceStyle.note("Choose a processing goal. The preview shows its physical result."), BorderLayout.NORTH);
        JScrollPane controls = new JScrollPane(optionsPanel);
        controls.setBorder(null);
        controls.getVerticalScrollBar().setUnitIncrement(12);
        controls.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        controls.setPreferredSize(new Dimension(330, 420));
        form.add(controls, BorderLayout.CENTER);

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

    void setSource(java.io.File file) {
        PaperFormat next=SvgDocumentSize.read(file).orElse(null);
        svgDocumentFormat=next;
        if(next!=null && ((DefaultComboBoxModel<String>)fitToCombo.getModel()).getIndexOf(USE_SVG_SIZE)<0)fitToCombo.insertItemAt(USE_SVG_SIZE,1);
        if(next==null && USE_SVG_SIZE.equals(fitToCombo.getSelectedItem()))fitToCombo.setSelectedItem(FIT_TO_MACHINE);
        preview.setSource(file);updateSafeFitSummary();
    }
    void configurePreview(org.trostheide.gantry.app.plot.PlotSettings settings){preview.configure(settings);}
    boolean previewReady(){return preview.ready();}
    void onPreviewChange(Runnable callback){preview.onChange(callback);}
    void closePreview(){preview.close();}
    private void watch(Container parent){
        for(Component c:parent.getComponents()){
            if(c instanceof JSpinner spinner)spinner.addChangeListener(e->preview.schedule());
            else if(c instanceof JComboBox<?> combo)combo.addActionListener(e->preview.schedule());
            else if(c instanceof AbstractButton button)button.addActionListener(e->preview.schedule());
            else if(c instanceof JTextField field)field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
                public void insertUpdate(javax.swing.event.DocumentEvent e){preview.schedule();}
                public void removeUpdate(javax.swing.event.DocumentEvent e){preview.schedule();}
                public void changedUpdate(javax.swing.event.DocumentEvent e){preview.schedule();}
            });
            if(c instanceof Container child)watch(child);
        }
    }

    Result collectResult() { return collectResult(true); }
    private Result collectResult(boolean report) {
        double maxDrawDistance = ((Number) maxDrawDistanceSpinner.getValue()).doubleValue();
        String station = stationField.getText().trim();
        if (station.isEmpty()) {
            station = "default_station";
        }
        double curveStep = ((Number) curveStepSpinner.getValue()).doubleValue();
        String fitToSelection = (String) fitToCombo.getSelectedItem();
        boolean preservePage = preservePageCheck.isSelected()
                || USE_SVG_SIZE.equals(fitToSelection);
        double padding = USE_SVG_SIZE.equals(fitToSelection)
                ? 0.0
                : ((Number) paddingSpinner.getValue()).doubleValue();
        boolean keepAspect = keepAspectRatioCheck.isSelected();
        boolean mirror = mirrorCheck.isSelected();

        PaperFormat format = FIT_TO_MACHINE.equals(fitToSelection)
                ? machineFormat
                : USE_SVG_SIZE.equals(fitToSelection)
                    ? svgDocumentFormat
                : "Custom".equals(fitToSelection)
                    ? PaperFormat.fromString(customSizeField.getText().trim())
                    : resolvePaperPreset(fitToSelection);

        if (format == null) {
            if(report)JOptionPane.showMessageDialog(this, "Custom size must be 'WxH' in mm, e.g. 210x297.",
                    "Invalid size", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        SvgImportOptions importOptions =
                SvgImportOptions.fitToFormat(maxDrawDistance, station, curveStep, format, padding,
                        mirror, keepAspect, preservePage);

        // Enabling a processing feature implies running the SVGToolBox pipeline — otherwise
        // the visible settings would be silently dropped.
        Config toolboxConfig = null;
        if (optionsPanel.hasProcessingEnabled()) {
            try {
                toolboxConfig = optionsPanel.buildConfig();
            } catch (IllegalArgumentException ex) {
                if(report)JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid SVGToolBox option",
                        JOptionPane.ERROR_MESSAGE);
                return null;
            }
        }

        return new Result(importOptions, toolboxConfig);
    }
}
