package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.plotter.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.*;

/** G-code export and replay file workflows. */
final class GcodeFileWorkflow {
    private final Component parent; private final File configFile; private final Supplier<GantryConfig> config;
    private final Supplier<ProcessorOutput> prepared; private final Supplier<Boolean> hasSelection;
    private final PlotJobController jobs; private final DoubleSupplier alignX,alignY;
    private final Consumer<String> log,error,info;
    private final Consumer<Boolean> plotting;
    GcodeFileWorkflow(Component parent,File configFile,Supplier<GantryConfig> config,Supplier<ProcessorOutput> prepared,
            Supplier<Boolean> hasSelection,PlotJobController jobs,DoubleSupplier alignX,DoubleSupplier alignY,
            Consumer<String> log,Consumer<String> error,Consumer<String> info,Consumer<Boolean> plotting){this.plotting=plotting;this.parent=parent;this.configFile=configFile;this.config=config;
        this.prepared=prepared;this.hasSelection=hasSelection;this.jobs=jobs;this.alignX=alignX;this.alignY=alignY;this.log=log;this.error=error;this.info=info;}
    void export(){
        ProcessorOutput output=prepared.get();if(output==null){info.accept("Open a Commands (JSON) file or Import SVG first.");return;}
        if(!hasSelection.get()){info.accept("No layers selected. Tick at least one layer to export.");return;}
        JFileChooser chooser=chooser();if(chooser.showSaveDialog(parent)!=JFileChooser.APPROVE_OPTION)return;File file=chooser.getSelectedFile();
        if(!overwrite(file))return;remember(file);PlotSettings settings=config.get().toPlotSettings();settings.alignmentOffsetOverride=new double[]{alignX.getAsDouble(),alignY.getAsDouble()};
        new Thread(()->exportAtomically(output,settings,file),"gcode-export").start();
    }
    void replay(){
        if(!(jobs.backend() instanceof GcodeBackend real)){log.accept("ERROR: Connect to a real G-code backend first (not available in mock mode).");return;}
        JFileChooser chooser=chooser();if(chooser.showOpenDialog(parent)!=JFileChooser.APPROVE_OPTION)return;File file=chooser.getSelectedFile();remember(file);
        GcodeOptions options = new GcodeOptions(); options.copyFrom(config.get().gcode);
        new SwingWorker<GcodeFileReplay.Plan, Void>() {
            protected GcodeFileReplay.Plan doInBackground() throws Exception { return GcodeFileReplay.preflight(file, options); }
            protected void done() {
                try {
                    GcodeFileReplay.Plan plan = get();
                    if (JOptionPane.showConfirmDialog(parent, plan.summary(), "Replay preflight",
                            JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
                    if (jobs.backend() != real) { info.accept("The plotter connection changed. Run replay again."); return; }
                    if (jobs.isMachineBusy()) { info.accept("The plotter is busy. Wait for the current job to finish."); return; }
                    if (!plan.matches(config.get().gcode)) { info.accept("Machine settings changed. Run replay preflight again."); return; }
                    try {
                        jobs.startReplay(plan, log, (completed, failure) -> SwingUtilities.invokeLater(() -> {
                            plotting.accept(false);
                            if (completed) info.accept("Replay finished.");
                            else if (failure instanceof java.util.concurrent.CancellationException || failure instanceof InterruptedIOException)
                                info.accept("Replay stopped; safety recovery attempted.");
                            else if (failure != null) error.accept("Replay failed: " + failure.getMessage());
                        }));
                        plotting.accept(true);
                    } catch (RuntimeException failure) { throw failure; }
                } catch (Exception failure) {
                    Throwable reason = failure.getCause() == null ? failure : failure.getCause();
                    error.accept("Cannot replay: " + reason.getMessage());
                }
            }
        }.execute();
    }

    private void exportAtomically(ProcessorOutput output,PlotSettings settings,File destination){
        File parentDir=destination.getAbsoluteFile().getParentFile();
        File temporary=null;
        try{
            temporary=File.createTempFile(destination.getName()+".",".tmp",parentDir);
            GcodeFileBackend target=new GcodeFileBackend(config.get().gcode,temporary);
            PlotService service=new PlotService(target,settings);service.setLogCallback(log);
            if(!target.connect())throw new IOException("Could not open temporary output file");
            try{service.plot(output);}finally{target.disconnect();}
            try{Files.move(temporary.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temporary.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING);}
            log.accept("Exported G-code to "+destination.getName());
        }catch(IOException|RuntimeException failure){error.accept("Failed to export "+destination.getName()+": "+failure.getMessage());}
        finally{if(temporary!=null)try{Files.deleteIfExists(temporary.toPath());}catch(IOException ignored){}}
    }
    private JFileChooser chooser(){JFileChooser c=new JFileChooser();String dir=config.get().lastDirectory;if(dir!=null&&!dir.isBlank()){File f=new File(dir);if(f.isDirectory())c.setCurrentDirectory(f);}
        c.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Plotter G-code (*.gcode)","gcode"));return c;}
    private boolean overwrite(File f){return !f.exists()||JOptionPane.showConfirmDialog(parent,f.getName()+" already exists. Overwrite it?","Confirm overwrite",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.YES_OPTION;}
    private void remember(File f){File p=f.getAbsoluteFile().getParentFile();if(p==null)return;config.get().lastDirectory=p.getAbsolutePath();try{ConfigStore.save(config.get(),configFile);}catch(IOException ex){log.accept("WARNING: Failed to save config: "+ex.getMessage());}}
}
