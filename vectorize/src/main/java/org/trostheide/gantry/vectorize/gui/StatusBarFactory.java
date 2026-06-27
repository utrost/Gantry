package org.trostheide.gantry.vectorize.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;

class StatusBarFactory {

    private final JLabel imageDimsLabel;
    private final JLabel statsLabel;
    private final JLabel zoomLabel;
    private final JPanel panel;

    StatusBarFactory() {
        panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                new EmptyBorder(3, 10, 3, 10)));

        imageDimsLabel = new JLabel("No image loaded");
        imageDimsLabel.setFont(imageDimsLabel.getFont().deriveFont(11f));
        panel.add(imageDimsLabel, BorderLayout.WEST);

        statsLabel = new JLabel(" ");
        statsLabel.setFont(statsLabel.getFont().deriveFont(11f));
        statsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(statsLabel, BorderLayout.CENTER);

        zoomLabel = new JLabel("100%");
        zoomLabel.setFont(zoomLabel.getFont().deriveFont(11f));
        panel.add(zoomLabel, BorderLayout.EAST);
    }

    JPanel getPanel() {
        return panel;
    }

    void setImageInfo(String text) {
        imageDimsLabel.setText(text);
    }

    void updateZoomLabel(int zoomPercent) {
        zoomLabel.setText(zoomPercent + "%");
    }

    void updateStats(File svgFile, long elapsedMs) {
        try {
            long sizeBytes = svgFile.length();
            String content = new String(Files.readAllBytes(svgFile.toPath()));
            int pathCount = 0;
            int idx = 0;
            while ((idx = content.indexOf("<p", idx)) != -1) {
                if (content.startsWith("<polyline", idx) ||
                    content.startsWith("<polygon", idx) ||
                    content.startsWith("<path", idx)) {
                    pathCount++;
                }
                idx++;
            }
            String sizeStr = sizeBytes < 1024 ? sizeBytes + " B" :
                    sizeBytes < 1024 * 1024 ? String.format("%.1f KB", sizeBytes / 1024.0) :
                    String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
            String timeStr = elapsedMs < 1000 ? elapsedMs + " ms" :
                    String.format("%.1f s", elapsedMs / 1000.0);
            statsLabel.setText(String.format("Paths: %,d  |  Size: %s  |  Time: %s", pathCount, sizeStr, timeStr));
        } catch (Exception ex) {
            statsLabel.setText(" ");
        }
    }
}
