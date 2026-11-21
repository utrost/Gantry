import org.trostheide.gantry.app.GantryApp;
import org.trostheide.gantry.app.gui.PlotterPanel;
import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.command.*;
import org.trostheide.gantry.pipeline.io.ProcessorOutputIO;
import org.trostheide.gantry.plotter.*;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;

/** Actual Swing workflows over production GcodeBackend and in-memory serial transport only. */
public final class SerialSimulatorAcceptance extends GuiAcceptance {
    public static void main(String[] args) {
        try {
            Path output=Path.of(args[1]).toAbsolutePath();Files.createDirectories(output);
            profile=Files.createTempDirectory(output,"profile-");
            System.setProperty("gantry.config.file",profile.resolve("config.json").toString());
            var config=new GantryConfig();config.mock=true;config.serialSimulator=false;
            config.showWelcomeOnStartup=false;config.preflightBeforeStart=false;
            config.machineOrigin="Bottom-Left";config.canvasAlignment=null;
            config.gcode.serialPort="THIS_PORT_MUST_NEVER_BE_OPENED";
            config.gcode.responseTimeoutSeconds=2;config.gcode.positionPollIntervalSeconds=.01;
            config.gcode.penDownDelayMillis=0;config.gcode.feedRateDraw=6000;config.gcode.feedRateTravel=6000;
            config.gcode.machineWidth=100;config.gcode.machineHeight=100;
            config.stations.put("paint",new StationConfig(80,80,0,"dip_swirl","#123456",20,1));
            config.stations.put("rinse",new StationConfig(70,80,0,"rinse",null,20,1));
            ConfigStore.save(config,profile.resolve("config.json").toFile());
            Thread.setDefaultUncaughtExceptionHandler((thread,error)->{error.printStackTrace();System.exit(1);});
            GantryApp.main(new String[0]);
            await(()->{for(Window w:Window.getWindows())if(w instanceof JFrame f&&f.isShowing()){
                frame=f;panel=(PlotterPanel)f.getContentPane();return true;}return false;});
            edt(()->{frame.setSize(1200,850);frame.validate();return null;});
            menu("Preferences...");JDialog settings=dialog("Settings");
            edt(()->{simulationChoice(settings).setSelectedItem("Serial GRBL simulator");return null;});
            captureWindow(settings,output.resolve("settings.png"));
            click(settings,"OK");await(()->!settings.isShowing());
            check(ConfigStore.load(profile.resolve("config.json").toFile()).serialSimulator,"actual Settings selection persists");
            Path drawing=profile.resolve("two-pens.json");
            var document=new ProcessorOutput(new Metadata("serial acceptance",Instant.EPOCH,"paint","mm",4,new Bounds(10,10,20,20)),List.of(
                    new Layer("one","paint","#123456",List.of(new MoveCommand(1,10,10),new DrawCommand(2,List.of(new Point(15,10))))),
                    new Layer("two","paint","#654321",List.of(new MoveCommand(3,10,20),new DrawCommand(4,List.of(new Point(20,20)))))));
            ProcessorOutputIO.save(document,drawing.toFile());
            menu("Open Commands (JSON)...");choose(drawing);await(()->project().output()!=null);
            click(panel,"All");
            click(panel,"Connect");await(()->jobs().isConnected()&&idle());
            check(jobs().backend() instanceof GcodeBackend&&jobs().backend().isSimulation(),"production serial backend, no hardware");
            var transport=transport();
            check(labelText("safetyStatusLabel").contains("Serial GRBL simulator"),"visible serial simulator identity");
            edt(()->{frame.setSize(1024,800);frame.validate();return null;});
            await(()->frame.getWidth()==1024);
            edt(()->{
                for(Component control:List.of(findButton(panel,"Disconnect"),findButton(panel,"Stop"),
                        (Component)field(field(panel,"workspace"),"machineToggle"))) {
                    var at=SwingUtilities.convertPoint(control,0,0,frame);
                    check(control.isShowing()&&at.x>=0&&at.y>=0&&at.x+control.getWidth()<=frame.getWidth()&&at.y+control.getHeight()<=frame.getHeight(),"compact serial workspace keeps connection, Machine and Stop visible");
                }
                return null;
            });
            capture(output.resolve("compact.png"));
            edt(()->{frame.setSize(1200,850);frame.validate();return null;});
            capture(output.resolve("connected.png"));
            System.out.println("PASS serial simulator connects through production backend with an invalid physical-port name");

            menu("Find Starting Corner (Home)");click(dialog("Home machine"),"OK");
            await(()->!jobs().isMachineBusy()&&idle());
            check(transport.commands().contains("$H"),"Home uses the actual serial protocol");
            menu("Test Color Stations...");JDialog station=dialog("Test Color Stations");click(station,"Next >");
            click(station,"Move here (pen up)");await(()->!jobs().isMachineBusy()&&transport.snapshot().x()==80);
            check(!transport.snapshot().penDown(),"station dry visit stays pen-up");
            click(station,"Wet test (dip)");await(()->!jobs().isMachineBusy()&&idle());
            check(!transport.snapshot().penDown(),"station wet test ends pen-up after motion completion");
            captureWindow(station,output.resolve("station-check.png"));click(station,"Cancel");click(dialog("Cancel wizard"),"Yes");await(()->!station.isShowing());
            System.out.println("PASS Home, station dry/wet visits and completion barriers through actual Swing actions");

            click(panel,"Start plotting");await(()->(boolean)field(panel,"awaitingLayerConfirmation"));
            long before=barriers(transport);transport.pauseExecution();click(panel,"Pen ready — continue");
            await(()->barriers(transport)>before);
            check(!(boolean)field(panel,"awaitingLayerConfirmation"),"next pen gate stays closed while parking");
            check(jobs().isPlotting(),"job remains active during parking");
            check(findButton(panel,"Stop")!=null&&findButton(panel,"Stop").isEnabled(),"Stop stays available during barrier");
            await(()->labelText("safetyStatusLabel").contains("RUN") && (boolean)field(panel,"waitingForMotionCompletion"));
            check(findButton(panel,"Pause")==null,"Pause cannot submit conflicting pen commands during the completion barrier");
            capture(output.resolve("parking-wait.png"));
            transport.resumeExecution();await(()->(boolean)field(panel,"awaitingLayerConfirmation"));
            check(transport.snapshot().x()==0&&transport.snapshot().y()==0&&!transport.snapshot().penDown(),"next pen offered only after completed origin return");
            click(panel,"Pen ready — continue");await(()->!jobs().isPlotting()&&history()==1&&idle());
            await(()->field(panel,"outcome")!=null&&field(panel,"outcome").toString().equals("COMPLETE"));
            check(findButton(panel,"Plot another copy")!=null,"completion view has rendered");
            check(labelText("safetyStatusLabel").contains("no physical hardware"),"completion remains marked simulated");
            capture(output.resolve("complete.png"));
            System.out.println("PASS next-pen prompt and successful history wait for the production serial planner barrier");

            click(panel,"Plot another copy");click(panel,"Start plotting");await(()->(boolean)field(panel,"awaitingLayerConfirmation"));
            long stopBefore=barriers(transport);transport.pauseExecution();click(panel,"Pen ready — continue");
            await(()->barriers(transport)>stopBefore);click(panel,"Stop");
            await(()->!jobs().isMachineBusy()&&idle()&&field(panel,"outcome")!=null&&field(panel,"outcome").toString().equals("STOPPED"));
            check(history()==1,"stopped barrier cannot create successful history");
            check(field(panel,"outcome").toString().equals("STOPPED"),"Stop is not a failure dialog");
            capture(output.resolve("stopped.png"));
            System.out.println("PASS Stop during the serial completion barrier prevents the next handoff and false success");

            click(panel,"Return to artwork");
            click(panel,"Disconnect");await(()->!jobs().isConnected());
            click(panel,"Connect");await(()->jobs().isConnected()&&idle());
            check(transport()!=transport&&!transport.isOpen(),"reconnect creates a fresh in-memory transport");
            edt(()->{((AbstractButton)field(field(panel,"workspace"),"machineToggle")).doClick();return null;});
            transport().armFault(SerialSimulatorTransport.Fault.REJECT_COMMAND);
            click(panel,"Frame artwork — pen up");
            await(()->!jobs().isMachineBusy()&&field(panel,"outcome")!=null&&field(panel,"outcome").toString().equals("FAILED"));
            check(history()==1,"failed manual action does not create successful plot history");
            capture(output.resolve("rejected-command.png"));
            System.out.println("PASS reconnect and injected command rejection remain visible in the real workspace");
            jobs().cancelAndDisconnect(5000);
            System.out.println("Serial simulator GUI acceptance passed: "+output);
            System.exit(0);
        } catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    static SerialSimulatorTransport transport() throws Exception {return (SerialSimulatorTransport)field(jobs().backend(),"transport");}
    static boolean idle() throws Exception {return ((GcodeBackend)jobs().backend()).getMachineState()==GcodeBackend.MachineState.IDLE;}
    static long barriers(SerialSimulatorTransport transport){return transport.commands().stream().filter("G4 P0"::equals).count();}
    static int history() throws Exception {return ((PlotJobHistory)field(panel,"plotHistory")).jobs().size();}
    static String labelText(String name) throws Exception {return ((JLabel)field(panel,name)).getText();}
    static void capture(Path path) throws Exception {
        edt(()->{var image=new java.awt.image.BufferedImage(frame.getWidth(),frame.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
            var graphics=image.createGraphics();frame.paint(graphics);graphics.dispose();javax.imageio.ImageIO.write(image,"png",path.toFile());return null;});
    }
    static JComboBox<?> simulationChoice(Container root) {
        for(Component component:root.getComponents()) {
            if(component instanceof JComboBox<?> combo && combo.getItemCount()==2 && "Lightweight mock".equals(combo.getItemAt(0)))return combo;
            if(component instanceof Container nested){JComboBox<?> result=simulationChoice(nested);if(result!=null)return result;}
        }
        return null;
    }
    static void captureWindow(Window window,Path path) throws Exception {
        edt(()->{var image=new java.awt.image.BufferedImage(window.getWidth(),window.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
            var graphics=image.createGraphics();window.paint(graphics);graphics.dispose();javax.imageio.ImageIO.write(image,"png",path.toFile());return null;});
    }

}
