package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.app.session.DocumentSession;
import org.trostheide.gantry.app.session.GantryProject;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.command.DrawCommand;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalArtworkPlacementTest {
    @Test void groupPhysicalEditsPreserveOtherArtworkAcrossAxisAndOverlayTransforms(){
        for(int flags=0;flags<16;flags++)for(int rotation:new int[]{0,90,180,270}){
            DocumentSession session=new DocumentSession();session.replace(artwork("first"));session.appendArtwork(artwork("second"),"second");
            VisualizationPanel view=new VisualizationPanel();view.setMachineSize(300,200);view.setCanvasAlignment("Center");
            view.setEffectiveAxes((flags&1)!=0,(flags&2)!=0,(flags&4)!=0);view.setFlipY((flags&8)!=0);view.setDataRotation(rotation);
            view.loadFromOutput(session.currentOutput());view.applyPlacement(new GantryProject.Placement(3,4,1.2,90,true,false));
            var first=session.artworks().get(0);var second=session.artworks().get(1);
            double[] beforeOther=PhysicalArtworkPlacement.bounds(view,second.bounds());
            var beforePlacement=view.placement();var beforeOutput=session.currentOutput();
            double[] original=PhysicalArtworkPlacement.bounds(view,first.bounds());
            PhysicalArtworkPlacement.apply(session,view,first,new PhysicalArtworkPlacement.Edit(40,50,original[2]*1.25,original[3],true));
            double[] applied=PhysicalArtworkPlacement.bounds(view,session.artworks().get(0).bounds());
            assertEquals(40,applied[0],1e-7);assertEquals(50,applied[1],1e-7);assertEquals(original[2]*1.25,applied[2],1e-7);
            assertArrayEquals(beforeOther,PhysicalArtworkPlacement.bounds(view,session.artworks().get(1).bounds()),1e-7);
            session.undo();assertEquals(beforeOutput,session.currentOutput());assertEquals(beforePlacement,session.placement());
        }
    }
    private ProcessorOutput artwork(String name){
        return new ProcessorOutput(new Metadata(name,Instant.EPOCH,"pen","mm",1,new Bounds(0,0,20,10)),
                List.of(new Layer(name,"pen",List.of(new DrawCommand(1,List.of(new Point(0,0),new Point(20,10)))))));
    }
}
