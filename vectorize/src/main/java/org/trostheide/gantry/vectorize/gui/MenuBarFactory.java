package org.trostheide.gantry.vectorize.gui;

import javax.swing.*;
import java.awt.event.KeyEvent;

class MenuBarFactory {

    static JMenuBar create(MainFrame frame) {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem openItem = new JMenuItem("Open Image...");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
        openItem.addActionListener(frame::openImage);
        fileMenu.add(openItem);

        fileMenu.addSeparator();

        JMenuItem saveSvgItem = new JMenuItem("Save SVG...");
        saveSvgItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK));
        saveSvgItem.addActionListener(frame::saveSvg);
        fileMenu.add(saveSvgItem);

        JMenuItem savePngItem = new JMenuItem("Save PNG...");
        savePngItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
                KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));
        savePngItem.addActionListener(frame::savePng);
        fileMenu.add(savePngItem);

        fileMenu.addSeparator();

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);

        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic(KeyEvent.VK_E);

        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK));
        undoItem.addActionListener(e -> {
            if (frame.controlsPanel.undo()) frame.updateUndoRedoButtons();
        });
        editMenu.add(undoItem);

        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK));
        redoItem.addActionListener(e -> {
            if (frame.controlsPanel.redo()) frame.updateUndoRedoButtons();
        });
        editMenu.add(redoItem);

        menuBar.add(editMenu);

        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);

        JMenuItem fitToWindowItem = new JMenuItem("Fit to Window");
        fitToWindowItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, KeyEvent.CTRL_DOWN_MASK));
        fitToWindowItem.addActionListener(e -> frame.originalPanel.fitToWindow());
        viewMenu.add(fitToWindowItem);

        JMenuItem zoomInItem = new JMenuItem("Zoom In");
        zoomInItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.CTRL_DOWN_MASK));
        zoomInItem.addActionListener(e -> frame.originalPanel.zoomIn());
        viewMenu.add(zoomInItem);

        JMenuItem zoomOutItem = new JMenuItem("Zoom Out");
        zoomOutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, KeyEvent.CTRL_DOWN_MASK));
        zoomOutItem.addActionListener(e -> frame.originalPanel.zoomOut());
        viewMenu.add(zoomOutItem);

        viewMenu.addSeparator();

        JCheckBoxMenuItem toggleEdges = new JCheckBoxMenuItem("Show Edge Overlay");
        toggleEdges.addActionListener(e -> {
            frame.originalPanel.setShowEdgeOverlay(toggleEdges.isSelected());
            if (frame.edgeToggle != null) frame.edgeToggle.setSelected(toggleEdges.isSelected());
        });
        viewMenu.add(toggleEdges);

        JCheckBoxMenuItem toggleRoi = new JCheckBoxMenuItem("Select Region (ROI)");
        toggleRoi.addActionListener(e -> {
            frame.originalPanel.setRoiMode(toggleRoi.isSelected());
            if (frame.roiToggle != null) frame.roiToggle.setSelected(toggleRoi.isSelected());
        });
        viewMenu.add(toggleRoi);

        JMenuItem clearRoiItem = new JMenuItem("Clear Region Selection");
        clearRoiItem.addActionListener(e -> frame.originalPanel.clearRoi());
        viewMenu.add(clearRoiItem);

        viewMenu.addSeparator();

        JMenuItem toggleThemeItem = new JMenuItem("Toggle Dark/Light Mode");
        toggleThemeItem.addActionListener(e -> VectorizerGUI.toggleTheme());
        viewMenu.add(toggleThemeItem);

        menuBar.add(viewMenu);

        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);

        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(frame,
                "Vectorize v1.1\n\nConverts raster images to clean SVG vector graphics.\n\n"
                + "Strategies: DP, Line, ConvexHull, Bezier, Bezier2, Centerline\n\n"
                + "Copyright 2025-2026 Uwe Trostheide\nLicense: AGPL-3.0",
                "About Vectorize", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(aboutItem);

        JMenuItem shortcutsItem = new JMenuItem("Keyboard Shortcuts");
        shortcutsItem.addActionListener(e -> JOptionPane.showMessageDialog(frame,
                "Ctrl+O          Open Image\n"
                + "Ctrl+S          Save SVG\n"
                + "Ctrl+Shift+S    Save PNG\n"
                + "Ctrl+Z          Undo\n"
                + "Ctrl+Y          Redo\n"
                + "Ctrl+0          Fit to Window\n"
                + "Ctrl++/-        Zoom In/Out\n"
                + "Space           Update Preview\n"
                + "Escape          Cancel\n"
                + "Alt+1-5         Quick Presets",
                "Keyboard Shortcuts", JOptionPane.PLAIN_MESSAGE));
        helpMenu.add(shortcutsItem);

        menuBar.add(helpMenu);

        return menuBar;
    }
}
