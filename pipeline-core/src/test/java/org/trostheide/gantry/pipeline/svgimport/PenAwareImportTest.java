package org.trostheide.gantry.pipeline.svgimport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.trostheide.gantry.svgtoolbox.*;
import org.trostheide.gantry.model.command.DrawCommand;
import java.nio.file.*;
import java.awt.geom.AffineTransform;
import static org.junit.jupiter.api.Assertions.*;
class PenAwareImportTest {
 @TempDir Path dir;
 @Test void svgWithoutViewBoxUsesCssPixelsInPhysicalMode()throws Exception{
  var file=dir.resolve("pixels.svg");Files.writeString(file,"<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96'><rect width='96' height='96' fill='#000000'/></svg>");
  var config=new Config.Builder().enableHatching(true).globalStyle(new HatchStyle(0,5,"linear",0,0,0,new PenFill(.5,20,true))).build();
  var preview=SvgImportStage.previewSvg(file.toFile(),config,SvgImportOptions.defaults(),new AffineTransform());
  assertEquals(25.4,preview.fills().get(0).target().getBounds2D().getWidth(),.00001);
  assertTrue(preview.output().metadata().bounds().maxX()<=25.4);
 }
 @Test void tinySvgUnitsRetainPhysicalPrecision()throws Exception{
  var file=dir.resolve("tiny.svg");Files.writeString(file,"<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 .01 .01'><rect width='.01' height='.01' fill='#000000'/></svg>");
  var config=new Config.Builder().enableHatching(true).globalStyle(new HatchStyle(0,5,"linear",0,0,0,new PenFill(.5,20,true))).build();
  var preview=SvgImportStage.previewSvg(file.toFile(),config,new SvgImportOptions(0,"pen",.001,20,20,false,0,0,false,true),new AffineTransform());
  var lines=preview.output().layers().stream().flatMap(l->l.commands().stream()).filter(c->c instanceof DrawCommand).map(c->(DrawCommand)c).toList();
  var paths=preview.fills().get(0).paths();assertEquals(paths.size(),lines.size());
  for(int i=0;i<lines.size();i++){var bounds=paths.get(i).getBounds2D();var points=lines.get(i).points;
   assertEquals(bounds.getMinX(),points.stream().mapToDouble(p->p.x()).min().orElseThrow(),.00001);
   assertEquals(bounds.getMinY(),points.get(0).y(),.00001);
  }
 }
 @Test void nativeMillimetresAndPerColourRecipesMatchPreview() throws Exception {
  var file=dir.resolve("native.svg");Files.writeString(file,"<svg xmlns='http://www.w3.org/2000/svg' width='40mm' height='20mm' viewBox='0 0 400 200'><rect width='180' height='200' fill='#000000'/><rect x='220' width='180' height='200' fill='#ff0000'/></svg>");
  var black=new HatchStyle(0,5,"linear",0,0,0,new PenFill(.5,20,true));
  var red=new HatchStyle(0,5,"linear",0,0,0,new PenFill(1,10,true));
  var config=new Config.Builder().enableHatching(true).globalStyle(black).overrides(java.util.Map.of("#ff0000",red)).build();
  var opts=SvgImportOptions.defaults();
  var preview=SvgImportStage.previewSvg(file.toFile(),config,opts,new AffineTransform());
  var actual=SvgImportStage.importSvg(file.toFile(),config,opts);
  assertEquals(2,preview.fills().size());assertTrue(preview.fills().stream().allMatch(f->f.overshoot().isEmpty()));
  assertEquals(preview.output().layers().stream().mapToInt(l->l.commands().size()).sum(),actual.layers().stream().mapToInt(l->l.commands().size()).sum());
  assertTrue(actual.metadata().bounds().maxX()<=40.01);
  for(var layer:actual.layers()){
   var ys=layer.commands().stream().filter(c->c instanceof DrawCommand).map(c->((DrawCommand)c).points.get(0).y()).sorted().distinct().toList();
   assertEquals(layer.color().equals("#ff0000")?.9:.4,ys.get(1)-ys.get(0),.001);
  }
 }
 @Test void physicalSpacingSurvivesSvgUnitsStretchAndPlacement() throws Exception {
  var file=dir.resolve("shape.svg");Files.writeString(file,"<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><rect x='0' y='0' width='100' height='100' fill='#000000'/></svg>");
  var style=new HatchStyle(0,5,"linear",0,0,0,new PenFill(.5,20,true));var config=new Config.Builder().enableHatching(true).globalStyle(style).build();
  var opts=new SvgImportOptions(0,"pen",.01,20,10,false,5,8,false,true);
  var result=SvgImportStage.importSvg(file.toFile(),config,opts,AffineTransform.getScaleInstance(2,3));
  var lines=result.layers().stream().flatMap(l->l.commands().stream()).filter(c->c instanceof DrawCommand).map(c->(DrawCommand)c).toList();
  assertTrue(lines.size()>10);
  var ys=lines.stream().map(d->d.points.get(0).y()).sorted().distinct().toList();
  assertEquals(.4,(ys.get(1)-ys.get(0))*3,.001);
  assertTrue(result.metadata().bounds().minX()>=5);assertTrue(result.metadata().bounds().maxX()<=25);
 }
}
