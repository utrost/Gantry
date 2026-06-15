package org.trostheide.svgtoolbox.ui;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

public class GuiRunner {

    private static boolean darkMode = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            applyTheme();
            new MainWindow().setVisible(true);
        });
    }

    static void applyTheme() {
        try {
            UIManager.put("Button.arc", 6);
            UIManager.put("Component.arc", 6);
            UIManager.put("TextComponent.arc", 6);
            UIManager.put("ScrollBar.width", 10);
            UIManager.put("ScrollBar.trackArc", 999);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollPane.smoothScrolling", true);
            UIManager.put("TitlePane.unifiedBackground", true);

            if (darkMode) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean isDarkMode() {
        return darkMode;
    }

    public static void toggleDarkMode() {
        darkMode = !darkMode;
        applyTheme();
        // Update all existing windows
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w);
        }
    }
}
