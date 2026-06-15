package org.trostheide.watercolorprocessor.gui;

import org.trostheide.watercolorprocessor.ProcessorService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;

public class ProcessorPanel extends JPanel {

    private final JTextField inputField;
    private final JTextField outputField;
    private final JSpinner maxDistSpinner;
    private final JSpinner curveStepSpinner;
    private final JTextField stationField;
    private final JSpinner targetWidthSpinner;
    private final JSpinner targetHeightSpinner;
    private final JCheckBox keepAspectCheckBox;
    private final JSpinner posXSpinner;
    private final JSpinner posYSpinner;
    private final JComboBox<String> presetCombo;
    private final JCheckBox mirrorCheckBox;
    private final JTextArea statusArea;
    private final JButton processBtn;
    private final JProgressBar progressBar;

    private boolean updatingPreset = false;
    private double aspectRatio = 1.0;

    public ProcessorPanel(JTextArea statusArea) {

        this.statusArea = statusArea;
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        // --- File Selection ---
        JPanel filePanel = new JPanel(new GridBagLayout());
        filePanel.setBorder(createSection("Input / Output"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        filePanel.add(label("Input SVG"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        inputField = new JTextField();
        inputField.setToolTipText("Path to the SVG file with Inkscape layers");
        filePanel.add(inputField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        JButton inputBtn = new JButton("Browse");
        inputBtn.addActionListener(e -> selectFile(inputField, false));
        filePanel.add(inputBtn, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        filePanel.add(label("Output JSON"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        outputField = new JTextField();
        outputField.setToolTipText("Output path for the generated commands JSON");
        filePanel.add(outputField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        JButton outputBtn = new JButton("Browse");
        outputBtn.addActionListener(e -> selectFile(outputField, true));
        filePanel.add(outputBtn, gbc);

        topPanel.add(filePanel);
        topPanel.add(Box.createVerticalStrut(4));

        // --- Processing Settings ---
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        settingsPanel.setBorder(createSection("Processing Settings"));
        GridBagConstraints sg = new GridBagConstraints();
        sg.insets = new Insets(4, 8, 4, 8);
        sg.fill = GridBagConstraints.HORIZONTAL;
        sg.anchor = GridBagConstraints.WEST;

        // Row 0: Max Distance + Curve Step
        sg.gridx = 0; sg.gridy = 0; sg.weightx = 0;
        settingsPanel.add(label("Max Draw Dist (mm)"), sg);

        sg.gridx = 1; sg.weightx = 0.4;
        maxDistSpinner = new JSpinner(new SpinnerNumberModel(200.0, 10.0, 5000.0, 10.0));
        maxDistSpinner.setToolTipText("Maximum distance to draw before triggering a paint refill");
        settingsPanel.add(maxDistSpinner, sg);

        sg.gridx = 2; sg.weightx = 0;
        settingsPanel.add(label("Curve Step (mm)"), sg);

        sg.gridx = 3; sg.weightx = 0.4;
        curveStepSpinner = new JSpinner(new SpinnerNumberModel(0.05, 0.001, 10.0, 0.01));
        curveStepSpinner.setToolTipText("Resolution for linearizing curves (smaller = smoother)");
        settingsPanel.add(curveStepSpinner, sg);

        // Row 1: Station + Preset
        sg.gridx = 0; sg.gridy = 1; sg.weightx = 0;
        settingsPanel.add(label("Default Station"), sg);

        sg.gridx = 1; sg.weightx = 0.4;
        stationField = new JTextField("default_station");
        stationField.setToolTipText("Fallback station ID for layers without matching names");
        settingsPanel.add(stationField, sg);

        sg.gridx = 2; sg.weightx = 0;
        settingsPanel.add(label("Size Preset"), sg);

        sg.gridx = 3; sg.weightx = 0.4;
        presetCombo = new JComboBox<>(new String[]{"None", "A5 (148x210)", "A4 (210x297)", "A3 (297x420)", "Custom"});
        presetCombo.setSelectedItem("None");
        presetCombo.addActionListener(e -> applyPreset());
        settingsPanel.add(presetCombo, sg);

        topPanel.add(settingsPanel);
        topPanel.add(Box.createVerticalStrut(4));

        // --- Size & Position ---
        JPanel sizePanel = new JPanel(new GridBagLayout());
        sizePanel.setBorder(createSection("Size & Position (mm)"));
        GridBagConstraints pg = new GridBagConstraints();
        pg.insets = new Insets(4, 8, 4, 8);
        pg.fill = GridBagConstraints.HORIZONTAL;
        pg.anchor = GridBagConstraints.WEST;

        // Row 0: Width + Height
        pg.gridx = 0; pg.gridy = 0; pg.weightx = 0;
        sizePanel.add(label("Width"), pg);

        pg.gridx = 1; pg.weightx = 0.3;
        targetWidthSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 10000.0, 10.0));
        targetWidthSpinner.setToolTipText("Target width in mm (0 = original size)");
        targetWidthSpinner.addChangeListener(e -> onWidthChanged());
        sizePanel.add(targetWidthSpinner, pg);

        pg.gridx = 2; pg.weightx = 0;
        sizePanel.add(label("Height"), pg);

        pg.gridx = 3; pg.weightx = 0.3;
        targetHeightSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 10000.0, 10.0));
        targetHeightSpinner.setToolTipText("Target height in mm (0 = original size)");
        targetHeightSpinner.addChangeListener(e -> onHeightChanged());
        sizePanel.add(targetHeightSpinner, pg);

        // Row 1: Position X + Y
        pg.gridx = 0; pg.gridy = 1; pg.weightx = 0;
        sizePanel.add(label("Pos X"), pg);

        pg.gridx = 1; pg.weightx = 0.3;
        posXSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 10000.0, 1.0));
        posXSpinner.setToolTipText("X position on the output in mm");
        sizePanel.add(posXSpinner, pg);

        pg.gridx = 2; pg.weightx = 0;
        sizePanel.add(label("Pos Y"), pg);

        pg.gridx = 3; pg.weightx = 0.3;
        posYSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 10000.0, 1.0));
        posYSpinner.setToolTipText("Y position on the output in mm");
        sizePanel.add(posYSpinner, pg);

        // Row 2: Options
        pg.gridx = 0; pg.gridy = 2; pg.weightx = 0; pg.gridwidth = 2;
        keepAspectCheckBox = new JCheckBox("Keep Aspect Ratio", true);
        sizePanel.add(keepAspectCheckBox, pg);

        pg.gridx = 2; pg.gridwidth = 2;
        mirrorCheckBox = new JCheckBox("Mirror Horizontally");
        mirrorCheckBox.setToolTipText("Flip the design along the X axis before processing");
        sizePanel.add(mirrorCheckBox, pg);

        topPanel.add(sizePanel);
        topPanel.add(Box.createVerticalStrut(8));

        // --- Action Bar ---
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
        processBtn = new JButton("Process SVG");
        processBtn.setFont(processBtn.getFont().deriveFont(Font.BOLD, 14f));
        processBtn.setPreferredSize(new Dimension(180, 38));
        processBtn.putClientProperty("JButton.buttonType", "roundRect");
        processBtn.addActionListener(e -> startProcessing());
        actionBar.add(processBtn);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(200, 8));
        actionBar.add(progressBar);

        topPanel.add(actionBar);

        add(topPanel, BorderLayout.NORTH);

        // === Center: Log ===
        statusArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(statusArea);
        scrollPane.setBorder(createSection("Processor Log"));
        add(scrollPane, BorderLayout.CENTER);
    }

    private void applyPreset() {
        if (updatingPreset) return;
        updatingPreset = true;

        String preset = (String) presetCombo.getSelectedItem();
        double w = 0, h = 0;

        if (preset != null && preset.startsWith("A5")) {
            w = 148; h = 210;
        } else if (preset != null && preset.startsWith("A4")) {
            w = 210; h = 297;
        } else if (preset != null && preset.startsWith("A3")) {
            w = 297; h = 420;
        }

        if (w > 0 && h > 0) {
            targetWidthSpinner.setValue(w);
            targetHeightSpinner.setValue(h);
            aspectRatio = w / h;
        } else if ("None".equals(preset)) {
            targetWidthSpinner.setValue(0.0);
            targetHeightSpinner.setValue(0.0);
        }

        updatingPreset = false;
    }

    private void onWidthChanged() {
        if (updatingPreset) return;
        updatingPreset = true;
        presetCombo.setSelectedItem("Custom");
        if (keepAspectCheckBox.isSelected()) {
            double w = (Double) targetWidthSpinner.getValue();
            double h = (Double) targetHeightSpinner.getValue();
            if (aspectRatio > 0 && w > 0) {
                targetHeightSpinner.setValue(Math.round(w / aspectRatio * 10.0) / 10.0);
            }
            if (w > 0 && h > 0) aspectRatio = w / h;
        }
        updatingPreset = false;
    }

    private void onHeightChanged() {
        if (updatingPreset) return;
        updatingPreset = true;
        presetCombo.setSelectedItem("Custom");
        if (keepAspectCheckBox.isSelected()) {
            double w = (Double) targetWidthSpinner.getValue();
            double h = (Double) targetHeightSpinner.getValue();
            if (aspectRatio > 0 && h > 0) {
                targetWidthSpinner.setValue(Math.round(h * aspectRatio * 10.0) / 10.0);
            }
            if (w > 0 && h > 0) aspectRatio = w / h;
        }
        updatingPreset = false;
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(12f));
        return l;
    }

    private TitledBorder createSection(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD, 12f));
        return border;
    }

    private void selectFile(JTextField target, boolean save) {
        JFileChooser fc = new JFileChooser();
        File current = new File(target.getText());
        if (current.exists())
            fc.setSelectedFile(current);
        else
            fc.setCurrentDirectory(new File("."));

        int res = save ? fc.showSaveDialog(this) : fc.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            target.setText(fc.getSelectedFile().getAbsolutePath());
            if (!save && outputField.getText().isEmpty()) {
                String in = fc.getSelectedFile().getAbsolutePath();
                if (in.toLowerCase().endsWith(".svg")) {
                    outputField.setText(in.substring(0, in.length() - 4) + ".json");
                } else {
                    outputField.setText(in + ".json");
                }
            }
        }
    }

    private void startProcessing() {
        String inPath = inputField.getText();
        String outPath = outputField.getText();

        if (inPath.isEmpty() || outPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select input and output files.", "Input Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        File inFile = new File(inPath);
        File outFile = new File(outPath);

        if (!inFile.exists()) {
            JOptionPane.showMessageDialog(this, "Input file does not exist.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        statusArea.setText("");
        processBtn.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setVisible(true);

        double maxDist = (Double) maxDistSpinner.getValue();
        double curveStep = (Double) curveStepSpinner.getValue();
        String station = stationField.getText();
        double targetW = (Double) targetWidthSpinner.getValue();
        double targetH = (Double) targetHeightSpinner.getValue();
        boolean keepAspect = keepAspectCheckBox.isSelected();
        double posX = (Double) posXSpinner.getValue();
        double posY = (Double) posYSpinner.getValue();
        boolean mirror = mirrorCheckBox.isSelected();

        new SwingWorker<String, String>() {
            @Override
            protected String doInBackground() throws Exception {
                publish("Starting processing...");
                publish("Input: " + inFile.getAbsolutePath());
                publish("Output: " + outFile.getAbsolutePath());
                publish(String.format("Max Distance: %.2f mm", maxDist));
                if (targetW > 0 && targetH > 0) {
                    publish(String.format(java.util.Locale.US,
                            "Target: %.0f x %.0f mm at (%.0f, %.0f)", targetW, targetH, posX, posY));
                }

                ProcessorService service = new ProcessorService();
                service.process(inFile, outFile, maxDist, station,
                        curveStep, targetW, targetH, keepAspect, posX, posY, mirror);

                return "Success! Output written to: " + outFile.getName();
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String line : chunks) {
                    statusArea.append(line + "\n");
                }
            }

            @Override
            protected void done() {
                processBtn.setEnabled(true);
                progressBar.setIndeterminate(false);
                progressBar.setVisible(false);
                try {
                    String result = get();
                    statusArea.append("Done: " + result + "\n");
                    JOptionPane.showMessageDialog(null, result, "Processing Complete",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    statusArea.append("Error: " + cause.getMessage() + "\n");
                    JOptionPane.showMessageDialog(null, "Error: " + cause.getMessage(),
                            "Processing Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
