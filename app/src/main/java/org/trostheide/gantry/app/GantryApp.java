package org.trostheide.gantry.app;

import com.formdev.flatlaf.FlatDarkLaf;
import org.trostheide.gantry.app.gui.PlotterPanel;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** Entry point for the Gantry GUI application: launches the standalone plotter window. */
public final class GantryApp {

    private GantryApp() {
    }

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Gantry");
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            PlotterPanel panel = new PlotterPanel();
            frame.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){if(panel.requestClose()){frame.dispose();System.exit(0);}}});
            frame.setJMenuBar(panel.buildMenuBar());
            frame.setContentPane(panel);
            frame.setSize(1280, 820);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
