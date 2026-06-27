package org.trostheide.gantry.vectorize.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

class ToolbarFactory {

    record Result(JToolBar toolBar, JProgressBar progressBar, JLabel statusLabel,
                  JButton cancelBtn, JButton undoBtn, JButton redoBtn,
                  JToggleButton edgeToggle, JToggleButton roiToggle, JButton clearRoiBtn) {}

    static Result create(MainFrame frame) {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
                new EmptyBorder(4, 8, 4, 8)));

        JButton openBtn = createButton("Open", "Open an image file (Ctrl+O)");
        openBtn.addActionListener(frame::openImage);
        toolBar.add(openBtn);

        JButton saveBtn = createButton("Save SVG", "Save as SVG (Ctrl+S)");
        saveBtn.addActionListener(frame::saveSvg);
        toolBar.add(saveBtn);

        JButton savePngBtn = createButton("Save PNG", "Export as PNG (Ctrl+Shift+S)");
        savePngBtn.addActionListener(frame::savePng);
        toolBar.add(savePngBtn);

        toolBar.addSeparator(new Dimension(12, 0));

        JButton processBtn = createButton("Vectorize", "Run vectorization (Space)");
        processBtn.putClientProperty("JButton.buttonType", "roundRect");
        processBtn.addActionListener(e -> frame.runVectorization());
        toolBar.add(processBtn);

        JButton cancelBtn = createButton("Cancel", "Cancel current operation (Escape)");
        cancelBtn.setEnabled(false);
        cancelBtn.addActionListener(e -> frame.cancelVectorization());
        toolBar.add(cancelBtn);

        toolBar.addSeparator(new Dimension(12, 0));

        JButton undoBtn = createButton("Undo", "Undo last parameter change (Ctrl+Z)");
        undoBtn.setEnabled(false);
        undoBtn.addActionListener(e -> {
            if (frame.controlsPanel.undo()) frame.updateUndoRedoButtons();
        });
        toolBar.add(undoBtn);

        JButton redoBtn = createButton("Redo", "Redo (Ctrl+Y)");
        redoBtn.setEnabled(false);
        redoBtn.addActionListener(e -> {
            if (frame.controlsPanel.redo()) frame.updateUndoRedoButtons();
        });
        toolBar.add(redoBtn);

        toolBar.addSeparator(new Dimension(12, 0));

        JToggleButton edgeToggle = new JToggleButton("Edges");
        edgeToggle.setToolTipText("Toggle Canny edge overlay on source image");
        edgeToggle.setFocusable(false);
        edgeToggle.addActionListener(e -> frame.originalPanel.setShowEdgeOverlay(edgeToggle.isSelected()));
        toolBar.add(edgeToggle);

        JToggleButton roiToggle = new JToggleButton("Crop");
        roiToggle.setToolTipText("Select a region of interest to vectorize");
        roiToggle.setFocusable(false);
        roiToggle.addActionListener(e -> frame.originalPanel.setRoiMode(roiToggle.isSelected()));
        toolBar.add(roiToggle);

        JButton clearRoiBtn = createButton("Clear ROI", "Clear region selection");
        clearRoiBtn.setEnabled(false);
        clearRoiBtn.addActionListener(e -> frame.originalPanel.clearRoi());
        toolBar.add(clearRoiBtn);

        JButton fitBtn = createButton("Fit", "Fit image to window (Ctrl+0)");
        fitBtn.addActionListener(e -> frame.originalPanel.fitToWindow());
        toolBar.add(fitBtn);

        toolBar.add(Box.createHorizontalGlue());

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(120, 18));
        progressBar.setMaximumSize(new Dimension(140, 18));
        toolBar.add(progressBar);

        JLabel statusLabel = new JLabel("Ready");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        statusLabel.setBorder(new EmptyBorder(0, 8, 0, 4));
        toolBar.add(statusLabel);

        return new Result(toolBar, progressBar, statusLabel, cancelBtn, undoBtn, redoBtn, edgeToggle, roiToggle, clearRoiBtn);
    }

    private static JButton createButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setToolTipText(tooltip);
        btn.setFocusable(false);
        btn.putClientProperty("JButton.buttonType", "toolBarButton");
        return btn;
    }
}
