package org.trostheide.svgtoolbox.ui;

import javax.swing.*;
import java.awt.*;

public class MainWindow extends JFrame {

    private PreviewPanel previewPanel;
    private ControlPanel controlPanel;

    public MainWindow() {
        super("SVG Toolbox");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1024, 768);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());

        // Menu bar
        JMenuBar menuBar = new JMenuBar();
        JMenu viewMenu = new JMenu("View");
        JCheckBoxMenuItem darkModeItem = new JCheckBoxMenuItem("Dark Mode");
        darkModeItem.setAccelerator(KeyStroke.getKeyStroke("control D"));
        darkModeItem.setSelected(GuiRunner.isDarkMode());
        darkModeItem.addActionListener(e -> {
            GuiRunner.toggleDarkMode();
            darkModeItem.setSelected(GuiRunner.isDarkMode());
        });
        viewMenu.add(darkModeItem);
        menuBar.add(viewMenu);
        setJMenuBar(menuBar);

        previewPanel = new PreviewPanel();
        controlPanel = new ControlPanel(this);

        add(previewPanel, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.WEST);
    }

    public PreviewPanel getPreviewPanel() {
        return previewPanel;
    }

    public void setInputFile(java.io.File file) {
        setTitle("SVG Toolbox - " + file.getName());
        previewPanel.loadFile(file);
        controlPanel.setInputFile(file);
    }
}
