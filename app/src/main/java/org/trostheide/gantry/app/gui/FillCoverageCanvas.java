package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.svgtoolbox.patterns.PenFillGeometry;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/** Round-tip geometry preview, not a prediction of ink flow. */
final class FillCoverageCanvas extends JComponent {
    private PenFillGeometry.Result result;
    private boolean coverage=true;
    FillCoverageCanvas(){setPreferredSize(new Dimension(420,320));setMinimumSize(new Dimension(200,150));getAccessibleContext();}
    static PenFillGeometry.Result combine(java.util.List<PenFillGeometry.Result> results){
        Area target=new Area(),covered=new Area();java.util.List<Shape> paths=new java.util.ArrayList<>();
        for(var r:results){target.add(r.target());covered.add(r.covered());paths.addAll(r.paths());}
        Area missing=new Area(target);missing.subtract(covered);Area outside=new Area(covered);outside.subtract(target);
        return new PenFillGeometry.Result(java.util.List.copyOf(paths),target,covered,missing,outside,!missing.isEmpty());
    }
    void setResult(PenFillGeometry.Result value){result=value;repaint();}
    void setCoverage(boolean value){coverage=value;repaint();}
    @Override protected void paintComponent(Graphics graphics){
        Graphics2D g=(Graphics2D)graphics.create();
        g.setColor(UIManager.getColor("Panel.background"));g.fillRect(0,0,getWidth(),getHeight());
        if(result==null){g.dispose();return;}
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        Rectangle2D b=result.target().getBounds2D();b=b.createUnion(result.covered().getBounds2D());double scale=Math.min((getWidth()-32)/Math.max(.001,b.getWidth()),(getHeight()-32)/Math.max(.001,b.getHeight()));
        g.translate((getWidth()-b.getWidth()*scale)/2,(getHeight()+b.getHeight()*scale)/2);g.scale(scale,-scale);g.translate(-b.getX(),-b.getY());
        g.setColor(new Color(145,150,160,80));g.fill(result.target());
        if(coverage){g.setColor(new Color(70,140,230));g.fill(result.covered());g.setColor(new Color(235,174,67));g.fill(result.uncovered());g.setColor(new Color(215,65,90));g.fill(result.overshoot());}
        else{g.setStroke(new BasicStroke((float)(1/scale)));g.setColor(UIManager.getColor("Label.foreground"));for(Shape p:result.paths())g.draw(p);}
        g.setStroke(new BasicStroke((float)(1/scale)));g.setColor(UIManager.getColor("Label.foreground"));g.draw(result.target());g.dispose();
    }
}
