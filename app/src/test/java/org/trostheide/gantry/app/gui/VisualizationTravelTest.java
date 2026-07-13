package org.trostheide.gantry.app.gui;
import org.junit.jupiter.api.Test;import org.trostheide.gantry.model.*;import org.trostheide.gantry.model.command.*;import java.time.Instant;import java.util.List;import static org.junit.jupiter.api.Assertions.assertEquals;
class VisualizationTravelTest {@Test void efficiencyIncludesPenUpMoveBetweenLayers(){VisualizationPanel panel=new VisualizationPanel();ProcessorOutput out=new ProcessorOutput(new Metadata("x",Instant.EPOCH,"s","mm",4,Bounds.empty()),List.of(
        new Layer("a","s",List.of(new MoveCommand(1,0,0),new DrawCommand(2,List.of(new Point(0,0),new Point(10,0))))),
        new Layer("b","s",List.of(new MoveCommand(3,20,0),new DrawCommand(4,List.of(new Point(20,0),new Point(25,0)))))));
    panel.loadFromOutput(out);assertEquals(15,panel.travelPenDownMm,1e-9);assertEquals(25,panel.travelTotalMm,1e-9);}}
