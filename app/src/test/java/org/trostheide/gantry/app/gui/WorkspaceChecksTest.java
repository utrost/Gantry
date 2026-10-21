package org.trostheide.gantry.app.gui;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WorkspaceChecksTest {
 @Test void physicalConfirmationCannotOutliveInvalidationOrAutomaticFailure() throws Exception {
  SwingUtilities.invokeAndWait(()->{
   WorkspaceChecks p=new WorkspaceChecks(()->{},()->{},()->{},()->{});
   p.update(true,true,true,true,false,"100 × 100 mm");assertFalse(p.ready());
   for(var c:p.getComponents())if(c instanceof JCheckBox b)b.doClick();assertTrue(p.ready());
   p.update(true,true,true,false,false,"");assertFalse(p.ready());
   p.clearConfirmations();p.update(true,true,true,true,false,"");assertFalse(p.ready());
  });
 }
}
