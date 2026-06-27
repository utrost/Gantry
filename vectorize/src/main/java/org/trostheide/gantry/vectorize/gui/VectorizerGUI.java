package org.trostheide.gantry.vectorize.gui;

import javax.swing.*;
import java.awt.*;

public class VectorizerGUI {

    private static boolean isDarkTheme = true;

    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            applyTheme(isDarkTheme);

            // Global UI tweaks for a cleaner feel
            UIManager.put("TitlePane.unifiedBackground", true);
            UIManager.put("Component.focusWidth", 1);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("ProgressBar.arc", 999);
            UIManager.put("TabbedPane.showTabSeparators", true);

            MainFrame frame = new MainFrame();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();
            frame.setSize(1400, 900);
            frame.setMinimumSize(new Dimension(900, 600));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    public static void applyTheme(boolean dark) {
        isDarkTheme = dark;
        try {
            if (dark) {
                UIManager.setLookAndFeel("com.formdev.flatlaf.FlatDarkLaf");
            } else {
                UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
            }
        } catch (Exception e) {
            // FlatLaf not available — fall back to system L&F
            System.err.println("FlatLaf not available, using system L&F: " + e.getMessage());
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                System.err.println("Could not set Look and Feel: " + ex.getMessage());
            }
        }
    }

    public static boolean isDarkTheme() {
        return isDarkTheme;
    }

    public static void toggleTheme() {
        applyTheme(!isDarkTheme);
        // Update all open windows
        for (Window w : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w);
        }
    }
}
