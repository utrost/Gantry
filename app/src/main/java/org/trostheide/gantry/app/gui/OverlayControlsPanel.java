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
        void stationMoved(String name,double x,double y); void stationAdded(double x,double y); void overlayChanged(); void log(String line);
    }
    private final VisualizationPanel visualization; private final Actions actions;
    private final JSpinner x=new JSpinner(new SpinnerNumberModel(0.0,-2000.0,2000.0,1.0));
    private final JSpinner y=new JSpinner(new SpinnerNumberModel(0.0,-2000.0,2000.0,1.0));
    private final JSpinner width=new JSpinner(new SpinnerNumberModel(1.0,0.1,2000.0,1.0));
    private final JSpinner height=new JSpinner(new SpinnerNumberModel(1.0,0.1,2000.0,1.0));
    private boolean refreshing;
    private final List<JComponent> controls;
    OverlayControlsPanel(VisualizationPanel visualization,Actions actions){this.visualization=visualization;this.actions=actions;
        setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));setBorder(BorderFactory.createEmptyBorder(8,0,8,0));
        JButton reset=new JButton("Reset Position"),rotate=new JButton("Rotate 90°"),mirror=new JButton("Mirror"),set=new JButton("Set");
        compactCoordinateSpinner(x);compactCoordinateSpinner(y);
        reset.addActionListener(e->visualization.resetOverlay());rotate.addActionListener(e->visualization.rotateOverlay());mirror.addActionListener(e->visualization.toggleMirror());set.addActionListener(e->apply());
        JPanel buttons=row(reset,rotate,mirror);add(WorkspaceStyle.heading("Artwork placement"));add(buttons);add(row(new JLabel("Artwork X"),x,new JLabel("mm")));add(row(new JLabel("Artwork Y"),y,new JLabel("mm"),set));
        add(WorkspaceStyle.heading("Artwork size"));
        compactCoordinateSpinner(width);compactCoordinateSpinner(height);
        add(row(new JLabel("Width"),width,new JLabel("mm")));add(row(new JLabel("Height"),height,new JLabel("mm")));
        JCheckBox lock=new JCheckBox("Lock aspect ratio",true);lock.setToolTipText("Width and height remain proportional to preserve the drawing");lock.setAlignmentX(LEFT_ALIGNMENT);add(lock);
        width.addChangeListener(e->{if(!refreshing)visualization.resizeArtwork(((Number)width.getValue()).doubleValue(),((Number)height.getValue()).doubleValue(),true,lock.isSelected());});
        height.addChangeListener(e->{if(!refreshing)visualization.resizeArtwork(((Number)width.getValue()).doubleValue(),((Number)height.getValue()).doubleValue(),false,lock.isSelected());});
        controls=List.of(reset,rotate,mirror,x,y,set,width,height,lock);wirePreview();
    }
    void setPlotting(boolean plotting){for(JComponent c:controls)c.setEnabled(!plotting);}
    void refreshPosition(){refreshing=true;try{double[]p=visualization.getContentMotorMin();x.setValue(Math.round(p[0]*10)/10.0);y.setValue(Math.round(p[1]*10)/10.0);
        double[]size=visualization.getContentMotorSize();width.setValue(Math.max(.1,size[0]));height.setValue(Math.max(.1,size[1]));}finally{refreshing=false;}}
    private void apply(){if(!actions.hasDocument()){actions.log("ERROR: Load or import commands first.");return;}visualization.setContentMotorMin(((Number)x.getValue()).doubleValue(),((Number)y.getValue()).doubleValue());}
    private void wirePreview(){visualization.setOverlayChangeListener(()->{refreshPosition();actions.overlayChanged();});visualization.setRemoveDrawingListener(actions::remove);
        visualization.setRegionHatchListener(new VisualizationPanel.RegionHatchListener(){public void onHatchRegion(Path2D r,int l){actions.hatch(r,l);}public void onClearHatchRegion(Path2D r){actions.clearHatch(r);}});
        visualization.setHatchStyleAction(actions::hatchStyle);visualization.setStrokeEditListener(new VisualizationPanel.StrokeEditListener(){
            public void onDeleteStroke(int id){actions.delete(id);}public void onAddLine(double x1,double y1,double x2,double y2,int l){actions.add(x1,y1,x2,y2,l);}
            public void onMoveStroke(int id,double[][]p){actions.move(id,p);}public void onDuplicateStroke(int id){actions.duplicate(id);}});
        visualization.setInteractionModeChangeListener(actions::mode);visualization.setStationEditListener(new VisualizationPanel.StationEditListener(){
            public void onStationMoved(String n,double x,double y){actions.stationMoved(n,x,y);}public void onStationAdded(double x,double y){actions.stationAdded(x,y);}});}
    private static JPanel row(JComponent...items){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,4,2));for(JComponent i:items)p.add(i);p.setAlignmentX(LEFT_ALIGNMENT);p.setMaximumSize(new Dimension(Integer.MAX_VALUE,p.getPreferredSize().height));return p;}
    /** Keep both coordinates and the Set button inside the control column at the current UI scale. */
    private static void compactCoordinateSpinner(JSpinner spinner){
        Dimension preferred=spinner.getPreferredSize();
        Dimension compact=new Dimension(com.formdev.flatlaf.util.UIScale.scale(72),preferred.height);
        spinner.setPreferredSize(compact);spinner.setMinimumSize(compact);
    }
}
