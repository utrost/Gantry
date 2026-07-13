package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.plotter.PlotterBackend;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.*;

/** Jog, pen, speed and homing controls plus soft-limited motion state. */
final class JogPanel extends JPanel {
    private static final double CONTINUOUS_MM=1.0; private static final int HOLD_MS=300;
    private final Supplier<GantryConfig> config; private final PlotJobController jobs;
    private final VisualizationPanel visualization; private final Consumer<Consumer<PlotterBackend>> backend;
    private final Consumer<String> log; private final Component parent;
    private final JSpinner step=new JSpinner(new SpinnerNumberModel(10.0,.1,1000.0,1.0));
    private final JLabel speed=new JLabel("100%"); private final List<JComponent> connectedControls=new ArrayList<>();
    private final List<JComponent> plotSafeControls=new ArrayList<>();
    private volatile double x,y; private volatile boolean known,holding;

    JogPanel(Supplier<GantryConfig> config,PlotJobController jobs,VisualizationPanel visualization,
            Consumer<Consumer<PlotterBackend>> backend,Consumer<String> log,Component parent){
        this.config=config;this.jobs=jobs;this.visualization=visualization;this.backend=backend;this.log=log;this.parent=parent;
        setLayout(new GridBagLayout());setBorder(new TitledBorder("Move pen manually"));GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(2,2,2,2);
        JButton up=jogButton("▲",0,1),down=jogButton("▼",0,-1),left=jogButton("◄",-1,0),right=jogButton("►",1,0);
        g.gridx=1;g.gridy=0;add(up,g);g.gridx=0;g.gridy=1;add(left,g);g.gridx=2;add(right,g);g.gridx=1;g.gridy=2;add(down,g);
        JButton penUp=control(new JButton("Pen Up"),true),penDown=control(new JButton("Pen Down"),true);
        penUp.addActionListener(e->backend.accept(PlotterBackend::penup));penDown.addActionListener(e->backend.accept(PlotterBackend::pendown));
        JPanel side=new JPanel();side.setLayout(new BoxLayout(side,BoxLayout.Y_AXIS));JPanel sr=row(new JLabel("Step (mm)"),step);plotSafeControls.add(step);
        side.add(sr);side.add(penUp);side.add(penDown);g.gridx=3;g.gridy=0;g.gridheight=3;add(side,g);g.gridheight=1;
        JButton minus=control(new JButton("-"),false),plus=control(new JButton("+"),false),reset=control(new JButton("Reset"),false);
        minus.addActionListener(e->backend.accept(b->b.adjustSpeed("down")));plus.addActionListener(e->backend.accept(b->b.adjustSpeed("up")));reset.addActionListener(e->backend.accept(b->b.adjustSpeed("reset")));
        g.gridx=0;g.gridy=3;g.gridwidth=4;add(row(new JLabel("Speed"),minus,speed,plus,reset),g);
        JButton home=control(new JButton("⌂ Find starting corner (Home)"),true);home.addActionListener(e->home());g.gridy=4;g.fill=GridBagConstraints.HORIZONTAL;add(home,g);
    }

    void setConnected(boolean value){for(JComponent c:connectedControls)c.setEnabled(value);}
    void setPlotting(boolean plotting){for(JComponent c:plotSafeControls)c.setEnabled(!plotting&&jobs.isConnected());}
    void setSpeed(int percent){speed.setText(percent+"%");}
    void reportPosition(double x,double y){this.x=x;this.y=y;known=true;}
    void home(){if(!jobs.isConnected()){log.accept("ERROR: Not connected.");return;}
        if(JOptionPane.showConfirmDialog(parent,"Run the homing cycle? The plotter will drive toward the limit switches at 0/0.","Home plotter",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return;
        log.accept("Homing...");backend.accept(b->{b.home();reportPosition(0,0);log.accept("Homed. Origin zeroed at (0, 0).");});}

    private JButton jogButton(String label,int dx,int dy){JButton b=control(new JButton(label),true);b.setFont(b.getFont().deriveFont(Font.BOLD,22f));b.setPreferredSize(new Dimension(54,54));wire(b,dx,dy);return b;}
    private <T extends JComponent>T control(T c,boolean plotSafe){connectedControls.add(c);if(plotSafe)plotSafeControls.add(c);c.setEnabled(false);return c;}
    private void wire(JButton button,int dx,int dy){Timer hold=new Timer(HOLD_MS,e->startContinuous(dx,dy));hold.setRepeats(false);button.addMouseListener(new MouseAdapter(){
        @Override public void mousePressed(MouseEvent e){if(button.isEnabled()&&SwingUtilities.isLeftMouseButton(e))hold.restart();}
        @Override public void mouseReleased(MouseEvent e){if(hold.isRunning()){hold.stop();jog(dx,dy);}else holding=false;}});}
    void jog(int dx,int dy){if(!jobs.isConnected())return;double[] delta=transform(dx,dy,((Number)step.getValue()).doubleValue());backend.accept(b->move(b,delta[0],delta[1]));}
    private double[] transform(int dx,int dy,double mm){PlotSettings s=config.get().toPlotSettings();double x=dx,y=dy;if(s.swapXY){double t=x;x=y;y=t;}if(s.invertX)x=-x;if(s.invertY)y=-y;return new double[]{x*mm,y*mm};}
    private boolean move(PlotterBackend b,double dx,double dy){if(config.get().softLimits&&known){double[] target=visualization.clampMotorToBed(x+dx,y+dy);dx=target[0]-x;dy=target[1]-y;}if(dx==0&&dy==0)return false;b.move(dx,dy);x+=dx;y+=dy;return true;}
    private void startContinuous(int dx,int dy){PlotterBackend b=jobs.backend();if(b==null||holding)return;holding=true;new Thread(()->{try{while(holding){double[]d=transform(dx,dy,CONTINUOUS_MM);if(!move(b,d[0],d[1]))break;}}
        catch(RuntimeException ex){log.accept("Jog stopped: "+ex.getMessage());}finally{holding=false;}},"jog-continuous").start();}
    private static JPanel row(JComponent...items){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));for(JComponent i:items)p.add(i);return p;}
}
