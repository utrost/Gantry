package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.plotter.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.function.*;

/** G-code export and replay file workflows. */
final class GcodeFileWorkflow {
    private final Component parent; private final File configFile; private final Supplier<GantryConfig> config;
    private final Supplier<ProcessorOutput> prepared; private final Supplier<Boolean> hasSelection;
    private final Supplier<PlotterBackend> backend; private final DoubleSupplier alignX,alignY;
    private final Consumer<String> log,error,info;
    GcodeFileWorkflow(Component parent,File configFile,Supplier<GantryConfig> config,Supplier<ProcessorOutput> prepared,
            Supplier<Boolean> hasSelection,Supplier<PlotterBackend> backend,DoubleSupplier alignX,DoubleSupplier alignY,
            Consumer<String> log,Consumer<String> error,Consumer<String> info){this.parent=parent;this.configFile=configFile;this.config=config;
        this.prepared=prepared;this.hasSelection=hasSelection;this.backend=backend;this.alignX=alignX;this.alignY=alignY;this.log=log;this.error=error;this.info=info;}
    void export(){
        ProcessorOutput output=prepared.get();if(output==null){info.accept("Open a Commands (JSON) file or Import SVG first.");return;}
        if(!hasSelection.get()){info.accept("No layers selected. Tick at least one layer to export.");return;}
        JFileChooser chooser=chooser();if(chooser.showSaveDialog(parent)!=JFileChooser.APPROVE_OPTION)return;File file=chooser.getSelectedFile();
        if(!overwrite(file))return;remember(file);PlotSettings settings=config.get().toPlotSettings();settings.alignmentOffsetOverride=new double[]{alignX.getAsDouble(),alignY.getAsDouble()};
        new Thread(()->{GcodeFileBackend target=new GcodeFileBackend(config.get().gcode,file);PlotService service=new PlotService(target,settings);service.setLogCallback(log);
            if(target.connect()){try{service.plot(output);}finally{target.disconnect();}log.accept("Exported G-code to "+file.getName());}
            else error.accept("Failed to open "+file.getName()+" for writing.");},"gcode-export").start();
    }
    void replay(){
        if(!(backend.get() instanceof GcodeBackend real)){log.accept("ERROR: Connect to a real G-code backend first (not available in mock mode).");return;}
        JFileChooser chooser=chooser();if(chooser.showOpenDialog(parent)!=JFileChooser.APPROVE_OPTION)return;File file=chooser.getSelectedFile();remember(file);
        new Thread(()->{try{log.accept("Replaying "+file.getName()+"...");GcodeFileReplay.replay(file,real,log);log.accept("--- Replay finished ---");}
            catch(IOException ex){log.accept("ERROR: Failed to replay "+file.getName()+": "+ex.getMessage());}},"gcode-replay").start();
    }
    private JFileChooser chooser(){JFileChooser c=new JFileChooser();String dir=config.get().lastDirectory;if(dir!=null&&!dir.isBlank()){File f=new File(dir);if(f.isDirectory())c.setCurrentDirectory(f);}
        c.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Plotter G-code (*.gcode)","gcode"));return c;}
    private boolean overwrite(File f){return !f.exists()||JOptionPane.showConfirmDialog(parent,f.getName()+" already exists. Overwrite it?","Confirm overwrite",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.YES_OPTION;}
    private void remember(File f){File p=f.getAbsoluteFile().getParentFile();if(p==null)return;config.get().lastDirectory=p.getAbsolutePath();try{ConfigStore.save(config.get(),configFile);}catch(IOException ex){log.accept("WARNING: Failed to save config: "+ex.getMessage());}}
}
