package org.trostheide.gantry.vectorize.gui;

import org.trostheide.gantry.vectorize.PaintByNumbersProcessor;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class PaletteEditorDialog extends JDialog {

    private final List<JPanel> swatches = new ArrayList<>();
    private final List<Color> colors = new ArrayList<>();
    private JSpinner countSpinner;
    private JPanel swatchGrid;
    private boolean confirmed = false;
    private BufferedImage sourceImage;

    public PaletteEditorDialog(Window owner, Color[] initialPalette, BufferedImage sourceImage) {
        super(owner, "Palette Editor", ModalityType.APPLICATION_MODAL);
        this.sourceImage = sourceImage;
        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        for (Color c : initialPalette) {
            colors.add(c);
        }

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Number of colors:"));
        countSpinner = new JSpinner(new SpinnerNumberModel(initialPalette.length, 2, 20, 1));
        countSpinner.addChangeListener(e -> updateSwatchCount());
        topPanel.add(countSpinner);

        JButton autoBtn = new JButton("Auto from Image");
        autoBtn.setToolTipText("Extract colors from the loaded image");
        autoBtn.setEnabled(sourceImage != null);
        autoBtn.addActionListener(e -> {
            if (sourceImage == null) return;
            int numColors = (Integer) countSpinner.getValue();
            Color[] extracted = PaintByNumbersProcessor.extractPalette(sourceImage, numColors);
            colors.clear();
            for (Color c : extracted) colors.add(c);
            rebuildSwatches();
        });
        topPanel.add(autoBtn);
        add(topPanel, BorderLayout.NORTH);

        swatchGrid = new JPanel(new GridLayout(0, 5, 6, 6));
        rebuildSwatches();
        JScrollPane scroll = new JScrollPane(swatchGrid);
        scroll.setPreferredSize(new Dimension(350, 200));
        add(scroll, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> { confirmed = true; dispose(); });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(300, 250));
        setLocationRelativeTo(owner);
    }

    private void updateSwatchCount() {
        int target = (Integer) countSpinner.getValue();
        while (colors.size() < target) {
            colors.add(Color.getHSBColor(colors.size() / (float) target, 0.8f, 0.9f));
        }
        while (colors.size() > target) {
            colors.remove(colors.size() - 1);
        }
        rebuildSwatches();
    }

    private void rebuildSwatches() {
        swatchGrid.removeAll();
        swatches.clear();

        for (int i = 0; i < colors.size(); i++) {
            final int idx = i;
            JPanel swatch = new JPanel();
            swatch.setPreferredSize(new Dimension(50, 50));
            swatch.setBackground(colors.get(i));
            swatch.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
            swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            swatch.setToolTipText("Color " + (i + 1) + ": click to change");
            swatch.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    Color chosen = JColorChooser.showDialog(PaletteEditorDialog.this,
                            "Choose Color " + (idx + 1), colors.get(idx));
                    if (chosen != null) {
                        colors.set(idx, chosen);
                        swatch.setBackground(chosen);
                    }
                }
            });
            swatches.add(swatch);
            swatchGrid.add(swatch);
        }

        swatchGrid.revalidate();
        swatchGrid.repaint();
    }

    public Color[] getSelectedPalette() {
        if (!confirmed) return null;
        return colors.toArray(new Color[0]);
    }

    public static Color[] showDialog(Window owner, Color[] currentPalette, BufferedImage sourceImage) {
        PaletteEditorDialog dialog = new PaletteEditorDialog(owner, currentPalette, sourceImage);
        dialog.setVisible(true);
        return dialog.getSelectedPalette();
    }
}
