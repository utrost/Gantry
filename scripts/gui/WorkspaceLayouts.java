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
   GantryConfig c=new GantryConfig();c.mock=true;c.showWelcomeOnStartup=false;c.preflightBeforeStart=true;c.uiTheme=args[2];if(args.length>3)c.uiDensity=args[3];
   c.gcode.machineWidth=210;c.gcode.machineHeight=148;ConfigStore.save(c,profile.resolve("config.json").toFile());
   GantryApp.main(new String[0]);await(()->{for(Window w:Window.getWindows())if(w instanceof JFrame f&&f.isShowing()){frame=f;panel=(PlotterPanel)f.getContentPane();return true;}return false;});
   edt(()->{frame.setSize(1024,800);return null;});capture(out,"empty");
   click(panel,"Close");edt(()->{Object hint=field(panel,"artworkWorkspace");Method refresh=hint.getClass().getDeclaredMethod("setHasArtwork",boolean.class);refresh.setAccessible(true);refresh.invoke(hint,false);Method showing=hint.getClass().getDeclaredMethod("isShowingEmptyState");showing.setAccessible(true);check(!(boolean)showing.invoke(hint),"closed hint stays dismissed after refresh");return null;});capture(out,"empty-dismissed");
   menu("Open SVG or Vector Drawing...");choose(root.resolve("docs/samples/multi-colour-layers.svg"));click(dialog("Add SVG or vector drawing"),"Import");await(()->project().output()!=null);
   capture(out,"prepared");
   var beforeNavigation=edt(()->project().output());
   edt(()->{Object vis=field(panel,"visPanel");Object session=field(panel,"documentSession");java.util.List<?> artworks=(java.util.List<?>)session.getClass().getMethod("artworks").invoke(session);if(!artworks.isEmpty()){Object artwork=artworks.get(0);String id=(String)artwork.getClass().getMethod("id").invoke(artwork);Method select=vis.getClass().getDeclaredMethod("selectArtwork",String.class);select.setAccessible(true);select.invoke(vis,id);}return null;});capture(out,"selected-artwork");
   edt(()->{Object shell=field(panel,"workspace");((JToggleButton)field(shell,"machineToggle")).doClick();return null;});capture(out,"machine");assertLayerRows();
   edt(()->{check(!findButton(panel,"Home…").isEnabled(),"Home disabled when disconnected");check(!findButton(panel,"Frame…").isEnabled(),"Frame disabled when disconnected");return null;});
   edt(()->{Object vis=field(panel,"visPanel");Method select=vis.getClass().getDeclaredMethod("selectArtwork",String.class);select.setAccessible(true);select.invoke(vis,new Object[]{null});return null;});capture(out,"machine-whole-drawing");assertLayerRows();
   check(edt(()->project().output().equals(beforeNavigation)),"selection and machine navigation preserve prepared output");
   edt(()->{Object shell=field(panel,"workspace");((JToggleButton)field(shell,"machineToggle")).doClick();frame.setSize(1440,1000);return null;});capture(out,"large-workspace");edt(()->{frame.setSize(1024,800);return null;});
   click(panel,"Connect");await(()->jobs().isConnected());click(panel,"Check before plotting");
   capture(out,"check");
   edt(()->{Container checks=(Container)field(panel,"workspaceChecks");for(Component child:checks.getComponents())if(child instanceof JCheckBox b)b.doClick();return null;});
   await(()->findButton(panel,"Start plotting")!=null);click(panel,"Start plotting");await(()->(boolean)field(panel,"awaitingLayerConfirmation"));capture(out,"pen-change");
   assertStop();click(panel,"Pen ready — continue");capture(out,"running");
   click(panel,"Stop");await(()->!jobs().isPlotting());capture(out,"stopped");
   edt(()->{Field outcome=PlotterPanel.class.getDeclaredField("outcome");outcome.setAccessible(true);outcome.set(panel,Enum.valueOf((Class)outcome.getType(),"FAILED"));Method refresh=PlotterPanel.class.getDeclaredMethod("refreshGuidance");refresh.setAccessible(true);refresh.invoke(panel);return null;});capture(out,"failure");
   System.out.println("PASS workspace reference layouts "+args[2]+" / scale "+System.getProperty("sun.java2d.uiScale")+" / density "+c.uiDensity+" / device transform "+frame.getGraphicsConfiguration().getDefaultTransform());System.exit(0);
  }catch(Throwable e){e.printStackTrace();System.exit(1);}
 }
 static void assertLayerRows() throws Exception {
  edt(()->{
   if(frame.getHeight()<800){System.out.println("UNAVAILABLE minimum-workspace layer check: desktop limited frame to "+frame.getWidth()+"×"+frame.getHeight()+" logical pixels");return null;}
   JTable table=findLayers(panel);Rectangle visible=table.getVisibleRect();
   check(visible.contains(table.getCellRect(2,0,true)),"three layer rows visible with Machine open: "+visible);return null;});
 }
 static void assertStop() throws Exception {
  edt(()->{JButton stop=findButton(panel,"Stop");check(stop!=null&&stop.isShowing()&&stop.isEnabled(),"Stop reachable");Rectangle bounds=SwingUtilities.convertRectangle(stop.getParent(),stop.getBounds(),panel);check(new Rectangle(panel.getSize()).contains(bounds),"Stop within workspace: "+bounds);return null;});
 }
 static void capture(Path out,String name)throws Exception {
  Thread.sleep(180);edt(()->{BufferedImage image=new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();frame.paint(g);g.dispose();ImageIO.write(image,"png",out.resolve(name+".png").toFile());return null;});
 }
}
