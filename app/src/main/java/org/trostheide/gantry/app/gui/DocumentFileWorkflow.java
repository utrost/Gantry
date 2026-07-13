package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.app.session.DocumentSession;
import org.trostheide.gantry.app.session.GantryProject;
import org.trostheide.gantry.app.session.GantryProjectIO;
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
import java.util.function.*;

/** File and source-provenance workflows for the current document. */
final class DocumentFileWorkflow {
    @FunctionalInterface interface BusyRunner {
        void run(String description, Callable<ProcessorOutput> task, Consumer<ProcessorOutput> success);
    }
    record Actions(Supplier<GantryConfig> config, Runnable resetReplot, Runnable refresh,
                   Consumer<Boolean> revectorizeEnabled, Consumer<String> log,
                   Consumer<String> error, Consumer<String> info, BusyRunner busy,
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
        try{actions.openProject().accept(GantryProjectIO.load(file));actions.log().accept("Opened project "+file.getName());}
        catch(IOException ex){actions.error().accept("Failed to open project "+file.getName()+": "+ex.getMessage());}
    }

    void saveProject() {
        if(session.currentOutput()==null){actions.info().accept("Load or import a drawing first.");return;}
        JFileChooser chooser=chooser("Gantry project (*.gantry)","gantry");
        if(chooser.showSaveDialog(parent)!=JFileChooser.APPROVE_OPTION)return;
        File file=withExtension(chooser.getSelectedFile(),"gantry");if(!overwrite(file))return;remember(file);
        try{GantryProjectIO.save(actions.project().get(),file);session.markSaved();actions.log().accept("Saved project "+file.getName());}
        catch(IOException ex){actions.error().accept("Failed to save project "+file.getName()+": "+ex.getMessage());}
    }

    void importSvg() {
        JFileChooser chooser=chooser("Vector artwork — SVG (*.svg)","svg");
        if(chooser.showOpenDialog(parent)!=JFileChooser.APPROVE_OPTION)return;
        File file=chooser.getSelectedFile();remember(file);
        SvgImportDialog.Result options=new SvgImportDialog(owner(),file).showDialog();if(options==null)return;
        actions.busy().run("Import",()->map(options.toolboxConfig()!=null
                ?SvgImportStage.importSvg(file,options.toolboxConfig(),options.importOptions())
                :SvgImportStage.importSvg(file,options.importOptions())),out->{
            editor.replace(out);session.recordSvgSource(file,options.importOptions());loaded(false);
            actions.log().accept(summary("Imported "+file.getName(),out));
        });
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
        SvgImportDialog.Result options=new SvgImportDialog(owner()).showDialog();if(options==null)return;
        final File svg;
        try{svg=File.createTempFile("gantry-vectorize-",".svg");svg.deleteOnExit();}
        catch(IOException ex){actions.error().accept("Vectorize failed: could not create temporary SVG");return;}
        actions.busy().run("Vectorize",()->{
            List<String> args=new ArrayList<>(List.of("-i",image.getAbsolutePath(),"-o",svg.getAbsolutePath()));
            args.addAll(vector.vectorizeArgs());org.trostheide.gantry.vectorize.Main.runSingleFile(args.toArray(String[]::new));
            return map(options.toolboxConfig()!=null?SvgImportStage.importSvg(svg,options.toolboxConfig(),options.importOptions())
                    :SvgImportStage.importSvg(svg,options.importOptions()));
        },out->{editor.replace(out);session.recordSvgSource(svg,options.importOptions());
            session.recordImageSource(image,vector.vectorizeArgs());actions.revectorizeEnabled().accept(true);loaded(false);
            actions.log().accept(summary("Vectorized "+image.getName()+" ("+vector.strategyLabel()+")",out));});
    }

    void reprocessSvg(){
        if(session.sourceSvg()==null||session.sourceSvgOptions()==null){actions.info().accept("Import an SVG file first.");return;}
        org.trostheide.gantry.svgtoolbox.Config toolbox=new EditProcessDialog(owner(),session.sourceSvg()).showDialog();if(toolbox==null)return;
        File source=session.sourceSvg();editor.snapshot();
        actions.busy().run("Process SVG",()->SvgImportStage.importSvg(source,toolbox,session.sourceSvgOptions()),out->{
            editor.update(out);visualization.loadPathsPreservingOverlay(out);actions.refresh().run();actions.log().accept("Reprocessed "+source.getName());});
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
    private void message(String text,String title){JOptionPane.showMessageDialog(parent,text,title,JOptionPane.INFORMATION_MESSAGE);}
    private static String summary(String action,ProcessorOutput out){return String.format("%s: %d layer(s), %d command(s)",action,out.layers().size(),out.metadata().totalCommands());}
}
