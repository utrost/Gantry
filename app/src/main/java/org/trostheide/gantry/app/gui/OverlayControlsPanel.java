package org.trostheide.gantry.app.gui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.function.*;

/** Drawing-position controls and preview interaction wiring. */
final class OverlayControlsPanel extends JPanel {
    interface Actions {
        boolean hasDocument(); void remove(); void hatch(Path2D region,int layer); void clearHatch(Path2D region);
        void hatchStyle(); void delete(int id); void add(double x1,double y1,double x2,double y2,int layer);
        void move(int id,double[][] points); void duplicate(int id); void mode(VisualizationPanel.InteractionMode mode);
        void stationMoved(String name,double x,double y); void stationAdded(double x,double y); void log(String line);
    }
    private final VisualizationPanel visualization; private final Actions actions;
    private final JSpinner x=new JSpinner(new SpinnerNumberModel(0.0,-2000.0,2000.0,1.0));
    private final JSpinner y=new JSpinner(new SpinnerNumberModel(0.0,-2000.0,2000.0,1.0));
    private final List<JComponent> controls;
    OverlayControlsPanel(VisualizationPanel visualization,Actions actions){this.visualization=visualization;this.actions=actions;
        setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));setBorder(new TitledBorder("Overlay / Position"));
        JButton reset=new JButton("Reset Position"),rotate=new JButton("Rotate 90°"),mirror=new JButton("Mirror"),set=new JButton("Set");
        reset.addActionListener(e->visualization.resetOverlay());rotate.addActionListener(e->visualization.rotateOverlay());mirror.addActionListener(e->visualization.toggleMirror());set.addActionListener(e->apply());
        JPanel buttons=row(reset,rotate,mirror),position=row(new JLabel("X"),x,new JLabel("Y"),y,new JLabel("mm"),set);add(buttons);add(position);
        controls=List.of(reset,rotate,mirror,x,y,set);wirePreview();
    }
    void setPlotting(boolean plotting){for(JComponent c:controls)c.setEnabled(!plotting);}
    void refreshPosition(){double[]p=visualization.getContentMotorMin();x.setValue(Math.round(p[0]*10)/10.0);y.setValue(Math.round(p[1]*10)/10.0);}
    private void apply(){if(!actions.hasDocument()){actions.log("ERROR: Load or import commands first.");return;}visualization.setContentMotorMin(((Number)x.getValue()).doubleValue(),((Number)y.getValue()).doubleValue());}
    private void wirePreview(){visualization.setOverlayChangeListener(this::refreshPosition);visualization.setRemoveDrawingListener(actions::remove);
        visualization.setRegionHatchListener(new VisualizationPanel.RegionHatchListener(){public void onHatchRegion(Path2D r,int l){actions.hatch(r,l);}public void onClearHatchRegion(Path2D r){actions.clearHatch(r);}});
        visualization.setHatchStyleAction(actions::hatchStyle);visualization.setStrokeEditListener(new VisualizationPanel.StrokeEditListener(){
            public void onDeleteStroke(int id){actions.delete(id);}public void onAddLine(double x1,double y1,double x2,double y2,int l){actions.add(x1,y1,x2,y2,l);}
            public void onMoveStroke(int id,double[][]p){actions.move(id,p);}public void onDuplicateStroke(int id){actions.duplicate(id);}});
        visualization.setInteractionModeChangeListener(actions::mode);visualization.setStationEditListener(new VisualizationPanel.StationEditListener(){
            public void onStationMoved(String n,double x,double y){actions.stationMoved(n,x,y);}public void onStationAdded(double x,double y){actions.stationAdded(x,y);}});}
    private static JPanel row(JComponent...items){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,4,2));for(JComponent i:items)p.add(i);return p;}
}
