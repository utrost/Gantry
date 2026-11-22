import org.trostheide.gantry.app.GantryApp;
import org.trostheide.gantry.app.gui.*;
import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.app.session.GantryProjectIO;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.command.*;
import org.trostheide.gantry.pipeline.io.ProcessorOutputIO;
import org.trostheide.gantry.plotter.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;

/** Actual GUI: anchor save/undo, firmware review, real simulated mid-stroke Stop, process restart and selected-stroke execution. */
public final class RoadmapAcceptance extends GuiAcceptance {
    public static void main(String[] args){try{
        profile=Path.of(args[1]).toAbsolutePath();Files.createDirectories(profile);String phase=args[2];Path evidence=profile.resolve("evidence");Files.createDirectories(evidence);
        System.setProperty("gantry.config.file",profile.resolve("config.json").toString());
        if(phase.equals("seed")){
            var config=new GantryConfig();config.mock=true;config.serialSimulator=true;config.showWelcomeOnStartup=false;config.preflightBeforeStart=false;
            config.machineOrigin="Bottom-Left";config.canvasAlignment=null;config.gcode.serialPort="NEVER_OPEN_A_PHYSICAL_PORT";
            config.gcode.machineWidth=100;config.gcode.machineHeight=100;config.gcode.feedRateDraw=600;config.gcode.feedRateTravel=3000;
            config.gcode.responseTimeoutSeconds=3;config.gcode.positionPollIntervalSeconds=.01;config.gcode.penDownDelayMillis=0;
            ConfigStore.save(config,profile.resolve("config.json").toFile());
        }
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{error.printStackTrace();System.exit(1);});GantryApp.main(new String[0]);
        await(()->{for(Window w:Window.getWindows())if(w instanceof JFrame f&&f.isShowing()){frame=f;panel=(PlotterPanel)f.getContentPane();return true;}return false;});
        edt(()->{frame.setSize(1200,850);frame.validate();return null;});
        if(phase.equals("seed")){
            Path source=profile.resolve("drawing.json");
            var output=new ProcessorOutput(new Metadata("restart acceptance",Instant.EPOCH,"pen","mm",6,new Bounds(10,10,90,40)),List.of(
                    new Layer("one","pen",List.of(new MoveCommand(1,10,10),new DrawCommand(2,List.of(new Point(10,10),new Point(90,10))),new MoveCommand(3,10,20),new DrawCommand(4,List.of(new Point(30,20))))),
                    new Layer("two","pen",List.of(new MoveCommand(5,20,30),new DrawCommand(6,List.of(new Point(40,40)))))));
            ProcessorOutputIO.save(output,source.toFile());menu("Open Commands (JSON)...");choose(source);await(()->project().output()!=null);click(panel,"All");
            VisualizationPanel view=(VisualizationPanel)field(panel,"visPanel");
            edt(()->{referenceChoice(panel).setSelectedIndex(4);return null;});
            click(panel,"Undo");await(()->view.getPlacementAnchor()==0);
            click(panel,"Redo");await(()->view.getPlacementAnchor()==4);
            edt(()->{findSpinner(panel,"Artwork X in millimetres").setValue(50.0);findSpinner(panel,"Artwork Y in millimetres").setValue(50.0);return null;});double[] fixed=edt(view::getContentMotorAnchor);
            edt(()->{findSpinner(panel,"Artwork Width in millimetres").setValue(40.0);return null;});
            System.out.println("Anchor size after resize: "+java.util.Arrays.toString(edt(view::getContentMotorSize))+" reference "+java.util.Arrays.toString(fixed));
            check(close(fixed,edt(view::getContentMotorAnchor)),"resize preserves selected center");
            click(panel,"Rotate 90°");await(()->view.getOverlayRotation()==90);check(close(fixed,edt(view::getContentMotorAnchor)),"rotation preserves selected center");
            SerialSimulatorAcceptance.captureWindow(frame,evidence.resolve("anchor.png"));
            edt(()->{frame.setSize(1024,768);frame.validate();return null;});
            check(edt(()->referenceChoice(panel).isShowing()),"reference selector remains visible at minimum window size");
            SerialSimulatorAcceptance.captureWindow(frame,evidence.resolve("anchor-compact.png"));
            edt(()->{frame.setSize(1200,850);frame.validate();return null;});
            click(panel,"Undo");await(()->view.getOverlayRotation()==0);click(panel,"Undo");await(()->Math.abs(view.getContentMotorSize()[0]-80)<.001);
            check(view.getPlacementAnchor()==4,"anchor participates in placement history without moving original geometry");
            menu("Save Project...");choose(profile.resolve("anchors.gantry"));await(()->Files.exists(profile.resolve("anchors.gantry")));
            check(GantryProjectIO.load(profile.resolve("anchors.gantry").toFile()).placement().anchor()==4,"anchor saved in project");
            click(panel,"Connect");await(()->jobs().isConnected()&&!jobs().isMachineBusy());
            reviewFirmware(evidence.resolve("firmware.png"));
            menu("Review firmware limits...");JDialog cancelledReview=dialog("Review firmware limits");
            click(cancelledReview,"Cancel");await(()->!cancelledReview.isShowing()&&!jobs().isMachineBusy());
            check(((GcodeBackend)jobs().backend()).needsSafetyReview(),"cancelled fresh read invalidates earlier approval");
            reviewFirmware(null);
            menu("Calibrate Axes...");JDialog calibration=dialog("Calibrate Axes");
            click(calibration,"Next >");click(calibration,"Next >");
            click(calibration,"Review firmware limits...");JDialog nestedReview=dialog("Review firmware limits");
            check(nestedReview.getOwner()==calibration,"firmware review belongs to the active modal wizard");
            JDialog cancelledNestedReview=nestedReview;click(nestedReview,"Cancel");await(()->!cancelledNestedReview.isShowing());
            check(((GcodeBackend)jobs().backend()).needsSafetyReview(),"wizard cancellation keeps XY blocked");
            click(calibration,"Review firmware limits...");nestedReview=dialog("Review firmware limits");
            JDialog approvedNestedReview=nestedReview;
            edt(()->{((JComboBox<?>)SerialSimulatorAcceptance.named(approvedNestedReview,"firmwareCoordinateSpace")).setSelectedIndex(1);
                ((JCheckBox)SerialSimulatorAcceptance.named(approvedNestedReview,"firmwareReferenceConfirmed")).setSelected(true);return null;});
            click(nestedReview,"OK");await(()->!approvedNestedReview.isShowing()&&!jobs().isMachineBusy());
            click(calibration,"X +1 mm");await(()->!jobs().isMachineBusy()&&Math.abs(SerialSimulatorAcceptance.transport().snapshot().x()-1)<.001);
            SerialSimulatorAcceptance.captureWindow(calibration,evidence.resolve("calibration-review.png"));
            click(calibration,"Cancel");click(dialog("Cancel wizard"),"Yes");await(()->!calibration.isShowing());
            click(panel,"Start plotting");await(()->(boolean)field(panel,"awaitingLayerConfirmation"));click(panel,"Pen ready — continue");
            var transport=SerialSimulatorAcceptance.transport();
            await(()->{var state=transport.snapshot();return state.penDown()&&state.x()>12&&state.x()<85;});
            transport.pauseExecution();click(panel,"Stop");await(()->!jobs().isMachineBusy()&&field(panel,"outcome")!=null&&field(panel,"outcome").toString().equals("STOPPED"));
            var saved=checkpoint().snapshot();check(saved!=null&&saved.confirmed().isEmpty()&&saved.started()>0,"mid-stroke abort remains uncertain");
            check(saved.state().equals("STOPPED_UNCERTAIN"),"Stop final state persists despite interrupted worker");
            check(((GcodeBackend)jobs().backend()).needsSafetyReview(),"Stop invalidates reference review");
            menu("Review partial restart...");JDialog review=dialog("Review partial restart");
            var list=(JList<?>)SerialSimulatorAcceptance.named(review,"restartStrokes");
            // Pick second stroke through the actual rendered preview, not a controller shortcut.
            edt(()->{Component canvas=SerialSimulatorAcceptance.named(review,"restartCanvas");double scale=Math.min((canvas.getWidth()-32)/100.0,(canvas.getHeight()-32)/100.0);
                var stroke=checkpoint().snapshot().strokes().get(1).points();Point a=stroke.get(0),b=stroke.get(stroke.size()-1);
                double[] point=MachinePageProjection.from(checkpoint().snapshot().source().reviewSettings()).point((a.x()+b.x())/2,(a.y()+b.y())/2);
                canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,(int)(16+point[0]*scale),(int)(16+point[1]*scale),1,false,MouseEvent.BUTTON1));return null;});
            check(edt(list::getSelectedIndex)==1,"canvas click selects exact second stroke");
            edt(()->{((JCheckBox)SerialSimulatorAcceptance.named(review,"restartReverse")).doClick();return null;});
            SerialSimulatorAcceptance.captureWindow(review,evidence.resolve("uncertain-reverse.png"));click(review,"Cancel");await(()->!review.isShowing());
            check(checkpoint().snapshot().selection()==1&&checkpoint().snapshot().reverse(),"cancelled proposal remains persisted without motion");
            check(!jobs().isPlotting(),"review never starts motion");jobs().cancelAndDisconnect(5000);
            System.out.println("PASS anchor/Undo/save, firmware review, real mid-stroke Stop and persistent visual reverse proposal");System.exit(0);
        }
        menu("Open Gantry Project...");choose(profile.resolve("anchors.gantry"));await(()->project().output()!=null);
        check(project().placement().anchor()==4,"anchor restored after actual process restart");
        click(panel,"Connect");await(()->jobs().isConnected()&&!jobs().isMachineBusy());reviewFirmware(null);
        menu("Review partial restart...");JDialog review=dialog("Review partial restart");
        JList<?> list=(JList<?>)SerialSimulatorAcceptance.named(review,"restartStrokes");check(edt(list::getSelectedIndex)==1,"saved start stroke restored");
        edt(()->{var reverse=(JCheckBox)SerialSimulatorAcceptance.named(review,"restartReverse");check(reverse.isSelected(),"saved reverse mode restored");reverse.doClick();
            for(String name:new String[]{"restartMappingConfirmed","restartPensConfirmed","restartUncertaintyConfirmed"})((JCheckBox)SerialSimulatorAcceptance.named(review,name)).setSelected(true);return null;});
        SerialSimulatorAcceptance.captureWindow(review,evidence.resolve("reviewed-forward.png"));click(review,"OK");await(()->!review.isShowing());
        check(!jobs().isPlotting(),"approval prepares a job without moving");
        click(panel,"Start plotting");await(()->(boolean)field(panel,"awaitingLayerConfirmation"));click(panel,"Pen ready — continue");
        await(()->(boolean)field(panel,"awaitingLayerConfirmation"));click(panel,"Pen ready — continue");
        await(()->!jobs().isPlotting()&&field(panel,"outcome")!=null&&field(panel,"outcome").toString().equals("COMPLETE"));
        check(checkpoint().snapshot().confirmed().equals(List.of(1,2)),"only explicitly retained strokes become controller-confirmed");
        var skipped=checkpoint().snapshot().strokes().get(0).points();Point skippedEnd=skipped.get(skipped.size()-1);
        String skippedTarget=String.format(java.util.Locale.ROOT,"G1 X%.3f Y%.3f",skippedEnd.x(),skippedEnd.y());
        check(SerialSimulatorAcceptance.transport().commands().stream().noneMatch(s->s.startsWith(skippedTarget)),"skipped first stroke is never submitted");
        SerialSimulatorAcceptance.captureWindow(frame,evidence.resolve("partial-complete.png"));jobs().cancelAndDisconnect(5000);
        System.out.println("PASS actual process restart, restored anchor/proposal, explicit registration/uncertainty review and selected-stroke execution");System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();try{SerialSimulatorAcceptance.captureWindow(frame,profile.resolve("failure.png"));System.err.println("Placement: "+((VisualizationPanel)field(panel,"visPanel")).placement());}catch(Throwable ignored){}System.exit(1);}}
    static ReviewedRestart.Store checkpoint() throws Exception {return (ReviewedRestart.Store)field(panel,"restartStore");}
    static boolean close(double[] a,double[] b){return Math.abs(a[0]-b[0])<.001&&Math.abs(a[1]-b[1])<.001;}
    static JComboBox<?> referenceChoice(Container root){for(Component child:root.getComponents()){
        if(child instanceof JComboBox<?> combo&&"Artwork reference point".equals(combo.getAccessibleContext().getAccessibleName()))return combo;
        if(child instanceof Container nested){var result=referenceChoice(nested);if(result!=null)return result;}
    }return null;}
    static void reviewFirmware(Path image) throws Exception {
        menu("Review firmware limits...");JDialog review=dialog("Review firmware limits");
        edt(()->{((JComboBox<?>)SerialSimulatorAcceptance.named(review,"firmwareCoordinateSpace")).setSelectedIndex(1);
            ((JCheckBox)SerialSimulatorAcceptance.named(review,"firmwareReferenceConfirmed")).setSelected(true);return null;});
        if(image!=null)SerialSimulatorAcceptance.captureWindow(review,image);click(review,"OK");
        await(()->!review.isShowing()&&!jobs().isMachineBusy()&&!((GcodeBackend)jobs().backend()).needsSafetyReview());
    }
}
