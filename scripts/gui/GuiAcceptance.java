import org.trostheide.gantry.app.GantryApp;
import org.trostheide.gantry.app.gui.PlotterPanel;
import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.app.session.GantryProject;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.concurrent.Callable;

/** Real Swing actions and file dialogs, using only an isolated mock profile. */
public class GuiAcceptance {
    static JFrame frame;
    static PlotterPanel panel;
    static Path profile;
    public static void main(String[] args) {
        try {
            Path root = Path.of(args[0]).toAbsolutePath();
            profile = Path.of(args[1]).toAbsolutePath();
            Files.createDirectories(profile);
            System.setProperty("gantry.config.file", profile.resolve("config.json").toString());
            if (args[2].equals("seed")) {
                if (Files.exists(profile.resolve("config.json"))) throw new IllegalStateException("Use an empty profile");
                GantryConfig config = new GantryConfig();
                config.mock = true; config.showWelcomeOnStartup = false; config.preflightBeforeStart = false;
                config.gcode.machineWidth = 210; config.gcode.machineHeight = 148;
                config.gcode.feedRateDraw = 30000; config.gcode.feedRateTravel = 30000;
                ConfigStore.save(config, profile.resolve("config.json").toFile());
            }
            Thread.setDefaultUncaughtExceptionHandler((t,e)->{ e.printStackTrace(); System.exit(1); });
            GantryApp.main(new String[0]);
            await(() -> { for (Window w : Window.getWindows()) if (w instanceof JFrame f && f.isShowing()) { frame=f; panel=(PlotterPanel)f.getContentPane(); return true; } return false; });
            if (args[2].equals("recover")) {
                JDialog recovery = dialog("Recovery available");
                click(recovery,"Yes");
                await(() -> project().output()!=null && frame.getTitle().contains("Unsaved"));
                check(edt(()->project().selectedLayers().size())==2,"recovery restores unsaved layer selection");
                check(!edt(()->jobs().isConnected()),"recovery starts disconnected");
                check(edt(()->frame.getTitle().contains("Unsaved")),"recovered document stays dirty");
                System.out.println("PASS recovery after process restart");
                System.exit(0);
            }
            edt(()->{frame.setSize(1024,768);return null;});
            menu("Open SVG or Vector Drawing..."); choose(root.resolve("docs/samples/multi-colour-layers.svg"));
            click(dialog("Add SVG or vector drawing"),"Import");
            await(()->project().output()!=null && project().output().layers().size()==3);
            System.out.println("PASS real SVG chooser/import dialog");
            // Every replacement entry point must preserve the dirty session on Cancel.
            GantryProject beforeReplacement = edt(()->project());
            for (String entry : new String[]{"Open Gantry Project...", "Open Commands (JSON)...",
                    "Open SVG or Vector Drawing...", "Add image or photo...", "Guided First Plot..."}) {
                menu(entry); click(dialog("Unsaved Gantry project"), "Cancel");
                check(edt(()->project().output().equals(beforeReplacement.output())), "cancel preserves output: "+entry);
                check(edt(()->frame.getTitle().contains("Unsaved")), "cancel preserves dirty state: "+entry);
            }
            System.out.println("PASS replacement guards for project, commands, SVG, image and practice");
            // Discard authorizes replacement but cancelling the import still preserves the drawing.
            menu("Open SVG or Vector Drawing..."); click(dialog("Unsaved Gantry project"), "Discard");
            choose(root.resolve("docs/samples/multi-colour-layers.svg"));
            click(dialog("Add SVG or vector drawing"),"Cancel");
            check(edt(()->project().output().layers().size())==3,"cancelled import preserves drawing");
            click(panel,"None"); check(edt(()->project().selectedLayers().isEmpty()),"none selects no layers");
            click(panel,"All"); check(edt(()->project().selectedLayers().size())==3,"all selects layers");
            click(panel,"Connect"); await(()->jobs().isConnected());
            check(edt(()->jobs().backend().getClass().getSimpleName().toLowerCase().contains("mock")),"only mock backend connected");
            click(panel,"Start plotting");
            for(int layer=0;layer<3;layer++) {
                await(()->(boolean)field(panel,"awaitingLayerConfirmation"));
                click(panel,"Pen ready — continue");
                Thread.sleep(150);
            }
            await(()->!jobs().isPlotting());
            await(()->((PlotJobHistory)field(panel,"plotHistory")).jobs().size()==1);
            System.out.println("PASS mock plot through GUI with three pen confirmations");
            click(panel,"Start plotting"); await(()->(boolean)field(panel,"awaitingLayerConfirmation"));
            click(panel,"Stop"); await(()->!jobs().isPlotting());
            check(edt(()->((PlotJobHistory)field(panel,"plotHistory")).jobs().size())==1,"cancelled plot not added to history");
            System.out.println("PASS Stop while awaiting pen confirmation");
            menu("Export G-code (for plotter)..."); choose(profile.resolve("gui-export.gcode"));
            await(()->Files.exists(profile.resolve("gui-export.gcode")));
            check(Files.readString(profile.resolve("gui-export.gcode")).contains("G1"),"export contains movement");
            click(panel,"None");
            edt(()->{findLayer(panel).doClick();return null;});
            menu("Save Project..."); choose(profile.resolve("gui-project.gantry"));
            await(()->Files.exists(profile.resolve("gui-project.gantry")));
            menu("Open Gantry Project..."); choose(profile.resolve("gui-project.gantry"));
            await(()->!frame.getTitle().contains("Unsaved"));
            check(edt(()->project().selectedLayers().size())==1,"reopen restores selected layers");
            System.out.println("PASS G-code export and project save/reopen via file dialogs");
            click(panel,"All");
            // An unsaved layer change must survive an abrupt process exit.
            edt(()-> { JCheckBox box=findLayer(panel); if(box==null)throw new IllegalStateException("No layer checkbox");box.doClick();return null;});
            await(()->Files.exists(profile.resolve(".gantry-recovery")) &&
                    org.trostheide.gantry.app.session.GantryProjectIO.load(profile.resolve(".gantry-recovery").toFile()).selectedLayers().size()==2);
            check(edt(()->project().selectedLayers().size())==2,"layer selection change applied");
            System.out.println("PASS recovery autosave; exiting without clean-close cleanup");
            System.exit(0);
        } catch(Throwable e) { e.printStackTrace(); System.exit(1); }
    }
    static PlotJobController jobs() throws Exception { return (PlotJobController)field(panel,"plotJobController"); }
    static GantryProject project() throws Exception {
        Method method=PlotterPanel.class.getDeclaredMethod("captureProject");method.setAccessible(true);return (GantryProject)method.invoke(panel);
    }
    static Object field(Object object,String name) throws Exception { Field f=object.getClass().getDeclaredField(name); f.setAccessible(true);return f.get(object); }
    static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    static <T>T edt(Callable<T> action) throws Exception {
        java.util.concurrent.FutureTask<T> task=new java.util.concurrent.FutureTask<>(action);SwingUtilities.invokeAndWait(task);return task.get();
    }
    static void await(Callable<Boolean> condition) throws Exception {
        for(int i=0;i<300;i++){if(edt(condition))return;Thread.sleep(100);}throw new AssertionError("Timed out waiting for GUI state");
    }
    static JDialog dialog(String title) throws Exception {
        JDialog[] found={null};await(()->{for(Window w:Window.getWindows())if(w instanceof JDialog d && d.isShowing() && d.getTitle().startsWith(title)){found[0]=d;return true;}return false;});return found[0];
    }
    static void menu(String text) throws Exception {
        JMenuItem item=edt(()->findMenu(frame.getJMenuBar(),text));check(item!=null,"menu "+text);SwingUtilities.invokeLater(item::doClick);
    }
    static JMenuItem findMenu(MenuElement root,String text) {
        if(root instanceof JMenuItem item && item.getText().equals(text))return item;
        for(MenuElement child:root.getSubElements()){JMenuItem result=findMenu(child,text);if(result!=null)return result;}return null;
    }
    static void choose(Path path) throws Exception {
        JFileChooser[] chooser={null};await(()->{for(Window w:Window.getWindows())if(w.isShowing()){JFileChooser c=findChooser(w);if(c!=null){chooser[0]=c;return true;}}return false;});
        edt(()->{chooser[0].setSelectedFile(path.toFile());chooser[0].approveSelection();return null;});
    }
    static JFileChooser findChooser(Container root){for(Component c:root.getComponents()){if(c instanceof JFileChooser f)return f;if(c instanceof Container n){JFileChooser f=findChooser(n);if(f!=null)return f;}}return null;}
    static void click(Container root,String text) throws Exception {
        JButton button=edt(()->findButton(root,text));check(button!=null,"button "+text);check(edt(button::isEnabled),"enabled "+text);SwingUtilities.invokeLater(button::doClick);
    }
    static JButton findButton(Container root,String text){for(Component c:root.getComponents()){if(c instanceof JButton b && b.getText().equals(text))return b;if(c instanceof Container n){JButton b=findButton(n,text);if(b!=null)return b;}}return null;}
    static JCheckBox findLayer(Container root){for(Component c:root.getComponents()){if(c instanceof JCheckBox b && b.getText().contains(" — "))return b;if(c instanceof Container n){JCheckBox b=findLayer(n);if(b!=null)return b;}}return null;}
}
