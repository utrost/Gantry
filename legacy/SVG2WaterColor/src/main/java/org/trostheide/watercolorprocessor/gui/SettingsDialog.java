package org.trostheide.watercolorprocessor.gui;

import javax.swing.*;
import java.awt.*;

public class SettingsDialog extends JDialog {

    public SettingsDialog(Frame owner, SettingsPanel settingsPanel) {
        super(owner, "Settings", false);
        setSize(750, 580);
        setLocationRelativeTo(owner);
        setMinimumSize(new Dimension(600, 400));

        JScrollPane scrollPane = new JScrollPane(settingsPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        JButton closeBtn = new JButton("Close");
        closeBtn.putClientProperty("JButton.buttonType", "roundRect");
        closeBtn.addActionListener(e -> setVisible(false));
        buttonBar.add(closeBtn);
        add(buttonBar, BorderLayout.SOUTH);
    }
}
