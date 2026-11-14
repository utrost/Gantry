package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.GantryConfig;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.Callable;

/** Rendered button states, including runtime theme changes. Uses no profile or machine. */
public final class ButtonStateAcceptance {
    private static final String[] STATES = {"Normal", "Hover", "Pressed", "Focused"};
    public static void main(String[] args) throws Exception {
        Path evidence = Path.of(args[0]); Files.createDirectories(evidence);
        JFrame[] frame = new JFrame[1];
        JPanel[] panel = new JPanel[1];
        java.util.ArrayList<AbstractButton> buttons = new java.util.ArrayList<>();
        edt(() -> {
            GantryConfig config = new GantryConfig(); config.uiTheme = "Dark";
            WorkspaceStyle.install(config);
            frame[0] = new JFrame("Gantry button-state acceptance");
            panel[0] = new JPanel(new GridLayout(0, 5, 12, 12));
            panel[0].setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            for (String kind : List.of("Default", "Primary", "Motion", "Stop", "Toggle")) {
                panel[0].add(new JLabel(kind));
                for (String state : STATES) {
                    AbstractButton button = kind.equals("Toggle") ? new JToggleButton("Readable label", true) : new JButton("Readable label");
                    button.setPreferredSize(new Dimension(180, 44));
                    if (kind.equals("Primary")) WorkspaceStyle.primary((JButton) button);
                    if (kind.equals("Motion")) WorkspaceStyle.motion((JButton) button);
                    if (kind.equals("Stop")) WorkspaceStyle.action(button, WorkspaceStyle.ActionRole.STOP, true);
                    button.putClientProperty("acceptance.kind", kind);
                    button.putClientProperty("acceptance.state", state);
                    button.setToolTipText(kind + " · " + state);
                    panel[0].add(button); buttons.add(button);
                }
            }
            frame[0].setContentPane(panel[0]); frame[0].pack(); frame[0].setVisible(true);
            return null;
        });
        try {
            for (String theme : List.of("Dark", "Light", "Dark")) {
                edt(() -> {
                    GantryConfig config = new GantryConfig(); config.uiTheme = theme;
                    WorkspaceStyle.install(config); SwingUtilities.updateComponentTreeUI(frame[0]);
                    frame[0].pack(); frame[0].toFront(); return null;
                });
                for (AbstractButton button : buttons) {
                    String kind = (String) button.getClientProperty("acceptance.kind");
                    String state = (String) button.getClientProperty("acceptance.state");
                    edt(() -> {
                        frame[0].getRootPane().setDefaultButton(kind.equals("Default") ? (JButton) button : null);
                        buttons.forEach(b -> { b.getModel().setArmed(false); b.getModel().setPressed(false); b.getModel().setRollover(false); });
                        if (state.equals("Hover")) button.getModel().setRollover(true);
                        if (state.equals("Pressed")) { button.getModel().setArmed(true); button.getModel().setPressed(true); }
                        if (state.equals("Focused")) button.requestFocusInWindow();
                        else panel[0].requestFocusInWindow();
                        return null;
                    });
                    if (state.equals("Focused")) {
                        for (int i = 0; i < 30 && !edt(button::isFocusOwner); i++) Thread.sleep(30);
                        if (!edt(button::isFocusOwner)) throw new AssertionError("Native focus unavailable for " + kind);
                    }
                    // Render the actual UI delegate and measure solid glyph pixels against its fill.
                    BufferedImage image = edt(() -> {
                        BufferedImage result = new BufferedImage(button.getWidth(), button.getHeight(), BufferedImage.TYPE_INT_RGB);
                        Graphics2D graphics = result.createGraphics(); button.paint(graphics); graphics.dispose(); return result;
                    });
                    javax.imageio.ImageIO.write(image, "png", evidence.resolve(theme.toLowerCase() + "-" + kind.toLowerCase() + "-" + state.toLowerCase() + ".png").toFile());
                    double contrast = renderedContrast(image);
                    if (contrast < 4.5) throw new AssertionError(theme + " " + kind + " " + state + ": " + contrast);
                    System.out.printf(java.util.Locale.ROOT, "PASS rendered %s %s %s contrast %.2f:1%n", theme, kind, state, contrast);
                    edt(() -> { button.getModel().setArmed(false); button.getModel().setPressed(false); return null; });
                }
                edt(() -> {
                    BufferedImage image = new BufferedImage(panel[0].getWidth(), panel[0].getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = image.createGraphics(); panel[0].paint(graphics); graphics.dispose();
                    javax.imageio.ImageIO.write(image, "png", evidence.resolve(theme.toLowerCase() + "-buttons.png").toFile());
                    return null;
                });
            }
        } finally { edt(() -> { frame[0].dispose(); return null; }); }
        System.out.println("PASS native button-state rendering and Dark/Light/Dark runtime switching");
        System.exit(0);
    }
    private static double renderedContrast(BufferedImage image) {
        Color background = new Color(image.getRGB(image.getWidth() / 2, 8));
        double maximum = 1;
        // Exclude rounded edges/focus rings. A fully painted glyph pixel must reach 4.5:1.
        for (int y = image.getHeight() / 2 - 9; y < image.getHeight() / 2 + 9; y++)
            for (int x = image.getWidth() / 2 - 45; x < image.getWidth() / 2 + 45; x++) {
                double a = luminance(background), b = luminance(new Color(image.getRGB(x, y)));
                maximum = Math.max(maximum, (Math.max(a,b)+.05)/(Math.min(a,b)+.05));
            }
        return maximum;
    }
    private static double luminance(Color c) { return .2126*linear(c.getRed())+.7152*linear(c.getGreen())+.0722*linear(c.getBlue()); }
    private static double linear(int v) { double s=v/255.; return s<=.04045?s/12.92:Math.pow((s+.055)/1.055,2.4); }
    private static <T> T edt(Callable<T> action) throws Exception {
        var task = new java.util.concurrent.FutureTask<T>(action); SwingUtilities.invokeAndWait(task); return task.get();
    }
}
