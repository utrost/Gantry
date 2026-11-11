package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.*;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class LayerPropertiesTest {
    @Test void inspectingALayerDoesNotChangePlotInclusionAndJobsLockEdits() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            AtomicInteger changed=new AtomicInteger(),edited=new AtomicInteger(-1);
            PlotControlsPanel panel=new PlotControlsPanel(new PlotControlsPanel.Actions(()->{},changed::incrementAndGet,b->{},edited::set));
            var layers=IntStream.range(0,40).mapToObj(i->new Layer("Long layer "+i+" name ".repeat(20),"","#00aa33",List.of())).toList();
            panel.rebuild(new ProcessorOutput(null,layers),new VisualizationPanel(),false);
            JTable table=find(panel,JTable.class);assertNotNull(table);
            table.setRowSelectionInterval(28,28);assertEquals(40,panel.selectedLayers().size());assertEquals(0,changed.get());
            JButton properties=findButton(panel,"Pen properties…");assertTrue(properties.isEnabled());properties.doClick();assertEquals(28,edited.get());
            table.setValueAt(false,28,0);assertEquals(39,panel.selectedLayers().size());assertEquals(1,changed.get());
            panel.setPlotting(true);assertFalse(table.isCellEditable(0,0));assertFalse(properties.isEnabled());
            panel.setPlotting(false);assertTrue(table.isCellEditable(0,0));assertTrue(properties.isEnabled());
        });
    }
    private static <T> T find(Container root,Class<T> type){for(Component c:root.getComponents()){if(type.isInstance(c))return type.cast(c);if(c instanceof Container n){T found=find(n,type);if(found!=null)return found;}}return null;}
    private static JButton findButton(Container root,String text){for(Component c:root.getComponents()){if(c instanceof JButton b&&text.equals(b.getText()))return b;if(c instanceof Container n){JButton b=findButton(n,text);if(b!=null)return b;}}return null;}
}
