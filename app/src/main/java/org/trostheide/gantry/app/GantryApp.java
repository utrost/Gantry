package org.trostheide.gantry.app;

import com.formdev.flatlaf.FlatDarkLaf;
import org.trostheide.gantry.app.gui.PlotterPanel;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
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
            frame.setBounds(initialBounds(GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getMaximumWindowBounds()));
            frame.setVisible(true);
        });
    }

    /** Keeps the first window completely inside the usable area, including on a 1024x800 display. */
    static Rectangle initialBounds(Rectangle usableBounds) {
        int width = Math.min(1280, usableBounds.width);
        int height = Math.min(820, usableBounds.height);
        int x = usableBounds.x + Math.max(0, (usableBounds.width - width) / 2);
        int y = usableBounds.y + Math.max(0, (usableBounds.height - height) / 2);
        return new Rectangle(x, y, width, height);
    }
}
