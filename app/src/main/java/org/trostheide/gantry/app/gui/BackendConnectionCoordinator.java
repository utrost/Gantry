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
        void machineState(GcodeBackend.MachineState state);
        void log(String line); void stopPlot(); void refreshGuidance();
    }
    private final PlotJobController jobs; private final Component parent; private final Listener listener;
    /** Guards toolbar, menu, preflight, and setup-wizard connection entry points. */
    private boolean connecting;
    BackendConnectionCoordinator(PlotJobController jobs,Component parent,Listener listener){this.jobs=jobs;this.parent=parent;this.listener=listener;}
    synchronized void toggle(GantryConfig config,boolean plotting){
        if(connecting){listener.log("Connection attempt already in progress.");return;}
        if(!jobs.isConnected()){PlotterBackend candidate=config.mock?new MockPlotterBackend(config.gcode,listener::log):new GcodeBackend(config.gcode);
            if(candidate instanceof GcodeBackend g){g.setPositionCallback(listener::position);g.setSpeedCallback(listener::speed);g.setSentCommandCallback(listener::sent);g.setStateCallback(listener::machineState);}
            connecting=true;listener.connectionState(true,false,false);
            new Thread(()->{boolean ok=false;try{ok=jobs.connect(candidate);}catch(RuntimeException failure){
                listener.log("ERROR: Connection failed: "+failure.getMessage());candidate.disconnect();}
                boolean connected=ok;SwingUtilities.invokeLater(()->{synchronized(BackendConnectionCoordinator.this){connecting=false;}
                    listener.connectionState(false,connected,!connected);listener.log(connected?"Connected.":"ERROR: Connection failed.");listener.refreshGuidance();});},"backend-connect").start();
        }else{
            if(plotting&&JOptionPane.showConfirmDialog(parent,"A plot is still running. Stop it and disconnect?","Plot in progress",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION)return;
            if(plotting)listener.stopPlot();listener.connectionState(false,false,false);new Thread(jobs::disconnect,"backend-disconnect").start();listener.refreshGuidance();
        }
    }
}
