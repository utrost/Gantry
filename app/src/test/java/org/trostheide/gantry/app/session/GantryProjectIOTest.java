package org.trostheide.gantry.app.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.trostheide.gantry.model.*;
import org.trostheide.gantry.model.command.DrawCommand;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;

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
}
