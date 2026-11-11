package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ArtworkWorkspacePanelTest {
    @Test void browseUsesSharedEntryPointAndCloseSurvivesRefresh() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            AtomicInteger opened=new AtomicInteger();
            ArtworkWorkspacePanel panel=new ArtworkWorkspacePanel(new JPanel(),anchor->opened.incrementAndGet());
            assertTrue(panel.isShowingEmptyState());find(panel,"Browse…").doClick();assertEquals(1,opened.get());
            find(panel,"Close").doClick();assertFalse(panel.isShowingEmptyState());
            panel.setHasArtwork(false);assertFalse(panel.isShowingEmptyState());
            panel.setHasArtwork(true);panel.setHasArtwork(false);assertFalse(panel.isShowingEmptyState());
            assertTrue(new ArtworkWorkspacePanel(new JPanel(),anchor->{}).isShowingEmptyState());
        });
    }
    @Test void escapeDismissesOnlyTheFocusedHint() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            ArtworkWorkspacePanel panel=new ArtworkWorkspacePanel(new JPanel(),anchor->{});
            JComponent card=(JComponent)find(panel,"Close").getParent().getParent();
            Object key=card.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke("ESCAPE"));
            assertNotNull(key);card.getActionMap().get(key).actionPerformed(new ActionEvent(card,0,""));
            assertFalse(panel.isShowingEmptyState());
        });
    }
    private static JButton find(Container root,String text){for(Component child:root.getComponents()){if(child instanceof JButton b&&text.equals(b.getText()))return b;if(child instanceof Container c){JButton b=find(c,text);if(b!=null)return b;}}return null;}
}
