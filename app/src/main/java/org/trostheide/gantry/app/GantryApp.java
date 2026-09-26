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
        if (args.length > 0 && "--self-test".equals(args[0])) {
            System.exit(org.trostheide.gantry.app.diagnostics.InstallationCheck.execute(
                    java.util.Arrays.copyOfRange(args, 1, args.length)));
            return;
        }
        java.nio.file.Path smokeReport = null;
        if (args.length > 0 && "--smoke-test".equals(args[0])) {
            if (args.length != 2) throw new IllegalArgumentException("--smoke-test requires a report path");
            smokeReport = java.nio.file.Path.of(args[1]).toAbsolutePath();
            try {
                java.nio.file.Path profile = java.nio.file.Files.createTempDirectory(smokeReport.getParent(), "gantry-smoke-profile-");
                System.setProperty("gantry.config.file", profile.resolve("config.json").toString());
                var config = new org.trostheide.gantry.app.plot.GantryConfig();
                config.mock = true;
                org.trostheide.gantry.app.plot.ConfigStore.save(config, profile.resolve("config.json").toFile());
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("Could not create smoke-test profile", ex);
            }
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                error.printStackTrace();
                System.exit(1);
            });
        }
        final java.nio.file.Path report = smokeReport;
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
            if (report != null) {
                javax.swing.Timer timer = new javax.swing.Timer(1500, event -> {
                    try {
                        if (!frame.isShowing()) throw new IllegalStateException("GUI is not visible");
                        java.nio.file.Files.writeString(report,
                                "GUI ready " + GantryApp.class.getPackage().getImplementationVersion());
                        frame.dispose();
                        System.exit(0);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        System.exit(1);
                    }
                });
                timer.setRepeats(false);
                timer.start();
            }
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
