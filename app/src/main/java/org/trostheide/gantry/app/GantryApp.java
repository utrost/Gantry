package org.trostheide.gantry.app;

import com.formdev.flatlaf.FlatDarkLaf;
import org.trostheide.gantry.app.gui.PlotterPanel;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Entry point for the Gantry GUI application: launches the standalone plotter window. */
public final class GantryApp {

    private GantryApp() {
    }

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Gantry");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            PlotterPanel panel = new PlotterPanel();
            frame.setJMenuBar(panel.buildMenuBar());
            frame.setContentPane(panel);
            frame.setSize(1280, 820);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
