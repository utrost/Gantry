package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.pipeline.svgimport.SvgImportStage;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.function.Supplier;

/** A revisioned physical import preview; old or failed results are never marked ready. */
final class ImportPreviewPanel extends JPanel {
    private final VisualizationPanel canvas=new VisualizationPanel();
    private final JTextArea status=WorkspaceStyle.note("Choose artwork to preview.");
    private final Supplier<SvgImportOptionsPanel.Result> options;
    private final Timer debounce;
    private SwingWorker<ProcessorOutput,Void> worker;
    private File source;
    private long revision;
    private boolean ready;
    private Runnable changed=()->{};
    ImportPreviewPanel(double width,double height,Supplier<SvgImportOptionsPanel.Result> options){
        super(new BorderLayout(6,6));this.options=options;
        canvas.setMachineSize(width,height);canvas.editingEnabled=false;
        canvas.setMinimumSize(new Dimension(180,180));
        add(canvas,BorderLayout.CENTER);add(status,BorderLayout.SOUTH);
        debounce=new Timer(250,e->render());debounce.setRepeats(false);
    }
    void configure(org.trostheide.gantry.app.plot.PlotSettings s){
        canvas.setMachineSize(s.resolveMachineWidth(),s.resolveMachineHeight());
        canvas.setMachineOrigin((s.originBottom?"Bottom":"Top")+"-"+(s.originRight?"Right":"Left"));
        canvas.setEffectiveAxes(s.swapXY,s.invertX,s.invertY);canvas.setDataRotation(s.dataRotation);
        canvas.setFlipY(s.flipY);canvas.setPadding(s.paddingX,s.paddingY);canvas.setCanvasAlignment(s.canvasAlign);
        schedule();
    }
    void setSource(File file){source=file;schedule();}
    void onChange(Runnable callback){changed=callback;}
    boolean ready(){return ready;}
    void schedule(){
        revision++;ready=false;if(worker!=null)worker.cancel(true);changed.run();
        status.setText(source==null?"Waiting for a valid trace…":"Updating physical preview…");
        debounce.restart();
    }
    private void render(){
        File requestedSource=source;long requested=revision;
        SvgImportOptionsPanel.Result settings=options.get();
        if(requestedSource==null || settings==null){status.setText("Choose valid size and processing settings.");return;}
        worker=new SwingWorker<>(){
            protected ProcessorOutput doInBackground()throws Exception{
                return settings.toolboxConfig()==null?SvgImportStage.importSvg(requestedSource,settings.importOptions()):
                        SvgImportStage.importSvg(requestedSource,settings.toolboxConfig(),settings.importOptions());
            }
            protected void done(){
                if(requested!=revision || isCancelled())return;
                try{
                    ProcessorOutput output=get();
                    boolean drawable=output.layers().stream().flatMap(l->l.commands().stream()).anyMatch(c->c instanceof org.trostheide.gantry.model.command.DrawCommand);
                    if(!drawable)throw new IllegalArgumentException("No drawable paths. Change the trace or processing settings.");
                    canvas.loadFromOutput(output);
                    canvas.setSuppressAlignment(settings.importOptions().preserveSvgViewport());
                    if(!settings.importOptions().preserveSvgViewport())canvas.setContentMotorMin(0,0);
                    double[] size=canvas.getContentMotorSize();
                    status.setText(String.format(java.util.Locale.ROOT,"Result: %.1f × %.1f mm · %d pen layers%s",size[0],size[1],output.layers().size(),
                            size[0]>canvas.machineWidth+.01 || size[1]>canvas.machineHeight+.01?" — exceeds machine bed; output may be clipped":""));
                    ready=true;
                }catch(Exception error){Throwable reason=error.getCause()==null?error:error.getCause();status.setText("Preview unavailable: "+reason.getMessage());ready=false;}
                changed.run();
            }
        };worker.execute();
    }
    void close(){revision++;debounce.stop();if(worker!=null)worker.cancel(true);}
}
