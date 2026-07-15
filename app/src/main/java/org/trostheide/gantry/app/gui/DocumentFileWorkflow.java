package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.app.session.DocumentSession;
import org.trostheide.gantry.app.session.GantryProject;
import org.trostheide.gantry.app.session.GantryProjectIO;
import org.trostheide.gantry.app.session.ProcessingRecipe;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.pipeline.io.ProcessorOutputIO;
import org.trostheide.gantry.pipeline.svgimport.SvgImportStage;
import org.trostheide.gantry.watercolor.PaintStation;
import org.trostheide.gantry.watercolor.StationMapper;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.function.*;

/** File and source-provenance workflows for the current document. */
final class DocumentFileWorkflow {
    @FunctionalInterface interface BusyRunner {
        void run(String description, Callable<ProcessorOutput> task, Consumer<ProcessorOutput> success);
    }
    @FunctionalInterface interface CancellableBusyRunner {
        void run(String description, CancellableTask task,
                 Consumer<ProcessorOutput> success, Runnable cancelled);
    }
    @FunctionalInterface interface CancellableTask {
        ProcessorOutput run(BooleanSupplier cancellationRequested) throws Exception;
    }
    record Actions(Supplier<GantryConfig> config, Runnable resetReplot, Runnable refresh,
                   Consumer<Boolean> revectorizeEnabled, Consumer<String> log,
                   Consumer<String> error, Consumer<String> info, Consumer<String> feedback, BusyRunner busy,
                   CancellableBusyRunner cancellableBusy,
                   Supplier<GantryProject> project, Consumer<GantryProject> openProject,
                   Supplier<ProcessorOutput> flattenedOutput) { }

    private final Component parent;
    private final File configFile;
    private final DocumentSession session;
    private final DocumentEditor editor;
    private final VisualizationPanel visualization;
    private final Actions actions;

    DocumentFileWorkflow(Component parent, File configFile, DocumentSession session,
            DocumentEditor editor, VisualizationPanel visualization, Actions actions) {
        this.parent=parent;this.configFile=configFile;this.session=session;this.editor=editor;
        this.visualization=visualization;this.actions=actions;
    }

    void loadCommands() {
        JFileChooser chooser=chooser("Gantry commands — JSON (*.json)","json");
        if(chooser.showOpenDialog(parent)!=JFileChooser.APPROVE_OPTION)return;
        File file=chooser.getSelectedFile();remember(file);
        try { editor.replace(CommandFile.load(file));session.clearSource();session.markSaved();loaded(false);actions.log().accept("Loaded "+file.getName()); }
        catch(IOException ex){actions.error().accept("Failed to load "+file.getName()+": "+ex.getMessage());}
    }

    void openProject() {
        JFileChooser chooser=chooser("Gantry project (*.gantry)","gantry");
        if(chooser.showOpenDialog(parent)!=JFileChooser.APPROVE_OPTION)return;
        File file=chooser.getSelectedFile();remember(file);
        try{actions.openProject().accept(GantryProjectIO.load(file));actions.log().accept("Opened project "+file.getName());actions.feedback().accept("Project opened: "+file.getName());}
        catch(IOException ex){actions.error().accept("Failed to open project "+file.getName()+": "+ex.getMessage());}
    }

    void saveProject() {
        if(session.currentOutput()==null){actions.info().accept("Load or import a drawing first.");return;}
        JFileChooser chooser=chooser("Gantry project (*.gantry)","gantry");
        if(chooser.showSaveDialog(parent)!=JFileChooser.APPROVE_OPTION)return;
        File file=withExtension(chooser.getSelectedFile(),"gantry");if(!overwrite(file))return;remember(file);
        try{GantryProjectIO.save(actions.project().get(),file);session.markSaved();actions.log().accept("Saved project "+file.getName());actions.feedback().accept("Project saved: "+file.getName());}
        catch(IOException ex){actions.error().accept("Failed to save project "+file.getName()+": "+ex.getMessage());}
    }

    void importSvg() {
        JFileChooser chooser=chooser("Vector artwork — SVG (*.svg)","svg");
        if(chooser.showOpenDialog(parent)!=JFileChooser.APPROVE_OPTION)return;
        File file=chooser.getSelectedFile();remember(file);
        SvgImportDialog.Result options=importDialog(file).showDialog();if(options==null)return;
        actions.cancellableBusy().run("Import",cancel->{ProcessorOutput imported=map(options.toolboxConfig()!=null
                ?SvgImportStage.importSvg(file,options.toolboxConfig(),options.importOptions())
                :SvgImportStage.importSvg(file,options.importOptions()));if(cancel.getAsBoolean())throw new CancellationException();return imported;},out->{
            editor.replace(out);session.recordSvgSource(file,options.importOptions(),recipe(options.toolboxConfig()));loaded(false);
            String result=summary("Imported "+file.getName(),out);actions.log().accept(result);actions.feedback().accept(result);
        },()->actions.feedback().accept("Import cancelled. Artwork was not changed."));
    }

    void importImage(){
        JFileChooser chooser=chooser("Raster image — PNG/JPG (*.png, *.jpg, *.jpeg, *.bmp)","png","jpg","jpeg","bmp");
        if(chooser.showOpenDialog(parent)!=JFileChooser.APPROVE_OPTION)return;
        File file=chooser.getSelectedFile();remember(file);vectorize(file,null);
    }

    void revectorize(){
        File image=session.sourceImage();
        if(image==null||!image.exists()){message("No vectorized image to re-tune. Use Import Image first.","Re-vectorize");return;}
        vectorize(image,session.vectorizeArgs());
    }

    private void vectorize(File image,List<String> initialArgs){
        VectorizeStudioDialog.Result vector;
        try{vector=new VectorizeStudioDialog(owner(),image,initialArgs).showDialog();}
        catch(IOException ex){actions.error().accept("Vectorize failed: "+ex.getMessage());return;}
        if(vector==null)return;
        SvgImportDialog.Result options=importDialog(null).showDialog();if(options==null)return;
        final File svg;
        try{svg=File.createTempFile("gantry-vectorize-",".svg");svg.deleteOnExit();}
        catch(IOException ex){actions.error().accept("Vectorize failed: could not create temporary SVG");return;}
        actions.busy().run("Vectorize",()->{
            List<String> args=new ArrayList<>(List.of("-i",image.getAbsolutePath(),"-o",svg.getAbsolutePath()));
            args.addAll(vector.vectorizeArgs());org.trostheide.gantry.vectorize.Main.runSingleFile(args.toArray(String[]::new));
            return map(options.toolboxConfig()!=null?SvgImportStage.importSvg(svg,options.toolboxConfig(),options.importOptions())
                    :SvgImportStage.importSvg(svg,options.importOptions()));
        },out->{editor.replace(out);session.recordSvgSource(svg,options.importOptions(),recipe(options.toolboxConfig()));
            session.recordImageSource(image,vector.vectorizeArgs());actions.revectorizeEnabled().accept(true);loaded(false);
            String result=summary("Vectorized "+image.getName()+" ("+vector.strategyLabel()+")",out);actions.log().accept(result);actions.feedback().accept(result);});
    }

    void reprocessSvg(){
        if(session.sourceSvg()==null||session.sourceSvgOptions()==null){actions.info().accept("Import an SVG file first.");return;}
        org.trostheide.gantry.svgtoolbox.Config initial=session.processingRecipe()==null?null:session.processingRecipe().toConfig();
        EditProcessDialog dialog=new EditProcessDialog(owner(),session.sourceSvg(),initial);
        org.trostheide.gantry.svgtoolbox.Config toolbox=dialog.showDialog();if(toolbox==null)return;
        boolean process=dialog.processingEnabled();
        File source=session.sourceSvg();
        actions.cancellableBusy().run("Process SVG",cancel->{ProcessorOutput processed=process
                ?SvgImportStage.importSvg(source,toolbox,session.sourceSvgOptions())
                :SvgImportStage.importSvg(source,session.sourceSvgOptions());
            if(cancel.getAsBoolean())throw new CancellationException();return processed;},out->{
            editor.snapshot();editor.update(out);session.recordSvgSource(source,session.sourceSvgOptions(),process?ProcessingRecipe.fromConfig(toolbox):null);
            visualization.loadPathsPreservingOverlay(out);actions.refresh().run();actions.log().accept("Reprocessed "+source.getName());},
            ()->actions.feedback().accept("Processing cancelled. Artwork was not changed."));
    }

    void mapColors(){
        if(session.currentOutput()==null){actions.info().accept("Load or import a drawing first.");return;}
        List<PaintStation> stations=stations();if(stations.isEmpty()){actions.info().accept("No stations have a colour configured.");return;}
        editor.snapshot();editor.update(StationMapper.assignByColor(session.currentOutput(),stations));
        visualization.loadPathsPreservingOverlay(session.currentOutput());actions.refresh().run();
        for(Layer layer:session.currentOutput().layers())actions.log().accept("Layer '"+layer.id()+"' ("+layer.color()+") → station '"+layer.stationId()+"'");
    }

    void exportCommands(){
        if(session.currentOutput()==null){actions.info().accept("Load or import a drawing first.");return;}
        JFileChooser chooser=chooser("Gantry commands — JSON (*.json)","json");
        if(chooser.showSaveDialog(parent)!=JFileChooser.APPROVE_OPTION)return;
        File file=chooser.getSelectedFile();if(!overwrite(file))return;remember(file);
        try{ProcessorOutputIO.save(actions.flattenedOutput().get(),file);actions.log().accept("Exported flattened commands "+file.getName());}
        catch(IOException ex){actions.error().accept("Failed to save "+file.getName()+": "+ex.getMessage());}
    }

    private void loaded(boolean preserve){actions.resetReplot().run();visualization.loadFromOutput(session.currentOutput());
        visualization.setContentMotorMin(0,0);actions.refresh().run();}
    private ProcessorOutput map(ProcessorOutput output){List<PaintStation>s=stations();return s.isEmpty()?output:StationMapper.assignByColor(output,s);}
    private List<PaintStation> stations(){List<PaintStation> result=new ArrayList<>();for(Map.Entry<String,StationConfig> e:actions.config().get().stations.entrySet())
        if(e.getValue().color()!=null&&!e.getValue().color().isBlank())result.add(new PaintStation(e.getKey(),e.getValue().color()));return result;}
    private JFileChooser chooser(String description,String...ext){JFileChooser c=new JFileChooser();String d=actions.config().get().lastDirectory;
        if(d!=null&&!d.isBlank()){File f=new File(d);if(f.isDirectory())c.setCurrentDirectory(f);}c.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(description,ext));return c;}
    private void remember(File file){File p=file.getAbsoluteFile().getParentFile();if(p==null)return;actions.config().get().lastDirectory=p.getAbsolutePath();
        try{ConfigStore.save(actions.config().get(),configFile);}catch(IOException ex){actions.log().accept("WARNING: Failed to save config: "+ex.getMessage());}}
    private boolean overwrite(File file){return !file.exists()||JOptionPane.showConfirmDialog(parent,"Overwrite '"+file.getName()+"'?","Confirm overwrite",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.YES_OPTION;}
    private static File withExtension(File file,String extension){return file.getName().toLowerCase().endsWith("."+extension)?file:new File(file.getParentFile(),file.getName()+"."+extension);}
    private Window owner(){return SwingUtilities.getWindowAncestor(parent);}
    private SvgImportDialog importDialog(File sourceSvg){GantryConfig c=actions.config().get();
        return new SvgImportDialog(owner(),sourceSvg,c.gcode.machineWidth,c.gcode.machineHeight);}
    private void message(String text,String title){JOptionPane.showMessageDialog(parent,text,title,JOptionPane.INFORMATION_MESSAGE);}
    private static String summary(String action,ProcessorOutput out){Bounds b=out.metadata().bounds();double w=Math.max(0,b.maxX()-b.minX());double h=Math.max(0,b.maxY()-b.minY());
        return String.format("%s — %.1f × %.1f mm, %d layer(s)",action,w,h,out.layers().size());}
    private static ProcessingRecipe recipe(org.trostheide.gantry.svgtoolbox.Config config){return config==null?null:ProcessingRecipe.fromConfig(config);}
}
