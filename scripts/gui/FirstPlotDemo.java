import org.trostheide.gantry.app.GantryApp;
import org.trostheide.gantry.app.gui.PlotterPanel;
import org.trostheide.gantry.app.plot.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Six real UI captures for a one-minute mock first-plot walkthrough; no hardware or desktop capture. */
public final class FirstPlotDemo extends GuiAcceptance {
    static Path output;
    public static void main(String[] args) {
        try {
            Path root = Path.of(args[0]).toAbsolutePath();
            profile = Path.of(args[1]).toAbsolutePath(); output = Path.of(args[2]).toAbsolutePath();
            Files.createDirectories(profile); Files.createDirectories(output);
            if (Files.exists(profile.resolve("config.json"))) throw new IllegalStateException("Use an empty demo profile");
            System.setProperty("gantry.config.file", profile.resolve("config.json").toString());
            GantryConfig config = new GantryConfig();
            config.mock = true; config.showWelcomeOnStartup = false; config.preflightBeforeStart = false;
            config.gcode.machineWidth = 210; config.gcode.machineHeight = 148;
            config.gcode.feedRateDraw = 30000; config.gcode.feedRateTravel = 30000;
            ConfigStore.save(config, profile.resolve("config.json").toFile());
            GantryApp.main(new String[0]);
            await(() -> { for (Window w : Window.getWindows()) if (w instanceof JFrame f && f.isShowing()) { frame=f; panel=(PlotterPanel)f.getContentPane(); return true; } return false; });
            edt(()->{frame.setSize(1280,800);return null;});
            capture(1, "1. Open Gantry. This walkthrough uses the mock plotter; no hardware moves.");
            menu("Open SVG or Vector Drawing..."); choose(root.resolve("docs/samples/multi-colour-layers.svg"));
            click(dialog("Add SVG or vector drawing"),"Import");
            await(()->project().output()!=null && project().output().layers().size()==3);
            capture(2, "2. Import SVG artwork. Review its size and position inside the machine bed.");
            capture(3, "3. Review the colour layers and plotting order before starting.");
            click(panel,"Connect"); await(()->jobs().isConnected());
            check(jobs().backend().getClass().getSimpleName().toLowerCase().contains("mock"),"mock only");
            click(panel,"Check before plotting...");
            JDialog wizard = dialog("Pre-Plot Checklist");
            click(wizard,"Next >"); edt(()->null);
            click(wizard,"Next >"); edt(()->null);
            click(wizard,"Next >"); edt(()->null);
            capture(4, "4. Review the pre-plot checklist. These physical checks are simulated here.");
            edt(()->{confirmMockChecks(wizard);return null;});
            click(wizard,"Next >"); edt(()->null); click(wizard,"Finish");
            await(()->(boolean)field(panel,"awaitingLayerConfirmation"));
            capture(5, "5. Confirm each pen when prompted. Stop remains available during the job.");
            for (int layer=0;layer<3;layer++) {
                await(()->(boolean)field(panel,"awaitingLayerConfirmation"));
                click(panel,"Pen ready — continue"); Thread.sleep(150);
            }
            await(()->!jobs().isPlotting());
            await(()->((PlotJobHistory)field(panel,"plotHistory")).jobs().size()==1);
            capture(6, "6. Mock plot complete. Save your project, or export G-code for later use.");
            System.out.println("Mock first-plot demo captures complete: " + output);
            System.exit(0);
        } catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    static void confirmMockChecks(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JCheckBox box && !box.isSelected()) box.doClick();
            if (child instanceof Container nested) confirmMockChecks(nested);
        }
    }
    static void capture(int step, String caption) throws Exception {
        BufferedImage image = edt(()->{
            BufferedImage result = new BufferedImage(1280,880,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=result.createGraphics();
            frame.paintAll(g);
            for (Window window : frame.getOwnedWindows()) {
                if (window instanceof JDialog && window.isShowing()) {
                    Graphics2D overlay = (Graphics2D) g.create((1280-window.getWidth())/2, (800-window.getHeight())/2,
                            window.getWidth(), window.getHeight());
                    window.paintAll(overlay); overlay.dispose();
                }
            }
            g.setColor(new Color(22,28,38));g.fillRect(0,800,1280,80);
            g.setColor(Color.WHITE);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,22));g.drawString(caption,24,834);
            g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,16));g.drawString("Gantry • simulated first plot • no physical hardware",24,865);
            g.dispose();return result;
        });
        ImageIO.write(image,"png",output.resolve(String.format("step-%02d.png",step)).toFile());
    }
}
