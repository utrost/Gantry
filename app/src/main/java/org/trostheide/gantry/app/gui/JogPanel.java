package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.plotter.PlotterBackend;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.*;

/** Manual controls own their focus and continuous-jog lifetime. */
final class JogPanel extends JPanel {
    private final Supplier<GantryConfig> config; private final PlotJobController jobs;
    private final VisualizationPanel visualization; private final Consumer<Consumer<PlotterBackend>> backend;
    private final Consumer<String> log; private final Component parent; private final Runnable changed;
    private final JSpinner step=new JSpinner(new SpinnerNumberModel(10.0,.1,1000.0,1.0));
    private final JLabel speed=new JLabel("100%"),position=new JLabel("Head position: unknown");
    private final List<JComponent> connectedControls=new ArrayList<>(),plotSafeControls=new ArrayList<>();
    private final List<Timer> holdTimers=new ArrayList<>();
    private Consumer<String> failure;
    void onFailure(Consumer<String> callback){failure=callback;}
    JLabel positionIndicator(){remove(position);return position;}
    private JButton home;
    JButton homeButton(){return home;}
    private boolean plotting;
    private volatile double x,y; private volatile boolean known,holding;
    private volatile Thread jogWorker;
    private Window window;
    private final WindowAdapter focusListener=new WindowAdapter(){@Override public void windowLostFocus(WindowEvent e){stopContinuous();}};

    JogPanel(Supplier<GantryConfig> config,PlotJobController jobs,VisualizationPanel visualization,
            Consumer<Consumer<PlotterBackend>> backend,Consumer<String> log,Component parent,Runnable changed){
        this.config=config;this.jobs=jobs;this.visualization=visualization;this.backend=backend;this.log=log;this.failure=log;this.parent=parent;this.changed=changed;
        setLayout(new BorderLayout(6,4));
        add(position,BorderLayout.NORTH);
        JButton up=jogButton("↑",0,1),down=jogButton("↓",0,-1),left=jogButton("←",-1,0),right=jogButton("→",1,0);
        JPanel pad=new JPanel(new GridLayout(3,3,2,2));
        for(JComponent cell:new JComponent[]{new JLabel(),up,new JLabel(),left,new JLabel(),right,new JLabel(),down,new JLabel()})pad.add(cell);
        JPanel manual=new JPanel(new BorderLayout(8,0));manual.add(pad,BorderLayout.WEST);
        JButton penUp=control(WorkspaceStyle.button("Pen up",()->backend.accept(PlotterBackend::penup)),true);
        JButton penDown=control(WorkspaceStyle.button("Pen down",()->backend.accept(PlotterBackend::pendown)),true);
        penUp.setMargin(new Insets(3,8,3,8));penDown.setMargin(new Insets(3,8,3,8));
        step.getAccessibleContext().setAccessibleName("Jog distance in millimetres");step.setPreferredSize(new Dimension(64,step.getPreferredSize().height));plotSafeControls.add(step);
        JComboBox<String> presets=new JComboBox<>(new String[]{"Step preset…","0.1 mm","1 mm","10 mm"});
        presets.getAccessibleContext().setAccessibleName("Jog step preset");presets.addActionListener(e->{if(presets.getSelectedIndex()>0)step.setValue(new double[]{.1,1,10}[presets.getSelectedIndex()-1]);});plotSafeControls.add(presets);
        JPanel tools=new JPanel(new GridLayout(0,1,0,2));tools.add(row(penUp,penDown));tools.add(row(new JLabel("Step (mm)"),step));tools.add(presets);manual.add(tools,BorderLayout.CENTER);add(manual,BorderLayout.CENTER);
        JButton minus=control(WorkspaceStyle.button("−",()->adjustSpeed("down")),false),plus=control(WorkspaceStyle.button("+",()->adjustSpeed("up")),false),reset=control(WorkspaceStyle.button("Reset",()->adjustSpeed("reset")),false);
        minus.getAccessibleContext().setAccessibleName("Reduce feed override");plus.getAccessibleContext().setAccessibleName("Increase feed override");
        home=control(WorkspaceStyle.button("Home machine…",this::home),true);
        for(JButton button:List.of(minus,plus,reset))button.setMargin(new Insets(3,6,3,6));
        reset.setText("↺");reset.getAccessibleContext().setAccessibleName("Reset feed override");reset.setToolTipText("Reset feed override to 100%");
        home.setText("Home…");home.setToolTipText("Home moves toward the configured limit switches");
        JPanel footer=row(new JLabel("Feed override"),minus,speed,plus,reset,home);add(footer,BorderLayout.SOUTH);
    }
    static boolean acceptsJogKeys(Component focus,JogPanel panel){return focus instanceof JButton && SwingUtilities.isDescendingFrom(focus,panel) && Boolean.TRUE.equals(((JButton)focus).getClientProperty("jogDirection"));}
    @Override public void addNotify(){super.addNotify();window=SwingUtilities.getWindowAncestor(this);if(window!=null)window.addWindowFocusListener(focusListener);}
    @Override public void removeNotify(){stopContinuous();if(window!=null)window.removeWindowFocusListener(focusListener);super.removeNotify();}
    void stopContinuous(){holding=false;for(Timer timer:holdTimers)timer.stop();if(jogWorker!=null)jobs.cancelPlot();}
    void setConnected(boolean value){if(!value){known=false;stopContinuous();position.setText("Head position: unknown");}for(JComponent c:connectedControls)c.setEnabled(value&&(!plotting||!plotSafeControls.contains(c)));for(JComponent c:plotSafeControls)c.setEnabled(value&&!plotting);}
    void setPlotting(boolean plotting){this.plotting=plotting;if(plotting)stopContinuous();for(JComponent c:plotSafeControls)c.setEnabled(!plotting&&jobs.isConnected());}
    void setSpeed(int percent){speed.setText(percent+"%");}
    void reportPosition(double x,double y){this.x=x;this.y=y;known=true;SwingUtilities.invokeLater(()->position.setText(String.format(java.util.Locale.ROOT,"Head X %.1f · Y %.1f mm",x,y)));}
    void home(){if(!jobs.isConnected()||jobs.isMachineBusy()){log.accept("Connect an idle plotter before homing.");return;}
        if(JOptionPane.showConfirmDialog(parent,"Home moves the plotter toward its configured limit switches. Clear the bed before continuing.","Home machine",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return;
        backend.accept(b->{b.home();reportPosition(0,0);});}
    private void adjustSpeed(String value){new Thread(()->{try{jobs.withBackend(b->b.adjustSpeed(value));}catch(RuntimeException e){log.accept("Feed override failed: "+e.getMessage());}},"feed-override").start();}
    private JButton jogButton(String label,int dx,int dy){JButton b=control(new JButton(label),true);b.putClientProperty("jogDirection",true);b.getAccessibleContext().setAccessibleName("Jog "+(dx<0?"left":dx>0?"right":dy>0?"up":"down"));b.setMargin(new Insets(0,0,0,0));b.setPreferredSize(new Dimension(34,30));
        boolean[] continuous={false};Timer timer=new Timer(300,e->{continuous[0]=true;startContinuous(dx,dy);});timer.setRepeats(false);holdTimers.add(timer);
        b.addActionListener(e->{timer.stop();if(!continuous[0])jog(dx,dy);continuous[0]=false;});
        b.addMouseListener(new MouseAdapter(){public void mousePressed(MouseEvent e){if(b.isEnabled()&&SwingUtilities.isLeftMouseButton(e)){continuous[0]=false;timer.restart();}}public void mouseReleased(MouseEvent e){timer.stop();stopContinuous();}public void mouseExited(MouseEvent e){timer.stop();stopContinuous();}});
        b.addFocusListener(new FocusAdapter(){public void focusLost(FocusEvent e){stopContinuous();}});return b;}
    private <T extends JComponent>T control(T c,boolean plotSafe){connectedControls.add(c);if(plotSafe)plotSafeControls.add(c);c.setEnabled(false);return c;}
    void jog(int dx,int dy){if(!jobs.isConnected()||jobs.isMachineBusy())return;double[]delta=transform(dx,dy,((Number)step.getValue()).doubleValue());backend.accept(b->move(b,delta[0],delta[1]));}
    private double[] transform(int dx,int dy,double mm){PlotSettings s=config.get().toPlotSettings();double x=dx,y=dy;if(s.swapXY){double t=x;x=y;y=t;}if(s.invertX)x=-x;if(s.invertY)y=-y;return new double[]{x*mm,y*mm};}
    private boolean move(PlotterBackend b,double dx,double dy){if(config.get().softLimits&&known){double[]target=visualization.clampMotorToBed(x+dx,y+dy);dx=target[0]-x;dy=target[1]-y;}if(dx==0&&dy==0)return false;b.move(dx,dy);x+=dx;y+=dy;return true;}
    private void startContinuous(int dx,int dy){
        if(holding || !isShowing() || !jobs.isConnected() || jobs.isMachineBusy())return;
        holding=true;
        try {
            jogWorker=jobs.startOperation("Manual jog",b->{
                while(holding && !Thread.currentThread().isInterrupted()) {
                    double[]d=transform(dx,dy,1);if(!move(b,d[0],d[1]))break;
                }
                // Releasing a held jog also halts any motion queued by the controller.
                throw new java.util.concurrent.CancellationException("Jog released");
            },(ok,failure)->SwingUtilities.invokeLater(()->{
                holding=false;jogWorker=null;
                if(failure!=null && !(failure instanceof java.util.concurrent.CancellationException))this.failure.accept("Jog recovery failed: "+failure.getMessage());
                changed.run();
            }));
            changed.run();
        }catch(RuntimeException failure){holding=false;log.accept("Jog unavailable: "+failure.getMessage());}
    }
    private static JPanel row(JComponent...items){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));for(JComponent i:items)p.add(i);return p;}
}
