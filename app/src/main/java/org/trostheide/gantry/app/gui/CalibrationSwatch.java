package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.session.DocumentSession;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.command.*;
import org.trostheide.gantry.svgtoolbox.PenFill;
import org.trostheide.gantry.svgtoolbox.patterns.PenFillGeometry;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Calibration is ordinary artwork: creating it never opens a backend or starts plotting. */
final class CalibrationSwatch {
    static ProcessorOutput generate(double width,double offsetX) {
        new PenFill(width,0,true);List<Shape> paths=new ArrayList<>();
        for(int i=0;i<4;i++) {
            double x=offsetX+i*18;
            paths.addAll(PenFillGeometry.paths(new Rectangle2D.Double(x,0,14,14),0,new PenFill(width,i*10,true)));
            Font font=new Font(Font.SANS_SERIF,Font.PLAIN,3);var glyph=font.createGlyphVector(new java.awt.font.FontRenderContext(null,true,true),i*10+"%");
            // Outlined labels remain legible in the Y-up document coordinate system.
            AffineTransform label=new AffineTransform(1,0,0,-1,x,18);paths.add(label.createTransformedShape(glyph.getOutline()));
            paths.add(new Line2D.Double(x,23,x+12,23));
        }
        var commands=RegionHatch.commands(paths,new AffineTransform(),1);
        List<Command> list=new ArrayList<>(commands);
        return new ProcessorOutput(new Metadata("Pen-paper calibration "+width+" mm",Instant.now(),"default_station","mm",list.size(),new Bounds(offsetX,0,offsetX+68,26)),
                List.of(new Layer("Calibration: 0%, 10%, 20%, 30% overlap","default_station","#333333",list)));
    }
    static ProcessorOutput transform(ProcessorOutput output,AffineTransform transform){
        List<Command> commands=new ArrayList<>();double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(Command c:output.layers().get(0).commands())if(c instanceof DrawCommand d){List<org.trostheide.gantry.model.Point> points=new ArrayList<>();for(var p:d.points){var point=transform.transform(new Point2D.Double(p.x(),p.y()),null);points.add(new org.trostheide.gantry.model.Point(point.getX(),point.getY()));minX=Math.min(minX,point.getX());minY=Math.min(minY,point.getY());maxX=Math.max(maxX,point.getX());maxY=Math.max(maxY,point.getY());}commands.add(new DrawCommand(d.id,points));}
        var m=output.metadata();return new ProcessorOutput(new Metadata(m.source(),m.generatedAt(),m.stationId(),m.units(),commands.size(),new Bounds(minX,minY,maxX,maxY)),List.of(output.layers().get(0).withCommands(commands)));
    }
    static void show(Component parent,DocumentSession session,java.util.function.Supplier<AffineTransform> physicalTransform,Runnable history,Runnable changed,DocumentEditor.Feedback feedback) {
        double width=session.penWidthsMm().isEmpty()?.3:session.penWidthsMm().get(0);
        PenFillOptionsPanel options=new PenFillOptionsPanel(width,10);
        JPanel form=new JPanel(new BorderLayout(8,8));form.add(options,BorderLayout.NORTH);
        form.add(WorkspaceStyle.note("Creates four labelled 14 mm squares at 0/10/20/30% overlap and stroke samples.\nSheet: 68 × 26 mm. Inspect the plotted paper, then save the preferred effective width/overlap as a pen/paper profile here.\nCreation only adds artwork. Review placement and use the normal Check / Start workflow."),BorderLayout.CENTER);
        while(JOptionPane.showConfirmDialog(parent,form,"Pen/paper calibration — create artwork",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){
            try{options.commit();double x=0;
                ProcessorOutput sample=generate(options.width(),x);
                // Full generated geometry is reviewed before adding it to the document.
                VisualizationPanel preview=new VisualizationPanel();preview.setPreferredSize(new Dimension(600,350));preview.setMachineSize(80,32);preview.setMachineOrigin("Bottom-Left");preview.setEffectiveAxes(false,false,false);preview.setCanvasAlignment("Center");preview.editingEnabled=false;preview.loadFromOutput(sample);preview.setPenWidthsMm(List.of(options.width()));
                StudioMetrics metrics=StudioMetrics.of(sample);
                long seconds=Math.round(metrics.drawDistance()/1000*60+metrics.travelDistance()/3000*60+metrics.strokes()*.15);
                JPanel review=new JPanel(new BorderLayout());review.add(preview);review.add(WorkspaceStyle.note("68 × 26 mm · "+sample.metadata().totalCommands()+" strokes · roughly "+(seconds/60)+"m "+(seconds%60)+"s at 1000 mm/min drawing · uses "+options.width()+" mm effective width.\nAdded beside existing artwork; check bed fit and pen before plotting."),BorderLayout.SOUTH);
                if(JOptionPane.showConfirmDialog(parent,review,"Review calibration artwork",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
                if(session.currentOutput()==null)session.replace(new ProcessorOutput(new Metadata("Empty drawing",Instant.now(),"default_station","mm",0,new Bounds(0,0,0,0)),List.of()));
                AffineTransform placement=physicalTransform.get();AffineTransform linear=new AffineTransform(placement.getScaleX(),placement.getShearY(),placement.getShearX(),placement.getScaleY(),0,0);
                sample=transform(sample,linear.createInverse());
                session.appendArtwork(sample,"Pen/paper calibration",null,null,null);
                List<Double> widths=new ArrayList<>(session.penWidthsMm());widths.set(widths.size()-1,options.width());session.setPenWidthsMm(widths);
                history.run();changed.run();feedback.edited("Calibration artwork added. Review placement, then Check and Start when ready.");return;
            }catch(Exception ex){JOptionPane.showMessageDialog(parent,ex.getMessage(),"Calibration",JOptionPane.ERROR_MESSAGE);}
        }
    }
}
