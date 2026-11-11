package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.PlotJobHistory;
import org.trostheide.gantry.app.plot.PlotSettings;
import javax.swing.*;
import java.awt.*;

/** Read-only stored-job preview; never replaces or dirties the editable document. */
final class HistoricalJobPreview extends JPanel {
    HistoricalJobPreview(PlotJobHistory.Job job,Runnable back){
        super(new BorderLayout(6,6));PlotSettings s=job.settings();
        VisualizationPanel canvas=new VisualizationPanel();canvas.editingEnabled=false;
        canvas.setMachineSize(s.resolveMachineWidth(),s.resolveMachineHeight());
        canvas.setMachineOrigin((s.originBottom?"Bottom":"Top")+"-"+(s.originRight?"Right":"Left"));
        canvas.setEffectiveAxes(s.swapXY,s.invertX,s.invertY);canvas.setDataRotation(s.dataRotation);
        canvas.setFlipY(s.flipY);canvas.setPadding(s.paddingX,s.paddingY);canvas.setCanvasAlignment(s.canvasAlign);
        canvas.loadFromOutput(job.output());
        if(s.alignmentOffsetOverride!=null){canvas.alignOffsetX=s.alignmentOffsetOverride[0];canvas.alignOffsetY=s.alignmentOffsetOverride[1];}
        JPanel header=new JPanel(new BorderLayout());header.add(WorkspaceStyle.heading("Stored job · "+job.timestamp()),BorderLayout.CENTER);
        header.add(WorkspaceStyle.button("Return to artwork",back),BorderLayout.EAST);
        ArtworkWorkspacePanel view=new ArtworkWorkspacePanel(canvas,c->{});view.setHasArtwork(true);
        add(header,BorderLayout.NORTH);add(view,BorderLayout.CENTER);
        add(WorkspaceStyle.note("Read-only job preview. Your current artwork is preserved. Repeat uses these stored paths and transform settings."),BorderLayout.SOUTH);
    }
    static boolean compatible(PlotSettings stored,PlotSettings current){
        return Math.abs(stored.resolveMachineWidth()-current.resolveMachineWidth())<.001
                && Math.abs(stored.resolveMachineHeight()-current.resolveMachineHeight())<.001
                && stored.swapXY==current.swapXY && stored.invertX==current.invertX && stored.invertY==current.invertY
                && stored.originRight==current.originRight && stored.originBottom==current.originBottom && stored.flipY==current.flipY;
    }
}
