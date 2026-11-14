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
    private final JTextArea status = WorkspaceStyle.note("Preparing preview…");
    private final JProgressBar progress = new JProgressBar();
    private final Timer debounce;
    private SwingWorker<Result,Void> worker;
    private int generation;
    private ProcessorOutput original;
    private final SvgImportOptions importOptions;
    private final java.awt.geom.AffineTransform placement;

    private record Result(int generation, ProcessorOutput original, ProcessorOutput processed, java.util.List<org.trostheide.gantry.svgtoolbox.patterns.PenFillGeometry.Result> fills) { }
    private boolean hasFill;
    private java.util.function.Consumer<Boolean> readiness = value -> {};
    private final JCheckBox coverageToggle=new JCheckBox("Show fill coverage",true);
    void onReady(java.util.function.Consumer<Boolean> callback){readiness=callback;callback.accept(source==null);}
    private final FillCoverageCanvas coverage=new FillCoverageCanvas();
    private final JPanel afterViews=new JPanel(new CardLayout());

    ProcessingPreviewPanel(File source, ToolboxOptionsPanel options) {
        this(source,options,SvgImportOptions.defaults(),new java.awt.geom.AffineTransform());
    }
    ProcessingPreviewPanel(File source,ToolboxOptionsPanel options,SvgImportOptions importOptions,java.awt.geom.AffineTransform placement){
        super(new BorderLayout(6,6)); this.source=source; this.options=options;this.importOptions=importOptions;this.placement=placement;
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Preview & impact"),
                BorderFactory.createEmptyBorder(5,5,5,5)));
        JPanel canvases=new JPanel(new GridLayout(1,2,6,0));
        afterViews.add(after,"paths");afterViews.add(coverage,"coverage");canvases.add(labeled("Original",before));canvases.add(labeled("With these settings",afterViews));
        coverageToggle.setToolTipText("Blue: covered; amber: gaps or narrow features; red: overshoot. Round-tip estimate.");
        coverageToggle.addActionListener(e->((CardLayout)afterViews.getLayout()).show(afterViews,hasFill&&coverageToggle.isSelected()?"coverage":"paths"));add(coverageToggle,BorderLayout.NORTH);
        add(canvases,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(6,0));progress.setIndeterminate(true);progress.setPreferredSize(new Dimension(90,10));
        footer.add(status,BorderLayout.CENTER);footer.add(progress,BorderLayout.EAST);add(footer,BorderLayout.SOUTH);
        setPreferredSize(new Dimension(480,390));
        debounce=new Timer(350,e->refreshNow());debounce.setRepeats(false);
        options.addChangeListener(this::schedule);
        if(source==null){status.setText("A preview will appear when source artwork is available.");progress.setVisible(false);}
        else schedule();
    }

    void schedule(){if(source==null)return;generation++;readiness.accept(false);coverage.setResult(null);after.setOutput(null);if(worker!=null)worker.cancel(true);debounce.restart();status.setText("Updating preview…");progress.setVisible(true);}

    private void refreshNow() {
        if(worker!=null)worker.cancel(true);
        final Config config;
        try{config=options.buildConfig();}catch(IllegalArgumentException ex){status.setText(ex.getMessage());progress.setVisible(false);return;}
        int requested=++generation;
        boolean processing=options.hasProcessingEnabled();
        worker=new SwingWorker<>(){
            @Override protected Result doInBackground() throws Exception{
                ProcessorOutput base=original;
                if(base==null)base=SvgImportStage.importSvg(source,importOptions);
                if(isCancelled())throw new CancellationException();
                var processed=processing?SvgImportStage.previewSvg(source,config,importOptions,placement):new SvgImportStage.FillPreview(base,java.util.List.of());
                return new Result(requested,base,processed.output(),processed.fills());
            }
            @Override protected void done(){
                try{Result r=get();if(r.generation()!=generation)return;original=r.original();before.setOutput(r.original());after.config=config;after.setOutput(r.processed());hasFill=!r.fills().isEmpty();coverage.setResult(hasFill?FillCoverageCanvas.combine(r.fills()):null);((CardLayout)afterViews.getLayout()).show(afterViews,hasFill&&coverageToggle.isSelected()?"coverage":"paths");readiness.accept(true);
                    status.setText(impact(r.original(),r.processed()));progress.setVisible(false);
                }catch(CancellationException ignored){}catch(Exception ex){if(requested==generation){Throwable cause=ex.getCause()==null?ex:ex.getCause();
                    status.setText("Preview unavailable: "+cause.getMessage());progress.setVisible(false);}}
            }};worker.execute();
    }

    @Override public void removeNotify(){generation++;debounce.stop();if(worker!=null)worker.cancel(true);super.removeNotify();}

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
        private Config config;
        PreviewCanvas(){setOpaque(true);setBackground(UIManager.getColor("Panel.background"));setBorder(BorderFactory.createLineBorder(new Color(210,210,210)));}
        void setOutput(ProcessorOutput value){output=value;repaint();}
        @Override protected void paintComponent(Graphics graphics){
            // A bare JComponent has no UI delegate, so super.paintComponent() does not fill its
            // background even when opaque. Clear explicitly or stale pixels from the settings form
            // behind this preview appear as a phantom overlay.
            graphics.setColor(getBackground());graphics.fillRect(0,0,getWidth(),getHeight());
            if(output==null)return;Graphics2D g=(Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Extents x=extents(output);if(!x.valid()){g.dispose();return;}double pad=12;
            double scale=Math.min((getWidth()-2*pad)/Math.max(.001,x.maxX-x.minX),(getHeight()-2*pad)/Math.max(.001,x.maxY-x.minY));
            g.translate((getWidth()-(x.maxX-x.minX)*scale)/2,(getHeight()+(x.maxY-x.minY)*scale)/2);
            g.scale(scale,-scale);g.translate(-x.minX,-x.minY);
            g.setColor(UIManager.getColor("Label.foreground"));g.setStroke(new BasicStroke((float)(1/scale)));
            for(Layer layer:output.layers())for(Command command:layer.commands())if(command instanceof DrawCommand d && d.points.size()>1){
                java.awt.geom.Path2D path=new java.awt.geom.Path2D.Double();path.moveTo(d.points.get(0).x(),d.points.get(0).y());
                for(int i=1;i<d.points.size();i++)path.lineTo(d.points.get(i).x(),d.points.get(i).y());g.draw(path);
            }
            g.dispose();}
        private static Extents extents(ProcessorOutput out){double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
            for(Layer l:out.layers())for(Command c:l.commands())if(c instanceof DrawCommand d)for(Point p:d.points){minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());}
            return new Extents(minX,minY,maxX,maxY);}
        private record Extents(double minX,double minY,double maxX,double maxY){boolean valid(){return Double.isFinite(minX)&&Double.isFinite(minY)&&Double.isFinite(maxX)&&Double.isFinite(maxY);}}
    }
}
