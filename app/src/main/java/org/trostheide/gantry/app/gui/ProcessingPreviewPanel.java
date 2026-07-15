package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.Command;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;
import org.trostheide.gantry.pipeline.svgimport.SvgImportStage;
import org.trostheide.gantry.svgtoolbox.Config;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Debounced, non-blocking before/after preview with plot-complexity feedback. */
final class ProcessingPreviewPanel extends JPanel {
    private final File source;
    private final ToolboxOptionsPanel options;
    private final PreviewCanvas before = new PreviewCanvas();
    private final PreviewCanvas after = new PreviewCanvas();
    private final JLabel status = new JLabel("Preparing preview…");
    private final JProgressBar progress = new JProgressBar();
    private final Timer debounce;
    private SwingWorker<Result,Void> worker;
    private int generation;
    private ProcessorOutput original;

    private record Result(int generation, ProcessorOutput original, ProcessorOutput processed) { }

    ProcessingPreviewPanel(File source, ToolboxOptionsPanel options) {
        super(new BorderLayout(6,6)); this.source=source; this.options=options;
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Preview & impact"),
                BorderFactory.createEmptyBorder(5,5,5,5)));
        JPanel canvases=new JPanel(new GridLayout(1,2,6,0));
        canvases.add(labeled("Original",before));canvases.add(labeled("With these settings",after));
        add(canvases,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(6,0));progress.setIndeterminate(true);progress.setPreferredSize(new Dimension(90,10));
        footer.add(status,BorderLayout.CENTER);footer.add(progress,BorderLayout.EAST);add(footer,BorderLayout.SOUTH);
        setPreferredSize(new Dimension(480,390));
        debounce=new Timer(350,e->refreshNow());debounce.setRepeats(false);
        options.addChangeListener(this::schedule);
        if(source==null){status.setText("A preview will appear when source artwork is available.");progress.setVisible(false);}
        else schedule();
    }

    void schedule(){if(source==null)return;debounce.restart();status.setText("Updating preview…");progress.setVisible(true);}

    private void refreshNow() {
        if(worker!=null)worker.cancel(true);
        final Config config;
        try{config=options.buildConfig();}catch(IllegalArgumentException ex){status.setText(ex.getMessage());progress.setVisible(false);return;}
        int requested=++generation;
        worker=new SwingWorker<>(){
            @Override protected Result doInBackground() throws Exception{
                ProcessorOutput base=original;
                if(base==null)base=SvgImportStage.importSvg(source,SvgImportOptions.defaults());
                if(isCancelled())throw new CancellationException();
                ProcessorOutput processed=options.hasProcessingEnabled()
                        ?SvgImportStage.importSvg(source,config,SvgImportOptions.defaults()):base;
                return new Result(requested,base,processed);
            }
            @Override protected void done(){
                try{Result r=get();if(r.generation()!=generation)return;original=r.original();before.setOutput(r.original());after.setOutput(r.processed());
                    status.setText(impact(r.original(),r.processed()));progress.setVisible(false);
                }catch(CancellationException ignored){}catch(Exception ex){if(requested==generation){Throwable cause=ex.getCause()==null?ex:ex.getCause();
                    status.setText("Preview unavailable: "+cause.getMessage());progress.setVisible(false);}}
            }};worker.execute();
    }

    private static String impact(ProcessorOutput a,ProcessorOutput b){
        StudioMetrics before=StudioMetrics.of(a),after=StudioMetrics.of(b);
        double beforeSeconds=roughSeconds(before),afterSeconds=roughSeconds(after);
        return String.format("%d → %d strokes  •  %s → %s points  •  rough plot time %s → %s",
                before.strokes(),after.strokes(),count(before.points()),count(after.points()),duration(beforeSeconds),duration(afterSeconds));
    }

    // Deliberately labelled rough: neutral desktop-plotter speeds plus pen lifts, for comparison only.
    private static double roughSeconds(StudioMetrics m){return m.drawDistance()/1000d*60+m.travelDistance()/3000d*60+m.strokes()*.15;}
    private static String duration(double seconds){long s=Math.max(0,Math.round(seconds));return s>=3600?String.format("%dh %02dm",s/3600,(s%3600)/60):String.format("%dm %02ds",s/60,s%60);}
    private static String count(int n){return n>=1000?String.format("%.1fk",n/1000d):Integer.toString(n);}
    private static JPanel labeled(String title,JComponent canvas){JPanel p=new JPanel(new BorderLayout());JLabel l=new JLabel(title,SwingConstants.CENTER);
        l.setFont(l.getFont().deriveFont(Font.BOLD));p.add(l,BorderLayout.NORTH);p.add(canvas);return p;}

    private static final class PreviewCanvas extends JComponent {
        private ProcessorOutput output;
        PreviewCanvas(){setOpaque(true);setBackground(Color.WHITE);setBorder(BorderFactory.createLineBorder(new Color(210,210,210)));}
        void setOutput(ProcessorOutput value){output=value;repaint();}
        @Override protected void paintComponent(Graphics graphics){super.paintComponent(graphics);if(output==null)return;Graphics2D g=(Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Extents x=extents(output);if(!x.valid()){g.dispose();return;}double pad=12;
            double scale=Math.min((getWidth()-2*pad)/Math.max(.001,x.maxX-x.minX),(getHeight()-2*pad)/Math.max(.001,x.maxY-x.minY));
            for(Layer layer:output.layers()){g.setColor(parse(layer.color()));for(Command command:layer.commands())if(command instanceof DrawCommand d)draw(g,d.points,x,scale,pad,getHeight());}
            g.dispose();}
        private static void draw(Graphics2D g,List<Point> pts,Extents x,double scale,double pad,int height){if(pts.size()<2)return;java.awt.geom.Path2D path=new java.awt.geom.Path2D.Double();
            Point p=pts.get(0);path.moveTo(pad+(p.x()-x.minX)*scale,height-pad-(p.y()-x.minY)*scale);for(int i=1;i<pts.size();i++){p=pts.get(i);path.lineTo(pad+(p.x()-x.minX)*scale,height-pad-(p.y()-x.minY)*scale);}g.draw(path);}
        private static Color parse(String text){if(text==null)return new Color(40,40,40);try{return Color.decode(text);}catch(NumberFormatException e){return new Color(40,40,40);}}
        private static Extents extents(ProcessorOutput out){double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
            for(Layer l:out.layers())for(Command c:l.commands())if(c instanceof DrawCommand d)for(Point p:d.points){minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());}
            return new Extents(minX,minY,maxX,maxY);}
        private record Extents(double minX,double minY,double maxX,double maxY){boolean valid(){return Double.isFinite(minX)&&Double.isFinite(minY)&&Double.isFinite(maxX)&&Double.isFinite(maxY);}}
    }
}
