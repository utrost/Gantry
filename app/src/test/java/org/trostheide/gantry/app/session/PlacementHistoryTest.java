package org.trostheide.gantry.app.session;
import org.junit.jupiter.api.Test;
import org.trostheide.gantry.model.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class PlacementHistoryTest {
 @Test void placementUndoAndRedoPreserveOutputAndSelection(){
  DocumentSession s=new DocumentSession();
  ProcessorOutput output=new ProcessorOutput(new Metadata("test",java.time.Instant.now(),"","mm",0,new Bounds(0,0,10,10)),List.of(new Layer("pen","","#123456",List.of())));
  s.replace(output);s.markSaved();var before=s.placement();
  var after=new GantryProject.Placement(12,34,2,90,true,false);
  s.changePlacement(after);assertTrue(s.isDirty());assertTrue(s.canUndo());
  s.undo();assertEquals(before,s.placement());assertEquals(output,s.currentOutput());
  s.redo();assertEquals(after,s.placement());assertEquals(List.of(0),s.selectedLayerIndices());
  s.changePlacement(after);s.undo();assertEquals(before,s.placement());
 }
}
