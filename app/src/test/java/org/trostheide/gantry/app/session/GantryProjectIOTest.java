package org.trostheide.gantry.app.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;
import org.trostheide.gantry.svgtoolbox.Config;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GantryProjectIOTest {
    @TempDir Path tmp;
    @Test void roundTripsEditableSessionState() throws Exception {
        ProcessorOutput output=new ProcessorOutput(new Metadata("source",Instant.EPOCH,"station","mm",1,Bounds.empty()),
                List.of(new Layer("ink","station",List.of(new DrawCommand(1,List.of(new Point(1,2),new Point(3,4)))))));
        GantryProject expected=new GantryProject(1,output,List.of(0),new GantryProject.Placement(12,8,1.5,90,true,true),3,
                new GantryProject.Source("art.svg",SvgImportOptions.defaults(),"art.png",List.of("--strategy","centerline")));
        var file=tmp.resolve("drawing.gantry").toFile();GantryProjectIO.save(expected,file);
        GantryProject loaded=GantryProjectIO.load(file);
        assertEquals(expected.placement(),loaded.placement());assertEquals(expected.source(),loaded.source());assertEquals(3,loaded.passes());
        assertEquals(expected.output().metadata(),loaded.output().metadata());assertEquals(1,loaded.output().layers().get(0).commands().size());
    }

    @Test void roundTripsExactProcessingRecipe() throws Exception {
        ProcessorOutput output=new ProcessorOutput(new Metadata("source",Instant.EPOCH,"station","mm",1,Bounds.empty()),List.of());
        Config config=new Config.Builder().inputPath("").outputPath("").enableHatching(true).hatchPattern("cross")
                .linesort(true).reloop(true).handdrawn(true).handdrawnMagnitude(3.5).handdrawnSeed(42).build();
        ProcessingRecipe recipe=ProcessingRecipe.fromConfig(config);
        GantryProject project=new GantryProject(1,output,List.of(),GantryProject.Placement.identity(),1,
                new GantryProject.Source("art.svg",SvgImportOptions.defaults(),null,List.of(),recipe));
        var file=tmp.resolve("recipe.gantry").toFile();GantryProjectIO.save(project,file);
        ProcessingRecipe loaded=GantryProjectIO.load(file).source().processingRecipe();
        assertEquals(recipe,loaded);
        assertEquals(3.5,loaded.toConfig().handdrawnMagnitude());
        assertEquals(42,loaded.toConfig().handdrawnSeed());
    }
}
