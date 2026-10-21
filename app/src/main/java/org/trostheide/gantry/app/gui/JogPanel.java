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
    private volatile double x,y; private volatile boolean known,holding;
    private volatile Thread jogWorker;
    private Window window;
    private final WindowAdapter focusListener=new WindowAdapter(){@Override public void windowLostFocus(WindowEvent e){stopContinuous();}};

    JogPanel(Supplier<GantryConfig> config,PlotJobController jobs,VisualizationPanel visualization,
            Consumer<Consumer<PlotterBackend>> backend,Consumer<String> log,Component parent,Runnable changed){
        this.config=config;this.jobs=jobs;this.visualization=visualization;this.backend=backend;this.log=log;this.parent=parent;this.changed=changed;
        setLayout(new GridBagLayout());GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(3,3,3,3);
        g.gridx=0;g.gridy=0;g.gridwidth=3;add(position,g);g.gridwidth=1;
        JButton up=jogButton("↑",0,1),down=jogButton("↓",0,-1),left=jogButton("←",-1,0),right=jogButton("→",1,0);
        g.gridx=1;g.gridy=1;add(up,g);g.gridx=0;g.gridy=2;add(left,g);g.gridx=2;add(right,g);g.gridx=1;g.gridy=3;add(down,g);
        JButton penUp=control(new JButton("Pen Up"),true),penDown=control(new JButton("Pen Down"),true);
        penUp.addActionListener(e->backend.accept(PlotterBackend::penup));penDown.addActionListener(e->backend.accept(PlotterBackend::pendown));
        g.gridx=0;g.gridy=4;g.gridwidth=3;add(row(penUp,penDown),g);
        step.getAccessibleContext().setAccessibleName("Jog distance in millimetres");step.setPreferredSize(new Dimension(80,step.getPreferredSize().height));plotSafeControls.add(step);
        g.gridy=5;add(row(new JLabel("Step (mm)"),step),g);
        JPanel presets=row();for(double value:new double[]{.1,1,10}){JButton b=new JButton(String.valueOf(value));b.addActionListener(e->step.setValue(value));presets.add(b);}g.gridy=6;add(presets,g);
        JButton minus=control(new JButton("−"),false),plus=control(new JButton("+"),false),reset=control(new JButton("Reset"),false);
        minus.addActionListener(e->adjustSpeed("down"));plus.addActionListener(e->adjustSpeed("up"));reset.addActionListener(e->adjustSpeed("reset"));
        g.gridy=7;add(row(new JLabel("Feed override"),minus,speed,plus,reset),g);
        JButton home=control(new JButton("Home machine…"),true);home.addActionListener(e->home());g.gridy=8;g.fill=GridBagConstraints.HORIZONTAL;add(home,g);
    }
    static boolean acceptsJogKeys(Component focus,JogPanel panel){return focus instanceof JButton && SwingUtilities.isDescendingFrom(focus,panel) && Boolean.TRUE.equals(((JButton)focus).getClientProperty("jogDirection"));}
    @Override public void addNotify(){super.addNotify();window=SwingUtilities.getWindowAncestor(this);if(window!=null)window.addWindowFocusListener(focusListener);}
    @Override public void removeNotify(){stopContinuous();if(window!=null)window.removeWindowFocusListener(focusListener);super.removeNotify();}
    void stopContinuous(){holding=false;for(Timer timer:holdTimers)timer.stop();Thread worker=jogWorker;if(worker!=null)worker.interrupt();}
    void setConnected(boolean value){if(!value){known=false;stopContinuous();position.setText("Head position: unknown");}for(JComponent c:connectedControls)c.setEnabled(value);}
    void setPlotting(boolean plotting){if(plotting)stopContinuous();for(JComponent c:plotSafeControls)c.setEnabled(!plotting&&jobs.isConnected());}
    void setSpeed(int percent){speed.setText(percent+"%");}
    void reportPosition(double x,double y){this.x=x;this.y=y;known=true;SwingUtilities.invokeLater(()->position.setText(String.format(java.util.Locale.ROOT,"Head X %.1f · Y %.1f mm",x,y)));}
    void home(){if(!jobs.isConnected()||jobs.isMachineBusy()){log.accept("Connect an idle plotter before homing.");return;}
        if(JOptionPane.showConfirmDialog(parent,"Home moves the plotter toward its configured limit switches. Clear the bed before continuing.","Home machine",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return;
        backend.accept(b->{b.home();reportPosition(0,0);});}
    private void adjustSpeed(String value){new Thread(()->{try{jobs.withBackend(b->b.adjustSpeed(value));}catch(RuntimeException e){log.accept("Feed override failed: "+e.getMessage());}},"feed-override").start();}
    private JButton jogButton(String label,int dx,int dy){JButton b=control(new JButton(label),true);b.putClientProperty("jogDirection",true);b.getAccessibleContext().setAccessibleName("Jog "+(dx<0?"left":dx>0?"right":dy>0?"up":"down"));b.setPreferredSize(new Dimension(44,40));
        boolean[] continuous={false};Timer timer=new Timer(300,e->{continuous[0]=true;startContinuous(dx,dy);});timer.setRepeats(false);holdTimers.add(timer);
        b.addActionListener(e->{timer.stop();if(!continuous[0])jog(dx,dy);continuous[0]=false;});
        b.addMouseListener(new MouseAdapter(){public void mousePressed(MouseEvent e){if(b.isEnabled()&&SwingUtilities.isLeftMouseButton(e)){continuous[0]=false;timer.restart();}}public void mouseReleased(MouseEvent e){timer.stop();stopContinuous();}public void mouseExited(MouseEvent e){timer.stop();stopContinuous();}});
        b.addFocusListener(new FocusAdapter(){public void focusLost(FocusEvent e){stopContinuous();}});return b;}
    private <T extends JComponent>T control(T c,boolean plotSafe){connectedControls.add(c);if(plotSafe)plotSafeControls.add(c);c.setEnabled(false);return c;}
    void jog(int dx,int dy){if(!jobs.isConnected()||jobs.isMachineBusy())return;double[]delta=transform(dx,dy,((Number)step.getValue()).doubleValue());backend.accept(b->move(b,delta[0],delta[1]));}
    private double[] transform(int dx,int dy,double mm){PlotSettings s=config.get().toPlotSettings();double x=dx,y=dy;if(s.swapXY){double t=x;x=y;y=t;}if(s.invertX)x=-x;if(s.invertY)y=-y;return new double[]{x*mm,y*mm};}
    private boolean move(PlotterBackend b,double dx,double dy){if(config.get().softLimits&&known){double[]target=visualization.clampMotorToBed(x+dx,y+dy);dx=target[0]-x;dy=target[1]-y;}if(dx==0&&dy==0)return false;b.move(dx,dy);x+=dx;y+=dy;return true;}
    private void startContinuous(int dx,int dy){PlotterBackend b=jobs.backend();if(b==null||holding||!isShowing()||!jobs.tryBeginExclusiveOperation("manual jog"))return;holding=true;changed.run();
        jogWorker=new Thread(()->{try{while(holding&&!Thread.currentThread().isInterrupted()){double[]d=transform(dx,dy,1);if(!move(b,d[0],d[1]))break;}}
            catch(RuntimeException ex){log.accept("Jog stopped: "+ex.getMessage());}finally{boolean interrupted=Thread.interrupted();try{b.haltMotion();}catch(RuntimeException e){log.accept("Jog halt failed: "+e.getMessage());}finally{holding=false;jogWorker=null;jobs.finishExclusiveOperation();SwingUtilities.invokeLater(changed);if(interrupted)Thread.currentThread().interrupt();}}},"jog-continuous");jogWorker.start();}
    private static JPanel row(JComponent...items){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));for(JComponent i:items)p.add(i);return p;}
}
