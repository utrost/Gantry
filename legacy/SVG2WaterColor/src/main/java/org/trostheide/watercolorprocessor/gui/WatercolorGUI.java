package org.trostheide.watercolorprocessor.gui;

import javax.swing.*;
import com.formdev.flatlaf.FlatDarkLaf;

public class WatercolorGUI {
    public static void main(String[] args) {
        FlatDarkLaf.setup();

        // Global UI refinements
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("TabbedPane.tabSeparatorsFullHeight", true);
        UIManager.put("TitlePane.unifiedBackground", true);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("defaultFont", new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 13));

        SwingUtilities.invokeLater(() -> {
            new MainFrame().setVisible(true);
        });
    }
}
