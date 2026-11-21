import org.trostheide.gantry.app.GantryApp;
import org.trostheide.gantry.app.gui.PlotterPanel;
import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.app.session.*;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.command.*;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;

/** Focused acceptance of historical A/current B, readiness and persistent feedback. Mock only. */
public final class WorkflowIssueAcceptance extends GuiAcceptance {
    public static void main(String[] args) {
        try {
            Path evidence = Path.of(args[0]); Files.createDirectories(evidence);
            profile = Files.createTempDirectory("gantry-issue-review-");
            System.setProperty("gantry.config.file", profile.resolve("config.json").toString());
            GantryConfig config = new GantryConfig();
            config.coordinateVersion=1; // This fixture replays an established calibrated profile.
            config.mock=true; config.showWelcomeOnStartup=false; config.preflightBeforeStart=true;
            config.gcode.machineWidth=210; config.gcode.machineHeight=148;
            config.machineOrigin="Top-Left"; config.canvasAlignment=null;
            config.gcode.feedRateTravel=30000; config.gcode.feedRateDraw=30000;
            ConfigStore.save(config, profile.resolve("config.json").toFile());
            GantryApp.main(new String[0]);
            await(() -> { for (Window w : Window.getWindows()) if (w instanceof JFrame f && f.isShowing()) { frame=f; panel=(PlotterPanel)f.getContentPane(); return true; } return false; });
            edt(() -> { frame.setSize(1024,800); return null; });
            click(panel,"Connect"); await(() -> jobs().isConnected() && !(boolean)field(panel,"connecting"));
            edt(() -> {
                for(Object value:(List<?>)field(panel,"frameControls")) {
                    JButton button=(JButton)value;
                    check(button.isVisible()&&!button.isEnabled(),"Frame stays visible but disabled without artwork");
                    check(button.getToolTipText().contains("Load artwork"),"Frame explains missing artwork");
                }
                for(Object value:(List<?>)field(field(panel,"workspaceChecks"),"motionControls")) {
                    JButton button=(JButton)value;
                    check(button.isVisible()&&!button.isEnabled(),"Plot-check actions stay visible but disabled without artwork");
                    check(button.getToolTipText().contains("Load artwork"),"Plot-check action explains missing artwork");
                }
                check(com.formdev.flatlaf.FlatLaf.isLafDark(),"default theme darkens the interface, independently of canvas");
                return null;
            });
            ProcessorOutput historical = drawing("Historical pen A",20,10);
            ProcessorOutput current = drawing("Current drawing B",300,160);
            PlotSettings stored = config.toPlotSettings(); stored.alignmentOffsetOverride=new double[]{30,40};
            PlotJobHistory.Job job = new PlotJobHistory.Job(Instant.parse("2026-01-01T12:00:00Z"),historical,stored);
            GantryProject document = new GantryProject(1,current,List.of(0),GantryProject.Placement.identity(),1,null);
            edt(() -> { invoke(panel,"openProject",GantryProject.class,document); return null; });
            GantryProject before = edt(GuiAcceptance::project);
            edt(() -> {
                for(Object value:(List<?>)field(panel,"frameControls"))check(((JButton)value).isEnabled(),"Frame becomes available with artwork on an idle connection");
                for(Object value:(List<?>)field(field(panel,"workspaceChecks"),"motionControls"))
                    check(((JButton)value).isEnabled(),"Plot-check actions recover with artwork on an idle connection");
                return null;
            });
            GantryConfig changeBackend=ConfigStore.load(profile.resolve("config.json").toFile());changeBackend.mock=false;
            byte[] connectedConfig=Files.readAllBytes(profile.resolve("config.json"));
            edt(()->{
                invoke(panel,"saveSettings",GantryConfig.class,changeBackend);
                check(((GantryConfig)field(panel,"config")).mock,"connected backend preference cannot change");
                check((boolean)invoke(panel,"isSimulation"),"connected status identifies the actual mock backend");
                check(!findMenu(frame.getJMenuBar(),"Setup Wizard...").isEnabled(),"setup remains visible but disabled while connected");
                invoke(field(panel,"localFeedback"),"dismiss");return null;
            });
            check(java.util.Arrays.equals(connectedConfig,Files.readAllBytes(profile.resolve("config.json"))),"rejected backend change is not persisted");
            System.out.println("PASS connected backend preferences cannot change machine identity or persisted configuration");
            edt(() -> { invoke(panel,"reviewHistoricalJob",PlotJobHistory.Job.class,job); return null; });
            double expected = TimeEstimator.estimate(historical,config.gcode,stored.stations).totalSeconds();
            edt(() -> {
                check(Math.abs(((PlotProgressState)field(panel,"plotProgress")).estimate().totalSeconds()-expected)<.0001,"historical estimate belongs to A");
                check(((String)field(panel,"boundsWarning")).isEmpty(),"B's overflow must not appear in A's review");
                check(project().equals(before),"review preserves B and its selection");
                Object shell=field(panel,"workspace");
                check(((JSplitPane)field(shell,"split")).getLeftComponent().getClass().getSimpleName().equals("HistoricalJobPreview"),"A has an explicit read-only canvas");
                return null;
            });
            capture(evidence,"historical-review");
            click(panel,"Frame artwork — pen up");
            await(() -> jobs().isMachineBusy()); await(() -> !jobs().isMachineBusy());
            await(() -> ((int)field(panel,"manualActions"))==0);
            double[] frameBounds = new PlotService(jobs().backend(),stored).computeFrameBounds(historical);
            double[] position = jobs().backend().queryPosition();
            check(Math.abs(position[0]-frameBounds[0])<.001 && Math.abs(position[1]-frameBounds[2])<.001,"Frame returns to A's stored origin");
            confirmPhysicalChecks();
            click(panel,"Start plotting"); await(() -> (boolean)field(panel,"awaitingLayerConfirmation"));
            capture(evidence,"historical-pen-change");
            edt(() -> {
                check(((String)field(panel,"boundsWarning")).isEmpty(),"active A still excludes B's overflow");
                Object context=field(panel,"jobContext");
                check(((JTextArea)field(context,"pen")).getText().contains("Historical pen A"),"requested pen belongs to A");
                return null;
            });
            click(panel,"Pen ready — continue"); await(() -> !jobs().isPlotting());
            await(() -> ((PlotJobHistory)field(panel,"plotHistory")).latest()!=null);
            edt(() -> {
                check(((PlotJobHistory)field(panel,"plotHistory")).latest().output().equals(historical),"execution submitted stored A");
                check(project().equals(before),"executing A preserves B");
                check(((String)field(panel,"boundsWarning")).isEmpty(),"completed A still excludes B's overflow");
                return null;
            });
            click(panel,"Return to artwork");
            edt(() -> {
                check(project().equals(before),"return restores B unchanged");
                check(!((String)field(panel,"boundsWarning")).isEmpty(),"B overflow returns with B");
                double actual = ((PlotProgressState)field(panel,"plotProgress")).estimate().totalSeconds();
                double currentExpected = TimeEstimator.estimate((ProcessorOutput)invoke(panel,"preparePlotOutput"),((GantryConfig)field(panel,"config")).gcode,config.stations).totalSeconds();
                check(Math.abs(actual-currentExpected)<.0001,"return restores B estimate: " + actual + " vs " + currentExpected);
                return null;
            });
            System.out.println("PASS historical A/current B preview, estimate, frame, pen, execution and return preserve document identity");
            edt(()->{findLayers(panel).setRowSelectionInterval(0,0);return null;});
            JSpinner penWidth=edt(()->findSpinner(panel,"Pen width in millimetres"));
            check(penWidth!=null,"shared pen properties available");
            edt(()->{penWidth.setValue(.8);return null;});click(panel,"Apply pen settings");
            await(()->project().penWidthsMm().equals(List.of(.8)));
            check(edt(()->project().output().equals(before.output())),"width-only edit preserves commands and does not require a hidden refill station");
            click(panel,"Undo");await(()->project().penWidthsMm().equals(List.of(.3)));
            click(panel,"Redo");await(()->project().penWidthsMm().equals(List.of(.8)));
            click(panel,"Undo");await(()->project().penWidthsMm().equals(List.of(.3)));
            check(edt(()->project().selectedLayers().equals(before.selectedLayers())),"inspecting/editing a pen does not change inclusion");
            System.out.println("PASS shared pen-property edit .3 → .8 mm, one Undo, Redo and unchanged inclusion");
            // Changing a physical prerequisite must revoke readiness rather than merely hiding Check.
            click(panel,"Check before plotting"); confirmPhysicalChecks();
            edt(() -> { check((boolean)invoke(field(panel,"workspaceChecks"),"ready"),"confirmed checks ready"); invoke(panel,"onProjectSettingsChanged"); return null; });
            edt(() -> { check(!(boolean)invoke(field(panel,"workspaceChecks"),"ready"),"job-setting edit invalidates checks"); return null; });
            System.out.println("PASS readiness invalidated by job-setting edits");
            capture(evidence,"current-artwork-overflow");
            // Save As cancellation and an actual filesystem write failure must retain the edit and recovery.
            Path savedProject = profile.resolve("issue-project.gantry");
            menu("Save Project..."); choose(savedProject); await(()->Files.isRegularFile(savedProject));
            edt(()->{invoke(panel,"onProjectSettingsChanged");invoke(panel,"writeRecovery");return null;});
            Path recovery = profile.resolve(".gantry-recovery");
            byte[] recoveryBefore = Files.readAllBytes(recovery);
            menu("Save Project As...");
            await(()->{for(Window window:Window.getWindows())if(window.isShowing()){JFileChooser chooser=findChooser(window);if(chooser!=null){chooser.cancelSelection();return true;}}return false;});
            check(edt(()->((DocumentSession)field(panel,"documentSession")).isDirty()),"Save As cancellation preserves dirty state");
            Path savedBackup = profile.resolve("saved-backup.gantry");
            Files.move(savedProject,savedBackup);Files.createDirectory(savedProject);Files.writeString(savedProject.resolve("keep"),"existing content");
            menu("Save Project...");
            await(()->((String)invoke(field(panel,"localFeedback"),"messageText")).contains("Failed to save project"));
            check(edt(()->((DocumentSession)field(panel,"documentSession")).isDirty()),"failed save keeps dirty state");
            check(java.util.Arrays.equals(recoveryBefore,Files.readAllBytes(recovery)),"failed save preserves recovery");
            check(Files.readString(savedProject.resolve("keep")).equals("existing content"),"failed atomic write preserves destination");
            Files.delete(savedProject.resolve("keep"));Files.delete(savedProject);Files.move(savedBackup,savedProject);
            menu("Save Project...");await(()->!((DocumentSession)field(panel,"documentSession")).isDirty());
            edt(()->{Object feedback=field(panel,"localFeedback");check(((String)invoke(feedback,"messageText")).contains("Project saved"),"successful retry visible despite retained error");check(((JButton)invoke(feedback,"unresolvedErrorButton")).isVisible(),"earlier failure still accessible");return null;});
            capture(evidence,"saved-with-error-details");
            System.out.println("PASS Save As cancellation, failed atomic Save, retained recovery and visible successful retry");
            Path exported=profile.resolve("review-export.gcode");
            Files.writeString(exported,"previous export");
            menu("Export G-code (for plotter)...");choose(exported);
            JDialog overwrite=dialog("Confirm overwrite");
            Files.delete(exported);Files.createDirectory(exported);Files.writeString(exported.resolve("keep"),"preserve destination");
            click(overwrite,"Yes");
            await(()->((String)invoke(field(panel,"localFeedback"),"messageText")).contains("Failed to export"));
            check(Files.readString(exported.resolve("keep")).equals("preserve destination"),"failed export preserves destination");
            await(()->{try(var entries=Files.list(profile)){return entries.noneMatch(path->path.getFileName().toString().startsWith("review-export.gcode.")&&path.toString().endsWith(".tmp"));}});
            Files.delete(exported.resolve("keep"));Files.delete(exported);
            menu("Export G-code (for plotter)...");choose(exported);
            await(()->((String)invoke(field(panel,"localFeedback"),"messageText")).contains("G-code exported:"));
            check(Files.readString(exported).contains("G1"),"successful export has complete G-code before reporting success");
            check(edt(()->((JButton)invoke(field(panel,"localFeedback"),"unresolvedErrorButton")).isVisible()),"export success keeps earlier error accessible");
            System.out.println("PASS failed atomic export preserves destination and cleans temporary file; successful retry reports its destination");
            // Stage valid machine settings, then make only the isolated config destination unwritable as a file.
            click(panel,"Disconnect");await(()->!jobs().isConnected());
            Path machineFile=profile.resolve("incoming-machine.json");
            GantryConfig incoming=ConfigStore.load(profile.resolve("config.json").toFile());incoming.gcode.machineWidth=222;
            MachineSettingsIO.exportSettings(incoming,machineFile.toFile());
            Path configPath=profile.resolve("config.json"),configBackup=profile.resolve("saved-config.json");
            Files.move(configPath,configBackup);Files.createDirectory(configPath);Files.writeString(configPath.resolve("keep"),"existing config destination");
            menu("Import Machine Settings...");choose(machineFile);click(dialog("Import machine settings"),"Apply settings");
            click(dialog("Machine settings"),"OK");
            check(edt(()->((GantryConfig)field(panel,"config")).gcode.machineWidth)==210,"failed settings persistence leaves active geometry unchanged");
            check(!edt(()->jobs().isConnected()),"failed settings import does not connect");
            edt(()->{invoke(panel,"saveSettings",GantryConfig.class,incoming);return null;});
            check(edt(()->((GantryConfig)field(panel,"config")).gcode.machineWidth)==210,"failed preferences persistence leaves active geometry unchanged");
            check(edt(()->((String)invoke(field(panel,"localFeedback"),"messageText")).contains("Preferences could not be saved")),"preferences failure is visible outside diagnostics");
            edt(()->{
                try { invoke(panel,"saveSetupConfig",GantryConfig.class,incoming);throw new AssertionError("setup must report persistence failure"); }
                catch(java.lang.reflect.InvocationTargetException failure){check(failure.getCause() instanceof java.io.UncheckedIOException,"setup reports its write failure");}
                return null;
            });
            check(edt(()->((GantryConfig)field(panel,"config")).gcode.machineWidth)==210,"failed setup persistence leaves active geometry unchanged");
            Files.delete(configPath.resolve("keep"));Files.delete(configPath);Files.move(configBackup,configPath);
            System.out.println("PASS machine-settings persistence failure leaves active settings and connection unchanged");
            menu("Guided First Plot...");
            await(()->((GantryConfig)field(panel,"config")).mock && "Gantry practice drawing".equals(project().output().metadata().source()));
            check(!edt(()->jobs().isConnected()),"practice does not restore/connect hardware");
            check(edt(()->{for(Window window:Window.getWindows())if(window instanceof JDialog d && d.isShowing())return false;return true;}),"practice reaches artwork without serial/axis/actuator dialogs");
            System.out.println("PASS direct sample practice without setup decisions or automatic connection");
            System.out.println("PASS focused workflow issue acceptance (mock only)");
            System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static ProcessorOutput drawing(String name,double width,double height) {
        return new ProcessorOutput(new Metadata(name,Instant.EPOCH,null,"mm",2,new Bounds(0,0,width,height)),List.of(
                new Layer(name,null,"#2463a8",0,null,List.of(new MoveCommand(1,0,0),new DrawCommand(2,List.of(new Point(0,0),new Point(width,height)))))));
    }
    private static void confirmPhysicalChecks() throws Exception {
        edt(() -> { Container checks=(Container)field(panel,"workspaceChecks"); for(Component child:checks.getComponents())if(child instanceof JCheckBox box && !box.isSelected())box.doClick(); return null; });
    }
    private static Object invoke(Object target,String name) throws Exception { Method method=target.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(target); }
    private static Object invoke(Object target,String name,Class<?> type,Object value) throws Exception { Method method=target.getClass().getDeclaredMethod(name,type);method.setAccessible(true);return method.invoke(target,value); }
    private static void capture(Path out,String name) throws Exception {
        edt(() -> { var image=new java.awt.image.BufferedImage(frame.getWidth(),frame.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();frame.paint(graphics);graphics.dispose();javax.imageio.ImageIO.write(image,"png",out.resolve(name+".png").toFile());return null; });
    }
}
