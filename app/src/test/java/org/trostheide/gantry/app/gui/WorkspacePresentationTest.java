package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.trostheide.gantry.app.gui.WorkspacePresentation.*;

class WorkspacePresentationTest {
    private Snapshot state(boolean active,boolean waiting,boolean paused,boolean pausing,boolean stopping,boolean ready,boolean policy,boolean mock,String controller,Phase outcome){
        return new Snapshot(true,true,false,mock,active,waiting,paused,pausing,stopping,ready,policy,true,outcome,controller);
    }
    @Test void unknownAlarmAndMovingControllersCannotStartEvenWithoutMandatoryChecklist(){
        for(String controller:new String[]{"UNKNOWN","ALARM","RUN","HOLD","DISCONNECTED"}){
            var view=of(state(false,false,false,false,false,true,false,false,controller,null));
            assertNotEquals(Action.START,view.action(),controller);
        }
    }
    @Test void mandatoryChecksRemainRequiredForSimulation(){
        assertEquals(Phase.CHECKING,of(state(false,false,false,false,false,false,true,true,"UNKNOWN",null)).phase());
        assertEquals(Phase.READY,of(state(false,false,false,false,false,true,true,true,"UNKNOWN",null)).phase());
    }
    @Test void everyActiveTransitionKeepsStopAvailable(){
        Snapshot[] states={state(true,false,false,false,false,true,true,false,"RUN",null),
            state(true,true,false,false,false,false,true,false,"IDLE",null),
            state(true,false,true,false,false,false,true,false,"HOLD",null),
            state(true,false,false,true,false,false,true,false,"RUN",null),
            state(true,true,true,true,true,false,true,false,"RUN",null)};
        Phase[] phases={Phase.RUNNING,Phase.WAITING,Phase.PAUSED,Phase.PAUSING,Phase.STOPPING};
        for(int i=0;i<states.length;i++){var view=of(states[i]);assertEquals(phases[i],view.phase());assertTrue(view.stop());}
    }
    @Test void finishedJobsRequireExplicitReviewBeforeRepeating(){
        for(Phase outcome:new Phase[]{Phase.COMPLETE,Phase.STOPPED,Phase.FAILED}){
            var view=of(state(false,false,false,false,false,true,true,false,"IDLE",outcome));
            assertEquals(outcome,view.phase());assertEquals(Action.CHECK,view.action());assertFalse(view.stop());
        }
    }
}
