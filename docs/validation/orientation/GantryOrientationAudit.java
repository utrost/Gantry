import org.trostheide.gantry.app.plot.*;
import org.trostheide.gantry.app.session.*;
import org.trostheide.gantry.model.*;
public class GantryOrientationAudit {
 public static void main(String[] args) {
  var text=QuickArtworkGenerator.generate(new QuickArtwork(QuickArtwork.Kind.TEXT,"F",0,7));
  for(String origin:new String[]{"Top-Left","Top-Right","Bottom-Left","Bottom-Right"}){
   var config=new GantryConfig(); config.mock=true; config.machineOrigin=origin;
   config.gcode.machineWidth=210; config.gcode.machineHeight=148;
   var settings=config.toPlotSettings();
   settings.alignmentOffsetOverride=new double[]{0,0};
   var stroke=new PlotService(null,settings).projectArtwork(text).layers().get(0).strokes().get(0);
   var a=stroke.get(0);var b=stroke.get(1);var c=stroke.get(2);
   var sa=CoordinateTransform.physicalToScreen(a.x(),a.y(),settings.swapXY,settings.originRight,settings.originBottom,210,148);
   var sb=CoordinateTransform.physicalToScreen(b.x(),b.y(),settings.swapXY,settings.originRight,settings.originBottom,210,148);
   var sc=CoordinateTransform.physicalToScreen(c.x(),c.y(),settings.swapXY,settings.originRight,settings.originBottom,210,148);
   System.out.printf("%s: workspace F-up dy=%.0f, F-right dx=%.0f; SVG-page F-up dy=%.0f, F-right dx=%.0f%n",origin,sb[1]-sa[1],sc[0]-sb[0],-(b.y()-a.y()),c.x()-b.x());
  }
 }
}
