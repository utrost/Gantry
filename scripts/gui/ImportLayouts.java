import org.trostheide.gantry.app.GantryApp;
import org.trostheide.gantry.app.gui.PlotterPanel;
import org.trostheide.gantry.app.plot.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Captures actual import previews; every action uses an isolated, disconnected mock profile. */
public class ImportLayouts extends GuiAcceptance {
    public static void main(String[] args) {
        try {
            Path root=Path.of(args[0]), out=Path.of(args[1]); Files.createDirectories(out);
            profile=Files.createTempDirectory("gantry-import-layout-");
            System.setProperty("gantry.config.file",profile.resolve("config.json").toString());
            GantryConfig config=new GantryConfig();config.mock=true;config.showWelcomeOnStartup=false;
            config.gcode.machineWidth=210;config.gcode.machineHeight=148;config.uiTheme="Light";
            ConfigStore.save(config,profile.resolve("config.json").toFile());GantryApp.main(new String[0]);
            await(()->{for(Window w:Window.getWindows())if(w instanceof JFrame f&&f.isShowing()){frame=f;panel=(PlotterPanel)f.getContentPane();return true;}return false;});
            menu("Open SVG or Vector Drawing...");choose(root.resolve("docs/samples/multi-colour-layers.svg"));
            JDialog svg=dialog("Add SVG or vector drawing");await(()->findButton(svg,"Import").isEnabled());capture(svg,out.resolve("svg-import.png"));click(svg,"Cancel");
            BufferedImage photo=new BufferedImage(200,200,BufferedImage.TYPE_INT_RGB);Graphics2D g=photo.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,200,200);g.setColor(Color.BLACK);g.setStroke(new BasicStroke(6));g.drawOval(35,35,130,130);g.dispose();
            Path source=profile.resolve("trace.png");ImageIO.write(photo,"png",source.toFile());
            menu("Add image or photo...");choose(source);JDialog raster=dialog("Add image — preview and placement");
            try { await(()->findButton(raster,"Import")!=null&&findButton(raster,"Import").isEnabled()); }
            catch(AssertionError failure){
                capture(raster,out.resolve("raster-failure.png"));
                System.out.println("Trace: "+edt(()->((JLabel)field(raster,"readout")).getText()));
                Object options=field(raster,"importOptions"), preview=field(options,"preview");
                System.out.println("Physical preview: "+edt(()->((JTextArea)field(preview,"status")).getText()));throw failure;
            }
            capture(raster,out.resolve("raster-import.png"));
            var preview=(org.trostheide.gantry.app.gui.VisualizationPanel)field(field(field(raster,"importOptions"),"preview"),"canvas");
            double[] expectedMin=edt(preview::getContentMotorMin), expectedSize=edt(preview::getContentMotorSize);
            click(raster,"Import");await(()->project().output()!=null);
            var actual=(org.trostheide.gantry.app.gui.VisualizationPanel)field(panel,"visPanel");
            double[] actualMin=edt(actual::getContentMotorMin),actualSize=edt(actual::getContentMotorSize);
            for(int i=0;i<2;i++){check(Math.abs(expectedMin[i]-actualMin[i])<.01,"preview position matches imported artwork");check(Math.abs(expectedSize[i]-actualSize[i])<.01,"preview dimensions match imported artwork");}
            check(!jobs().isConnected(),"import never connects");
            System.out.println("PASS SVG physical preview and accepted raster trace import; disconnected throughout");System.exit(0);
        }catch(Throwable e){e.printStackTrace();System.exit(1);}
    }
    static void capture(Window window,Path path)throws Exception {
        edt(()->{BufferedImage image=new BufferedImage(window.getWidth(),window.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();window.paint(g);g.dispose();ImageIO.write(image,"png",path.toFile());return null;});
    }
}
