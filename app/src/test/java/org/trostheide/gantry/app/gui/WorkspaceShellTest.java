package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.app.plot.GantryConfig;
import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceShellTest {
    @Test void manualMovementKeepsCanvasSpaceStableAndStopAvailable() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            GantryConfig config=new GantryConfig();
            WorkspaceShell shell=new WorkspaceShell(new JPanel(),new JPanel(),new JPanel(),new JPanel(),new JPanel(),new JPanel(),new JPanel(),new JButton(),()->{},()->{},()->config,()->{},()->{});
            shell.setProgressComponent(new JProgressBar());
            var idle=new WorkspacePresentation.View(WorkspacePresentation.Phase.PREPARE,"Ready","","Start",WorkspacePresentation.Action.START,true,false);
            shell.render(idle,true);
            shell.setSize(1024,800);shell.doLayout();
            int height=shell.split.getHeight();
            for(int i=0;i<5;i++){
                shell.render(new WorkspacePresentation.View(WorkspacePresentation.Phase.MANUAL,"Manual jog","","Busy",WorkspacePresentation.Action.NONE,false,true),true);
                shell.doLayout();assertEquals(height,shell.split.getHeight());assertTrue(shell.stop.isEnabled());
                shell.render(new WorkspacePresentation.View(WorkspacePresentation.Phase.STOPPING,"Stopping","","Busy",WorkspacePresentation.Action.NONE,false,true),true);
                shell.doLayout();assertEquals(height,shell.split.getHeight());assertTrue(shell.stop.isEnabled());
                shell.render(idle,true);shell.doLayout();assertEquals(height,shell.split.getHeight());assertFalse(shell.stop.isEnabled());
            }
        });
    }
    @Test void machineNavigationStopsJoggingAndPinSurvivesJobStart() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            GantryConfig config=new GantryConfig();AtomicInteger stopped=new AtomicInteger();
            WorkspaceShell shell=new WorkspaceShell(new JPanel(),new JPanel(),new JPanel(),new JPanel(),new JPanel(),new JPanel(),new JPanel(),new JButton(),()->{},()->{},()->config,()->{},stopped::incrementAndGet);
            assertFalse(shell.isMachineOpen());shell.machineToggle.doClick();assertTrue(shell.isMachineOpen());
            var running=new WorkspacePresentation.View(WorkspacePresentation.Phase.RUNNING,"Running","","Pause",WorkspacePresentation.Action.PAUSE,true,true);
            shell.render(running,true);assertTrue(shell.isMachineOpen());assertTrue(shell.stop.isVisible());
            shell.render(new WorkspacePresentation.View(WorkspacePresentation.Phase.PREPARE,"","","",WorkspacePresentation.Action.NONE,false,false),true);
            assertTrue(shell.stop.isVisible());assertFalse(shell.stop.isEnabled());
            shell.pin.doClick();assertTrue(config.machinePanelPinned);
            shell.render(running,true);assertTrue(shell.isMachineOpen());
            int calls=stopped.get();shell.showView("Check");assertTrue(stopped.get()>calls);
            shell.setMachineOpen(false);assertFalse(shell.isMachineOpen());
        });
    }
}
