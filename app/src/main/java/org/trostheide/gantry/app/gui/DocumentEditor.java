package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.session.DocumentSession;
import org.trostheide.gantry.model.Point;
import org.trostheide.gantry.model.ProcessorOutput;
import org.trostheide.gantry.model.command.DrawCommand;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Undoable command-model edits initiated from the preview canvas. */
final class DocumentEditor {
    private final DocumentSession session;
    private final Component parent;
    private final Runnable changed;
    private final Consumer<Boolean> undoAvailable;
    private final Consumer<Boolean> redoAvailable;
    private final Consumer<String> log;
    private final Set<Integer> hatchIds = new HashSet<>();
    private String pattern = "linear";
    private double gap = 2.0;
    private double angle = 45.0;

    DocumentEditor(DocumentSession session, Component parent, Runnable changed,
            Consumer<Boolean> undoAvailable, Consumer<Boolean> redoAvailable, Consumer<String> log) {
        this.session=session; this.parent=parent; this.changed=changed;
        this.undoAvailable=undoAvailable; this.redoAvailable=redoAvailable; this.log=log;
    }

    void replace(ProcessorOutput output){session.replace(output);hatchIds.clear();historyAvailability();}
    void restore(ProcessorOutput output, List<Integer> selectedLayers) {
        session.restore(output, selectedLayers);
        hatchIds.clear();
        historyAvailability();
    }
    void update(ProcessorOutput output){session.update(output);}
    void clear(){session.clear();hatchIds.clear();historyAvailability();}
    void snapshot(){session.snapshotForUndo();historyAvailability();}
    void undo(){if(session.undo()!=null){historyAvailability();changed.run();log.accept("Undo: reverted the last edit.");}}
    void redo(){if(session.redo()!=null){historyAvailability();changed.run();log.accept("Redo: restored the next edit.");}}
    void historyAvailability(){undoAvailable.accept(session.canUndo());redoAvailable.accept(session.canRedo());}

    void hatch(Path2D region,int layer){
        ProcessorOutput current=session.currentOutput(); if(current==null)return;
        List<DrawCommand> strokes=RegionHatch.hatchCommands(region,pattern,angle,gap,RegionHatch.maxCommandId(current)+1);
        if(strokes.isEmpty()){log.accept(String.format("Region too small to hatch at %.1f mm spacing.",gap));return;}
        snapshot();session.update(RegionHatch.appendToLayer(current,layer,strokes));
        for(DrawCommand stroke:strokes)hatchIds.add(stroke.id);changed.run();
        log.accept(String.format("Hatched region (%s, %.1fmm, %.0f°): +%d stroke(s) into layer %d.",pattern,gap,angle,strokes.size(),layer+1));
    }

    void delete(int id){
        ProcessorOutput current=session.currentOutput();if(current==null)return;
        RegionHatch.RemoveResult result=RegionHatch.removeCommandById(current,id);if(result.removed()==0)return;
        snapshot();session.update(result.output());hatchIds.removeAll(result.removedIds());changed.run();log.accept("Deleted 1 line.");
    }

    void addLine(double x1,double y1,double x2,double y2,int layer){
        ProcessorOutput current=session.currentOutput();if(current==null)return;
        DrawCommand line=new DrawCommand(RegionHatch.maxCommandId(current)+1,List.of(new Point(x1,y1),new Point(x2,y2)));
        ProcessorOutput next=RegionHatch.appendToLayer(current,layer,List.of(line));
        if(next==current){log.accept("Couldn't add the line (no target layer).");return;}
        snapshot();session.update(next);changed.run();log.accept(String.format("Added line (%.1f, %.1f) → (%.1f, %.1f).",x1,y1,x2,y2));
    }

    void move(int id,double[][] coordinates){
        ProcessorOutput current=session.currentOutput();if(current==null)return;
        List<Point> points=new ArrayList<>();for(double[] p:coordinates)points.add(new Point(p[0],p[1]));
        ProcessorOutput next=RegionHatch.replaceCommand(current,new DrawCommand(id,points));if(next==current)return;
        snapshot();session.update(next);changed.run();log.accept("Moved 1 line.");
    }

    void duplicate(int id){
        ProcessorOutput current=session.currentOutput();if(current==null)return;
        DrawCommand source=RegionHatch.findDrawCommand(current,id);int layer=RegionHatch.layerOfCommand(current,id);
        if(source==null||layer<0)return;List<Point> points=new ArrayList<>();
        for(Point p:source.points)points.add(new Point(p.x()+3,p.y()+3));
        snapshot();session.update(RegionHatch.appendToLayer(current,layer,List.of(new DrawCommand(RegionHatch.maxCommandId(current)+1,points))));
        changed.run();log.accept("Duplicated 1 line.");
    }

    void clearHatch(Path2D region){
        ProcessorOutput current=session.currentOutput();if(current==null)return;
        RegionHatch.RemoveResult result=RegionHatch.removeHatchInRegion(current,hatchIds,region);
        if(result.removed()==0){log.accept("No hatch fill from this session to clear in that area.");return;}
        snapshot();session.update(result.output());hatchIds.removeAll(result.removedIds());changed.run();
        log.accept(String.format("Cleared hatch: removed %d stroke(s).",result.removed()));
    }

    void chooseHatchStyle(){
        JComboBox<String> patterns=new JComboBox<>(RegionHatch.PATTERNS.toArray(new String[0]));patterns.setSelectedItem(pattern);
        JSpinner gaps=new JSpinner(new SpinnerNumberModel(gap,.2,50,.2));
        JSpinner angles=new JSpinner(new SpinnerNumberModel(angle,0,180,5));
        JPanel form=new JPanel(new GridLayout(0,2,6,6));
        form.add(new JLabel("Pattern"));form.add(patterns);form.add(new JLabel("Spacing (mm)"));form.add(gaps);form.add(new JLabel("Angle (°)"));form.add(angles);
        if(JOptionPane.showConfirmDialog(parent,form,"Hatch Region Style",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
        pattern=(String)patterns.getSelectedItem();gap=((Number)gaps.getValue()).doubleValue();angle=((Number)angles.getValue()).doubleValue();
        log.accept(String.format("Hatch style set: %s, %.1fmm, %.0f°.",pattern,gap,angle));
    }
}
