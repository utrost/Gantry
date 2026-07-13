package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.plotter.*;
import javax.swing.*;
import java.awt.*;

/** Asynchronous backend connect/disconnect orchestration without window widgets. */
final class BackendConnectionCoordinator {
    interface Listener {
        void connectionState(boolean connecting, boolean connected, boolean failed);
        void position(double x,double y); void speed(int percent); void sent(String line);
        void log(String line); void stopPlot(); void refreshGuidance();
    }
    private final PlotJobController jobs; private final Component parent; private final Listener listener;
    BackendConnectionCoordinator(PlotJobController jobs,Component parent,Listener listener){this.jobs=jobs;this.parent=parent;this.listener=listener;}
    void toggle(GantryConfig config,boolean plotting){
        if(!jobs.isConnected()){PlotterBackend candidate=config.mock?new MockPlotterBackend(config.gcode,listener::log):new GcodeBackend(config.gcode);
            if(candidate instanceof GcodeBackend g){g.setPositionCallback(listener::position);g.setSpeedCallback(listener::speed);g.setSentCommandCallback(listener::sent);}
            listener.connectionState(true,false,false);
            new Thread(()->{boolean ok=jobs.connect(candidate);SwingUtilities.invokeLater(()->{listener.connectionState(false,ok,!ok);listener.log(ok?"Connected.":"ERROR: Connection failed.");listener.refreshGuidance();});},"backend-connect").start();
        }else{
            if(plotting&&JOptionPane.showConfirmDialog(parent,"A plot is still running. Stop it and disconnect?","Plot in progress",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION)return;
            if(plotting)listener.stopPlot();listener.connectionState(false,false,false);new Thread(jobs::disconnect,"backend-disconnect").start();listener.refreshGuidance();
        }
    }
}
