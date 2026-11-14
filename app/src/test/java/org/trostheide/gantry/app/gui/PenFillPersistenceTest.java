package org.trostheide.gantry.app.gui;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.trostheide.gantry.svgtoolbox.*;
import org.trostheide.gantry.app.session.ProcessingRecipe;
import java.nio.file.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
class PenFillPersistenceTest {
 @TempDir Path dir;
 @Test void profileAndRecipeRoundTripWithoutLosingManualDefaults() throws Exception {
  var fill=new PenFill(.45,20,true);var profiles=new PenFillProfiles(dir.resolve("profiles.json"));profiles.save(new PenFillProfiles.Profile("Fineliner / paper",.5,fill));
  assertEquals(fill,profiles.load().get(0).fill());assertEquals(.5,profiles.load().get(0).nominalWidthMm());
  var recipe=ProcessingRecipe.fromConfig(new Config.Builder().enableHatching(true).globalStyle(new HatchStyle(30,5,"linear",0,0,0,fill)).build());
  ObjectMapper json=new ObjectMapper();assertEquals(fill,json.readValue(json.writeValueAsBytes(recipe),ProcessingRecipe.class).toConfig().globalStyle().penFill());
  var legacy=json.readValue("{\"angle\":45,\"gap\":2,\"pattern\":\"linear\"}",ProcessingRecipe.HatchRecipe.class);assertNull(legacy.penFill());
 }
 @Test void swatchesAreLabelledOrdinaryArtworkWithMultipleOverlapDensities(){
  var output=CalibrationSwatch.generate(.5,0);assertEquals(1,output.layers().size());assertTrue(output.metadata().totalCommands()>100);assertEquals(68,output.metadata().bounds().maxX());
 }
}
