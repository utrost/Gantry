import org.trostheide.gantry.app.GantryApp;
import org.trostheide.gantry.app.gui.PlotterPanel;
import org.trostheide.gantry.app.plot.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.lang.reflect.*;

/** Actual Swing reference states, rendered in logical pixels at each requested UI scale. */
public class WorkspaceLayouts extends GuiAcceptance {
 public static void main(String[] args) {
  try {
   Path root=Path.of(args[0]);Path out=Path.of(args[1]);Files.createDirectories(out);
   profile=Files.createTempDirectory("gantry-layout-");System.setProperty("gantry.config.file",profile.resolve("config.json").toString());
   GantryConfig c=new GantryConfig();c.mock=true;c.showWelcomeOnStartup=false;c.preflightBeforeStart=true;c.uiTheme=args[2];
   c.gcode.machineWidth=210;c.gcode.machineHeight=148;ConfigStore.save(c,profile.resolve("config.json").toFile());
   GantryApp.main(new String[0]);await(()->{for(Window w:Window.getWindows())if(w instanceof JFrame f&&f.isShowing()){frame=f;panel=(PlotterPanel)f.getContentPane();return true;}return false;});
   edt(()->{frame.setSize(1024,800);return null;});capture(out,"empty");
   menu("Open SVG or Vector Drawing...");choose(root.resolve("docs/samples/multi-colour-layers.svg"));click(dialog("Add SVG or vector drawing"),"Import");await(()->project().output()!=null);
   capture(out,"prepared");click(panel,"Connect");await(()->jobs().isConnected());click(panel,"Check before plotting");
   capture(out,"check");
   edt(()->{Container checks=(Container)field(panel,"workspaceChecks");for(Component child:checks.getComponents())if(child instanceof JCheckBox b)b.doClick();return null;});
   await(()->findButton(panel,"Start plotting")!=null);click(panel,"Start plotting");await(()->(boolean)field(panel,"awaitingLayerConfirmation"));capture(out,"pen-change");
   assertStop();click(panel,"Pen ready — continue");capture(out,"running");
   click(panel,"Stop");await(()->!jobs().isPlotting());capture(out,"stopped");
   edt(()->{Field outcome=PlotterPanel.class.getDeclaredField("outcome");outcome.setAccessible(true);outcome.set(panel,Enum.valueOf((Class)outcome.getType(),"FAILED"));Method refresh=PlotterPanel.class.getDeclaredMethod("refreshGuidance");refresh.setAccessible(true);refresh.invoke(panel);return null;});capture(out,"failure");
   System.out.println("PASS workspace reference layouts "+args[2]+" / scale "+System.getProperty("sun.java2d.uiScale"));System.exit(0);
  }catch(Throwable e){e.printStackTrace();System.exit(1);}
 }
 static void assertStop() throws Exception {
  edt(()->{JButton stop=findButton(panel,"Stop");check(stop!=null&&stop.isShowing()&&stop.isEnabled(),"Stop reachable");Rectangle bounds=SwingUtilities.convertRectangle(stop.getParent(),stop.getBounds(),panel);check(new Rectangle(panel.getSize()).contains(bounds),"Stop within workspace: "+bounds);return null;});
 }
 static void capture(Path out,String name)throws Exception {
  Thread.sleep(180);edt(()->{BufferedImage image=new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();frame.paint(g);g.dispose();ImageIO.write(image,"png",out.resolve(name+".png").toFile());return null;});
 }
}
