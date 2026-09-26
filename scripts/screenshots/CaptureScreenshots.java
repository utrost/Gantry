import org.trostheide.gantry.app.GantryApp;
import org.trostheide.gantry.app.gui.PlotterPanel;
import org.trostheide.gantry.app.plot.ConfigStore;
import org.trostheide.gantry.app.plot.GantryConfig;
import org.trostheide.gantry.app.session.GantryProject;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;
import org.trostheide.gantry.pipeline.svgimport.SvgImportStage;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.List;
import java.util.stream.IntStream;

/** Developer-only capture harness: real Swing windows, isolated mock profile, no connection. */
public final class CaptureScreenshots {
    private static JFrame frame;
    private static Robot robot;
    private static Path output;

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Usage: <repo-root> <output-directory> <profile-directory>");
        Path root = Path.of(args[0]).toAbsolutePath();
        output = Path.of(args[1]).toAbsolutePath();
        Path profile = Path.of(args[2]).toAbsolutePath();
        Files.createDirectories(output);
        Files.createDirectories(profile);
        System.setProperty("gantry.config.file", profile.resolve("config.json").toString());
        GantryConfig config = new GantryConfig();
        config.mock = true;
        config.showWelcomeOnStartup = true;
        config.machineOrigin = "Top-Left";
        config.gcode.machineWidth = 210;
        config.gcode.machineHeight = 148;
        config.canvasAlignment = "Center";
        ConfigStore.save(config, profile.resolve("config.json").toFile());
        try {
            robot = new Robot();
            GantryApp.main(new String[0]);
            JDialog welcome = dialog("Your first plot");
            SwingUtilities.invokeAndWait(() -> {
                frame = (JFrame) welcome.getOwner();
                frame.setBounds(80, 80, Integer.getInteger("gantry.capture.width", 1440),
                        Integer.getInteger("gantry.capture.height", 920));
                welcome.setLocationRelativeTo(frame);
                welcome.toFront();
            });
            capture(welcome, "first-run-guided-practice.png");
            click(welcome, "Close");

            var options = new SvgImportOptions(0, "default_station", .1, 140, 100, true, 0, 0, false, true);
            Path source = root.resolve("docs/samples/multi-colour-layers.svg");
            var artwork = SvgImportStage.importSvg(source.toFile(), options);
            if (artwork.layers().size() != 3) throw new IllegalStateException("Expected three gallery layers");
            var project = new GantryProject(GantryProject.CURRENT_VERSION, artwork,
                    IntStream.range(0, artwork.layers().size()).boxed().toList(),
                    GantryProject.Placement.identity(), 1,
                    new GantryProject.Source(source.toString(), options, null, List.of()), List.of(),
                    List.of(.6, .8, .6));
            PlotterPanel panel = (PlotterPanel) frame.getContentPane();
            SwingUtilities.invokeAndWait(() -> invoke(panel, "openProject", new Class<?>[]{GantryProject.class}, project));
            SwingUtilities.invokeAndWait(() -> { frame.toFront(); frame.requestFocus(); });
            capture(frame, "workspace-layer-preview.png");

            SwingUtilities.invokeLater(() -> invoke(panel, "onEditLayerSettings", new Class<?>[]{int.class}, 0));
            JDialog layer = dialog("Watercolor settings");
            capture(layer, "layer-watercolor-settings.png");
            click(layer, "Cancel");

            SwingUtilities.invokeLater(() -> invoke(panel, "onOpenSettings", new Class<?>[0]));
            JDialog settings = dialog("Settings");
            SwingUtilities.invokeAndWait(() -> {
                JTabbedPane tabs = find(settings, JTabbedPane.class);
                tabs.setSelectedIndex(tabs.indexOfTab("Geometry"));
                settings.pack();
                settings.setLocationRelativeTo(frame);
            });
            capture(settings, "preferences-geometry.png");
            click(settings, "Cancel");
            System.out.println("Captured four actual Gantry windows; no plotter connection was made.");
        } finally {
            SwingUtilities.invokeAndWait(() -> { for (Window window : Window.getWindows()) window.dispose(); });
        }
        System.exit(0);
    }

    private static void invoke(Object target, String name, Class<?>[] types, Object... values) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(target, values);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private static JDialog dialog(String prefix) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            JDialog[] found = {null};
            SwingUtilities.invokeAndWait(() -> {
                for (Window window : Window.getWindows()) {
                    if (window instanceof JDialog dialog && dialog.isShowing() && dialog.getTitle().startsWith(prefix)) found[0] = dialog;
                }
            });
            if (found[0] != null) return found[0];
            Thread.sleep(100);
        }
        throw new IllegalStateException("Dialog did not open: " + prefix);
    }

    private static void capture(Window window, String name) throws Exception {
        SwingUtilities.invokeAndWait(window::toFront);
        robot.waitForIdle();
        Thread.sleep(700);
        java.awt.image.BufferedImage[] rendered = {null};
        SwingUtilities.invokeAndWait(() -> {
            JRootPane content = ((RootPaneContainer) window).getRootPane();
            rendered[0] = new java.awt.image.BufferedImage(content.getWidth(), content.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rendered[0].createGraphics();
            try { content.printAll(graphics); } finally { graphics.dispose(); }
        });
        ImageIO.write(rendered[0], "png", output.resolve(name).toFile());
        System.out.println(name + " " + rendered[0].getWidth() + "x" + rendered[0].getHeight());
    }

    private static void click(Container container, String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton button = button(container, text);
            if (button == null) throw new IllegalStateException("Missing button: " + text);
            button.doClick();
        });
    }

    private static JButton button(Container parent, String text) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) return button;
            if (component instanceof Container child) {
                JButton found = button(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T find(Container parent, Class<T> type) {
        for (Component component : parent.getComponents()) {
            if (type.isInstance(component)) return type.cast(component);
            if (component instanceof Container child) {
                T found = find(child, type);
                if (found != null) return found;
            }
        }
        return null;
    }
}
