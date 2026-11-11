package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.app.plot.*;
import javax.swing.*;
import java.awt.*;
import static org.junit.jupiter.api.Assertions.*;
import org.trostheide.gantry.plotter.MockPlotterBackend;

class JogPanelStateTest {
    @Test void connectionEnablesCustomStepButNeverUnlocksAnActiveJob() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            PlotJobController jobs=new PlotJobController();jobs.connect(new MockPlotterBackend(null,null));
            JogPanel panel=new JogPanel(GantryConfig::new,jobs,new VisualizationPanel(),operation->{},line->{},new JPanel(),()->{});
            JSpinner step=find(panel,JSpinner.class);JComboBox<?> presets=find(panel,JComboBox.class);
            panel.setConnected(true);assertTrue(step.isEnabled());assertTrue(presets.isEnabled());
            panel.setPlotting(true);assertFalse(step.isEnabled());panel.setConnected(true);assertFalse(step.isEnabled());assertFalse(presets.isEnabled());
            panel.setPlotting(false);assertTrue(step.isEnabled());jobs.disconnect();panel.setConnected(false);assertFalse(step.isEnabled());assertFalse(presets.isEnabled());
        });
    }
    @Test void onlyDirectionButtonsAcceptArrowKeyJogging() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            JogPanel panel=new JogPanel(GantryConfig::new,new PlotJobController(),new VisualizationPanel(),operation->{},line->{},new JPanel(),()->{});
            JButton direction=direction(panel);assertNotNull(direction);assertTrue(JogPanel.acceptsJogKeys(direction,panel));
            assertFalse(JogPanel.acceptsJogKeys(find(panel,JSpinner.class),panel));assertFalse(JogPanel.acceptsJogKeys(new JButton("↑"),panel));
        });
    }
    private static JButton direction(Container root){for(Component c:root.getComponents()){if(c instanceof JButton b&&Boolean.TRUE.equals(b.getClientProperty("jogDirection")))return b;if(c instanceof Container n){JButton b=direction(n);if(b!=null)return b;}}return null;}
    private static <T> T find(Container root,Class<T> type){for(Component c:root.getComponents()){if(type.isInstance(c))return type.cast(c);if(c instanceof Container n){T found=find(n,type);if(found!=null)return found;}}return null;}
}
