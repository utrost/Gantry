package org.trostheide.watercolorprocessor.gui;

import org.trostheide.watercolorprocessor.ProcessorService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.function.Consumer;

public class SvgDrawPanel extends JPanel {

    private final JTextField svgField;
    private final JSpinner curveStepSpinner;
    private final JSpinner targetWidthSpinner;
    private final JSpinner targetHeightSpinner;
    private final JCheckBox keepAspectCheckBox;
    private final JSpinner posXSpinner;
    private final JSpinner posYSpinner;
    private final JComboBox<String> presetCombo;
    private final JCheckBox mirrorCheckBox;
    private final JButton convertButton;
    private final JTextArea logArea;
    private final JProgressBar progressBar;
    private final SettingsPanel settingsPanel;

    private boolean updatingPreset = false;
    private double aspectRatio = 1.0;
    private Consumer<File> onJsonReady;

    public SvgDrawPanel(SettingsPanel settingsPanel) {
        this.settingsPanel = settingsPanel;
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        // --- File Selection ---
        JPanel filePanel = new JPanel(new GridBagLayout());
        filePanel.setBorder(createSection("SVG Input"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        filePanel.add(label("SVG File"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        svgField = new JTextField();
        svgField.setToolTipText("Path to the SVG file to draw (supports Inkscape layers)");
        filePanel.add(svgField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        JButton browseBtn = new JButton("Browse");
        browseBtn.addActionListener(e -> selectSvgFile());
        filePanel.add(browseBtn, gbc);

        topPanel.add(filePanel);
        topPanel.add(Box.createVerticalStrut(4));

        // --- Size & Position ---
        JPanel sizePanel = new JPanel(new GridBagLayout());
        sizePanel.setBorder(createSection("Size & Position (mm)"));
        GridBagConstraints sg = new GridBagConstraints();
        sg.insets = new Insets(4, 8, 4, 8);
        sg.fill = GridBagConstraints.HORIZONTAL;
        sg.anchor = GridBagConstraints.WEST;

        // Row 0: Preset + Curve Step
        sg.gridx = 0; sg.gridy = 0; sg.weightx = 0;
        sizePanel.add(label("Preset"), sg);

        sg.gridx = 1; sg.weightx = 0.3;
        presetCombo = new JComboBox<>(new String[]{"Machine", "A5 (148x210)", "A4 (210x297)", "A3 (297x420)", "Custom"});
        presetCombo.addActionListener(e -> applyPreset());
        sizePanel.add(presetCombo, sg);

        sg.gridx = 2; sg.weightx = 0;
        sizePanel.add(label("Curve Step"), sg);

        sg.gridx = 3; sg.weightx = 0.3;
        curveStepSpinner = new JSpinner(new SpinnerNumberModel(0.05, 0.001, 10.0, 0.01));
        curveStepSpinner.setToolTipText("Resolution for linearizing curves (smaller = smoother)");
        sizePanel.add(curveStepSpinner, sg);

        // Row 1: Width + Height
        sg.gridx = 0; sg.gridy = 1; sg.weightx = 0;
        sizePanel.add(label("Width"), sg);

        sg.gridx = 1; sg.weightx = 0.3;
        targetWidthSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 10000.0, 10.0));
        targetWidthSpinner.setToolTipText("Target width in mm (0 = original size)");
        targetWidthSpinner.addChangeListener(e -> onWidthChanged());
        sizePanel.add(targetWidthSpinner, sg);

        sg.gridx = 2; sg.weightx = 0;
        sizePanel.add(label("Height"), sg);

        sg.gridx = 3; sg.weightx = 0.3;
        targetHeightSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 10000.0, 10.0));
        targetHeightSpinner.setToolTipText("Target height in mm (0 = original size)");
        targetHeightSpinner.addChangeListener(e -> onHeightChanged());
        sizePanel.add(targetHeightSpinner, sg);

        // Row 2: Position X + Y
        sg.gridx = 0; sg.gridy = 2; sg.weightx = 0;
        sizePanel.add(label("Pos X"), sg);

        sg.gridx = 1; sg.weightx = 0.3;
        posXSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 10000.0, 1.0));
        posXSpinner.setToolTipText("X position on the machine bed in mm (from origin)");
        sizePanel.add(posXSpinner, sg);

        sg.gridx = 2; sg.weightx = 0;
        sizePanel.add(label("Pos Y"), sg);

        sg.gridx = 3; sg.weightx = 0.3;
        posYSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 10000.0, 1.0));
        posYSpinner.setToolTipText("Y position on the machine bed in mm (from origin)");
        sizePanel.add(posYSpinner, sg);

        // Row 3: Options
        sg.gridx = 0; sg.gridy = 3; sg.weightx = 0; sg.gridwidth = 2;
        keepAspectCheckBox = new JCheckBox("Keep Aspect Ratio", true);
        sizePanel.add(keepAspectCheckBox, sg);

        sg.gridx = 2; sg.gridwidth = 2;
        mirrorCheckBox = new JCheckBox("Mirror Horizontally");
        sizePanel.add(mirrorCheckBox, sg);

        topPanel.add(sizePanel);
        topPanel.add(Box.createVerticalStrut(8));

        // --- Action Bar ---
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
        convertButton = new JButton("Convert & Plot");
        convertButton.setFont(convertButton.getFont().deriveFont(Font.BOLD, 14f));
        convertButton.setPreferredSize(new Dimension(180, 38));
        convertButton.putClientProperty("JButton.buttonType", "roundRect");
        convertButton.addActionListener(e -> startConversion());
        actionBar.add(convertButton);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(200, 8));
        actionBar.add(progressBar);

        topPanel.add(actionBar);

        add(topPanel, BorderLayout.NORTH);

        // === Center: Log ===
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(createSection("Conversion Log"));
        add(scrollPane, BorderLayout.CENTER);

        // Apply initial preset
        applyPreset();
    }

    public void setOnJsonReady(Consumer<File> callback) {
        this.onJsonReady = callback;
    }

    private void applyPreset() {
        if (updatingPreset) return;
        updatingPreset = true;

        String preset = (String) presetCombo.getSelectedItem();
        double w = 0, h = 0;

        if ("Machine".equals(preset)) {
            w = settingsPanel.getMachineWidth();
            h = settingsPanel.getMachineHeight();
        } else if (preset != null && preset.startsWith("A5")) {
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

    private void selectSvgFile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SVG Files", "svg"));
        File current = new File(svgField.getText());
        if (current.exists())
            fc.setSelectedFile(current);
        else
            fc.setCurrentDirectory(new File("."));

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            svgField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void startConversion() {
        String svgPath = svgField.getText().trim();
        if (svgPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select an SVG file.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File svgFile = new File(svgPath);
        if (!svgFile.exists()) {
            JOptionPane.showMessageDialog(this, "File not found: " + svgPath, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        logArea.setText("");
        convertButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setVisible(true);

        double curveStep = (Double) curveStepSpinner.getValue();
        double targetW = (Double) targetWidthSpinner.getValue();
        double targetH = (Double) targetHeightSpinner.getValue();
        boolean keepAspect = keepAspectCheckBox.isSelected();
        double posX = (Double) posXSpinner.getValue();
        double posY = (Double) posYSpinner.getValue();
        boolean mirror = mirrorCheckBox.isSelected();

        new SwingWorker<File, String>() {
            @Override
            protected File doInBackground() throws Exception {
                publish("Converting SVG for pen drawing (no refills)...");
                publish(String.format("Input: %s", svgFile.getAbsolutePath()));
                if (targetW > 0 && targetH > 0) {
                    publish(String.format(java.util.Locale.US,
                            "Target: %.0f x %.0f mm at (%.0f, %.0f)", targetW, targetH, posX, posY));
                }

                File tempJson = File.createTempFile("svgdraw_", ".json");
                tempJson.deleteOnExit();

                ProcessorService service = new ProcessorService();
                service.process(svgFile, tempJson, 0, "pen",
                        curveStep, targetW, targetH, keepAspect, posX, posY, mirror);

                publish("Conversion complete.");
                return tempJson;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String line : chunks) {
                    logArea.append(line + "\n");
                }
            }

            @Override
            protected void done() {
                convertButton.setEnabled(true);
                progressBar.setIndeterminate(false);
                progressBar.setVisible(false);
                try {
                    File jsonFile = get();
                    logArea.append("Ready to plot.\n");
                    if (onJsonReady != null) {
                        onJsonReady.accept(jsonFile);
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    logArea.append("Error: " + cause.getMessage() + "\n");
                    JOptionPane.showMessageDialog(SvgDrawPanel.this,
                            "Error: " + cause.getMessage(), "Conversion Failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
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
}
