package org.trostheide.gantry.app.gui;

/** Read-only presentation, independent from Swing and from the machine execution implementation. */
final class WorkspacePresentation {
    enum Phase { EMPTY, PREPARE, CONNECTING, CHECKING, READY, RUNNING, WAITING, PAUSING, PAUSED, STOPPING, STOPPED, FAILED, COMPLETE }
    enum Action { ADD, CONNECT, CHECK, START, CONTINUE, PAUSE, RESUME, NONE }
    record Snapshot(boolean artwork, boolean connected, boolean connecting, boolean simulation,
                    boolean active, boolean waiting, boolean paused, boolean pausing, boolean stopping, boolean ready,
                    boolean checksRequired, boolean hasSelection, Phase outcome, String controller) { }
    record View(Phase phase, String title, String hint, String button, Action action, boolean enabled, boolean stop) { }
    static View of(Snapshot s) {
        if (s.stopping) return v(Phase.STOPPING,"Stopping job","Waiting for halt and pen-lift recovery.","Stopping…",Action.NONE,false,s.active);
        if (s.active && s.pausing) return v(Phase.PAUSING,"Changing submission state","Waiting for pause/resume acknowledgement.","Please wait…",Action.NONE,false,true);
        if (s.active && s.waiting) return v(Phase.WAITING,"Waiting for pen","Fit the requested pen, then continue.","Pen ready — continue",Action.CONTINUE,true,true);
        if (s.active && s.paused) return v(Phase.PAUSED,"Command submission paused","Controller motion is reported separately above.","Resume plotting",Action.RESUME,true,true);
        if (s.active) return v(Phase.RUNNING,"Job in progress","Progress counts submitted commands, not measured physical completion.","Pause",Action.PAUSE,true,true);
        if (s.connecting) return v(Phase.CONNECTING,"Connecting","Waiting for the machine connection.","Connecting…",Action.NONE,false,false);
        if (!s.artwork) return v(Phase.EMPTY,"Start with your artwork","Add a drawing or practise with the sample in Help.","Add artwork",Action.ADD,true,false);
        if (!s.connected) return v(Phase.PREPARE,"Prepare your drawing","Review size, placement and pen layers before connecting.","Connect plotter",Action.CONNECT,true,false);
        if (s.outcome == Phase.FAILED) return v(Phase.FAILED,"Job failed","Review the error and machine state before checking again.","Review checks",Action.CHECK,true,false);
        if (s.outcome == Phase.STOPPED) return v(Phase.STOPPED,"Job stopped","Review placement and machine state before restarting.","Review checks",Action.CHECK,true,false);
        if (s.outcome == Phase.COMPLETE) return v(Phase.COMPLETE,"Job submitted successfully","Check the controller and output before removing the paper.","Plot another copy",Action.CHECK,true,false);
        if (!s.hasSelection) return v(Phase.PREPARE,"Choose a pen layer","Select at least one layer in Prepare.","Start plotting",Action.START,false,false);
        boolean machineReady = s.simulation || "IDLE".equals(s.controller);
        if (!machineReady) return v(Phase.CHECKING,"Check machine state","Wait for an idle controller. Unknown, alarm and moving states cannot start a job.","Check before plotting",Action.CHECK,true,false);
        if (s.checksRequired && !s.ready) return v(Phase.CHECKING,"Check before plotting","Review automatic checks and confirm the physical setup.","Check before plotting",Action.CHECK,true,false);
        return v(Phase.READY,"Ready to start",s.simulation ? "Simulation — no physical machine is connected." : "Starting will send movement commands to the plotter.","Start plotting",Action.START,true,false);
    }
    private static View v(Phase p,String t,String h,String b,Action a,boolean e,boolean stop){return new View(p,t,h,b,a,e,stop);}
}
