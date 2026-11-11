package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RawCommandAvailabilityTest {
    @Test void enterCannotSubmitWhileBusyAndDisconnectDoesNotRestoreControls() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            AtomicInteger calls=new AtomicInteger();
            RawCommandPanel panel=new RawCommandPanel(action->calls.incrementAndGet(),line->{});
            JTextField input=null;JButton send=null;
            for(var c:panel.getComponents()){if(c instanceof JTextField f)input=f;if(c instanceof JButton b)send=b;}
            assertNotNull(input);assertNotNull(send);assertFalse(send.isEnabled());assertTrue(send.isVisible());
            panel.setConnected(true);assertTrue(send.isEnabled());
            input.setText("G0 X10");panel.setUnavailable("Machine moving");
            assertFalse(send.isEnabled());assertEquals("Machine moving",send.getToolTipText());
            input.postActionEvent();assertEquals(0,calls.get());assertEquals("G0 X10",input.getText());
            panel.setConnected(false);panel.setUnavailable(null);assertFalse(send.isEnabled());
            panel.setConnected(true);assertTrue(send.isEnabled());assertTrue(input.isEnabled());
        });
    }
}
