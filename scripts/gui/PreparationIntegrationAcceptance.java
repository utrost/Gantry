import org.trostheide.gantry.app.GantryApp;
import org.trostheide.gantry.app.gui.PlotterPanel;
import org.trostheide.gantry.app.plot.*;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

/** Integrated preparation actions through Swing; isolated mock profile, no hardware. */
public class PreparationIntegrationAcceptance extends GuiAcceptance {
    public static void main(String[] args) {
        try {
            Path results = Path.of(args[0]).toAbsolutePath();
            Files.createDirectories(results);
            profile = Files.createTempDirectory(results, "profile-");
            System.setProperty("gantry.config.file", profile.resolve("config.json").toString());
            GantryConfig config = new GantryConfig();
            boolean legacy = args.length > 1 && args[1].equals("legacy");
            if (legacy) config.coordinateVersion = 1;
            config.mock = true;
            config.showWelcomeOnStartup = false;
            config.gcode.machineWidth = 210;
            config.gcode.machineHeight = 148;
            ConfigStore.save(config, profile.resolve("config.json").toFile());
            if (legacy) {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var old = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(profile.resolve("config.json").toFile());
                old.remove("coordinateVersion");
                mapper.writeValue(profile.resolve("config.json").toFile(), old);
            }
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> { e.printStackTrace(); System.exit(1); });
            GantryApp.main(new String[0]);
            await(() -> {
                for (Window window : Window.getWindows()) {
                    if (window instanceof JFrame f && f.isShowing()) {
                        frame = f; panel = (PlotterPanel) f.getContentPane(); return true;
                    }
                }
                return false;
            });
            click(panel, "Open / Add ▾");
            JMenuItem[] labelAction = {null};
            await(() -> {
                for (MenuElement element : MenuSelectionManager.defaultManager().getSelectedPath()) {
                    JMenuItem item = findMenu(element, "Add text label...");
                    if (item != null) { labelAction[0] = item; return true; }
                }
                return false;
            });
            SwingUtilities.invokeLater(labelAction[0]::doClick);
            JDialog create = dialog("Create quick artwork");
            edt(() -> {
                List<JTextField> fields = new ArrayList<>();
                textFields(create, fields);
                check(fields.size() == 3, "text, width and height inputs available");
                fields.get(0).setText("Max Mustermann");
                fields.get(2).setText("10");
                return null;
            });
            capture(create, results.resolve("quick-artwork-dialog.png"));
            click(create, "OK");
            await(() -> project().output() != null && project().artworks().size() == 1);
            check(edt(() -> project().artworks().get(0).quickArtwork().text()).equals("Max Mustermann"),
                    "editable label source retained");
            if (legacy) {
                var original = edt(() -> project().output());
                check(edt(() -> ((GantryConfig) field(panel, "config")).coordinateVersion) == 1,
                        "unversioned profile remains legacy");
                capture(frame, results.resolve("legacy-before.png"));
                changeOrientation("Upright artwork");
                click(dialog("Review artwork orientation change"), "Cancel");
                check(ConfigStore.load(profile.resolve("config.json").toFile()).coordinateVersion == 1,
                        "cancel preserves saved convention");
                changeOrientation("Upright artwork");
                JDialog confirm = dialog("Review artwork orientation change");
                capture(confirm, results.resolve("orientation-review.png"));
                click(confirm, "OK");
                await(() -> ((GantryConfig) field(panel, "config")).coordinateVersion == 2);
                check(ConfigStore.load(profile.resolve("config.json").toFile()).coordinateVersion == 2,
                        "explicit opt-in persists");
                check(edt(() -> project().output()) == original, "opt-in never rewrites document geometry");
                System.out.println("PASS unversioned profile preservation, cancelled migration and explicit upright opt-in");
            }
            menu("Undo");
            await(() -> project().output().layers().isEmpty());
            menu("Redo");
            await(() -> project().artworks().size() == 1);
            Path saved = results.resolve("label.gantry");
            menu("Save Project As..."); choose(saved); await(() -> Files.isRegularFile(saved));
            click(panel, "None");
            menu("Export Reviewed Job as SVG...");
            await(() -> hasLabel(panel, "Choose at least one artwork layer before exporting SVG."));
            check(!Files.exists(results.resolve("reviewed.svg")), "empty selection writes nothing");
            click(panel, "All");
            menu("Export Reviewed Job as SVG...");
            await(() -> {
                for (Window window : Window.getWindows()) {
                    if (window.isShowing()) {
                        JFileChooser chooser = findChooser(window);
                        if (chooser != null) { chooser.cancelSelection(); return true; }
                    }
                }
                return false;
            });
            check(!Files.exists(results.resolve("reviewed.svg")), "cancelled chooser writes nothing");
            Path svg = results.resolve("reviewed.svg");
            menu("Export Reviewed Job as SVG..."); choose(svg); await(() -> Files.isRegularFile(svg));
            await(() -> hasLabel(panel, "1 SVG file exported: " + svg.toAbsolutePath()));
            String text = Files.readString(svg);
            check(text.contains("data-gantry-pen-name="), "named pen retained in SVG metadata");
            check(!text.contains("data-gantry-pen-width-assumed"), "current width is known");
            Path zip = results.resolve("reviewed.zip");
            menu("Export Reviewed Job as SVG Package..."); choose(zip); await(() -> Files.isRegularFile(zip));
            await(() -> hasLabel(panel, "2 SVG files + README exported in package: " + zip.toAbsolutePath()));
            capture(frame, results.resolve("quick-artwork.png"));
            try (ZipFile packageFile = new ZipFile(zip.toFile())) {
                check(packageFile.getEntry("combined.svg") != null && packageFile.size() == 3,
                        "atomic package contains combined, layer and README");
            }
            SwingUtilities.invokeLater(() -> {
                try {
                    var action = PlotterPanel.class.getDeclaredMethod("showInkCoverage");
                    action.setAccessible(true); action.invoke(panel);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            JDialog coverage = dialog("Ink overlap");
            await(() -> hasAnalysis(coverage));
            capture(coverage, results.resolve("ink-overlap.png"));
            click(coverage, "Close");
            check(!edt(() -> jobs().isConnected()), "preparation never connects a machine");
            if (legacy) {
                click(panel, "Connect"); await(() -> jobs().isConnected());
                changeOrientation("Legacy (preserve existing plots)");
                await(() -> hasLabel(panel, "Disconnect the plotter before changing connection or artwork orientation settings. Settings were not changed."));
                check(ConfigStore.load(profile.resolve("config.json").toFile()).coordinateVersion == 2,
                        "connected profile convention cannot change");
                click(panel, "Disconnect"); await(() -> !jobs().isConnected());
                System.out.println("PASS connected orientation change blocked without persistence");
                menu("Setup Wizard...");
                JDialog wizard = dialog("Machine Setup");
                click(wizard, "Next >"); click(wizard, "Next >");
                edt(() -> { orientationCombo(wizard).setSelectedItem("Legacy (preserve existing plots)"); return null; });
                click(wizard, "Next >"); click(wizard, "Next >");
                click(wizard, "Finish");
                click(dialog("Review artwork orientation change"), "Cancel");
                await(() -> !wizard.isShowing());
                check(ConfigStore.load(profile.resolve("config.json").toFile()).coordinateVersion == 2,
                        "cancelled setup preserves saved convention");
                check(edt(() -> !jobs().isConnected() && !(boolean) field(panel, "connecting")),
                        "cancelled setup does not connect for calibration");
                check(edt(() -> {
                    for (Window window : Window.getWindows())
                        if (window instanceof JDialog && window.isShowing()) return false;
                    return true;
                }), "cancelled setup does not open calibration");
                System.out.println("PASS cancelled setup migration does not continue to calibration");
            }
            System.out.println("PASS editable label, Undo/Redo, save, SVG/ZIP counts, empty selection, cancellation and overlap UI (mock only)");
            System.exit(0);
        } catch (Throwable e) { e.printStackTrace(); System.exit(1); }
    }
    static void changeOrientation(String choice) throws Exception {
        menu("Preferences...");
        JDialog settings = dialog("Settings");
        edt(() -> {
            var combo = orientationCombo(settings);
            check(combo.isEnabled() != jobs().isConnected(), "orientation selector disabled while connected");
            combo.setSelectedItem(choice); return null;
        });
        click(settings, "OK");
    }
    static JComboBox<?> orientationCombo(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JComboBox<?> combo && "Artwork orientation".equals(combo.getAccessibleContext().getAccessibleName())) return combo;
            if (c instanceof Container child) { var found = orientationCombo(child); if (found != null) return found; }
        }
        return null;
    }
    static void capture(Window window, Path output) throws Exception {
        edt(() -> {
            Container content = window instanceof RootPaneContainer root
                    ? root.getRootPane() : window;
            var image = new java.awt.image.BufferedImage(content.getWidth(), content.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            content.paint(graphics);
            graphics.dispose();
            javax.imageio.ImageIO.write(image, "png", output.toFile());
            return null;
        });
    }
    static void textFields(Container root, List<JTextField> fields) {
        for (Component c : root.getComponents()) {
            if (c instanceof JTextField field) fields.add(field);
            else if (c instanceof Container nested) textFields(nested, fields);
        }
    }
    static boolean hasLabel(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel label && text.equals(label.getText())) return true;
            if (c instanceof Container nested && hasLabel(nested, text)) return true;
        }
        return false;
    }
    static boolean hasAnalysis(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel label && label.getText() != null && label.getText().contains("max ")) return true;
            if (c instanceof Container nested && hasAnalysis(nested)) return true;
        }
        return false;
    }
}
