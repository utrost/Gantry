package org.trostheide.watercolorprocessor.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class MainFrame extends JFrame {

    private final JLabel statusLabel;
    private final JPanel statusDot;

    public MainFrame() {
        setTitle("SVG2WaterColor  |  Watercolor Plotter Control");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 720);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Shared Status Area for Processor
        JTextArea statusArea = new JTextArea();
        statusArea.setEditable(false);
        statusArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));

        // Create Panels
        SettingsPanel settingsPanel = new SettingsPanel();
        PlotterPanel plotterPanel = new PlotterPanel(settingsPanel);
        ProcessorPanel processorPanel = new ProcessorPanel(statusArea);

        // Settings Dialog
        SettingsDialog settingsDialog = new SettingsDialog(this, settingsPanel);

        // Menu Bar
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem settingsItem = new JMenuItem("Settings...");
        settingsItem.setAccelerator(KeyStroke.getKeyStroke("ctrl COMMA"));
        settingsItem.addActionListener(e -> settingsDialog.setVisible(true));
        fileMenu.add(settingsItem);
        fileMenu.addSeparator();
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> dispose());
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);

        // Draw SVG Panel
        SvgDrawPanel svgDrawPanel = new SvgDrawPanel(settingsPanel);

        // Tabs
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(tabbedPane.getFont().deriveFont(Font.BOLD, 13f));
        tabbedPane.addTab("  Process SVG  ", processorPanel);
        tabbedPane.addTab("  Draw SVG  ", svgDrawPanel);
        tabbedPane.addTab("  Plot  ", plotterPanel);

        svgDrawPanel.setOnJsonReady(jsonFile -> {
            plotterPanel.loadJsonFile(jsonFile);
            tabbedPane.setSelectedComponent(plotterPanel);
        });

        add(tabbedPane, BorderLayout.CENTER);

        // --- Status Bar ---
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(new EmptyBorder(4, 12, 4, 12));

        JPanel leftStatus = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftStatus.setOpaque(false);

        statusDot = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(10, 10);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillOval(0, 0, 10, 10);
            }
        };
        statusDot.setOpaque(false);
        statusDot.setBackground(new Color(120, 120, 120));
        leftStatus.add(statusDot);

        statusLabel = new JLabel("Disconnected");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusLabel.setForeground(new Color(150, 150, 150));
        leftStatus.add(statusLabel);

        statusBar.add(leftStatus, BorderLayout.WEST);

        JLabel versionLabel = new JLabel("v1.0-SNAPSHOT");
        versionLabel.setFont(versionLabel.getFont().deriveFont(10f));
        versionLabel.setForeground(new Color(100, 100, 100));
        statusBar.add(versionLabel, BorderLayout.EAST);

        add(statusBar, BorderLayout.SOUTH);
    }

    public void setConnectionStatus(boolean connected) {
        if (connected) {
            statusDot.setBackground(new Color(76, 175, 80));
            statusLabel.setText("Connected");
            statusLabel.setForeground(new Color(76, 175, 80));
        } else {
            statusDot.setBackground(new Color(120, 120, 120));
            statusLabel.setText("Disconnected");
            statusLabel.setForeground(new Color(150, 150, 150));
        }
        statusDot.repaint();
    }
}
