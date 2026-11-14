package org.trostheide.gantry.svgtoolbox.patterns;
import org.junit.jupiter.api.Test;
import org.trostheide.gantry.svgtoolbox.PenFill;
import java.awt.geom.*;
import static org.junit.jupiter.api.Assertions.*;
class PenFillGeometryTest {
 @Test void insetContainsRoundTipAndPreservesHole(){
  Area region=new Area(new Rectangle2D.Double(0,0,20,15));region.subtract(new Area(new Rectangle2D.Double(6,5,5,5)));
  var r=PenFillGeometry.preview(region,0,new PenFill(.5,20,true));assertFalse(r.paths().isEmpty());assertTrue(r.overshoot().isEmpty());
  Area inHole=new Area(r.covered());inHole.intersect(new Area(new Rectangle2D.Double(6,5,5,5)));assertTrue(inHole.isEmpty());assertFalse(r.uncovered().isEmpty());
 }
 @Test void curvedExteriorAndHoleStayInside(){
  Area ring=new Area(new Ellipse2D.Double(0,0,20,16));ring.subtract(new Area(new Ellipse2D.Double(5,4,8,6)));
  for(double angle:new double[]{0,37,90})assertTrue(PenFillGeometry.preview(ring,angle,new PenFill(.5,20,true)).overshoot().isEmpty(),"curved boundary at "+angle);
 }
 @Test void narrowFeaturesAndLegacyOvershoot(){
  var r=PenFillGeometry.preview(new Rectangle2D.Double(0,0,.2,10),0,new PenFill(.5,20,true));assertTrue(r.paths().isEmpty());assertTrue(r.lostFeatures());
  assertFalse(PenFillGeometry.preview(new Rectangle2D.Double(0,0,10,10),0,new PenFill(.5,20,false)).overshoot().isEmpty());
 }
 @Test void concaveIslandsAndCancellation(){
  Area area=new Area(new Rectangle2D.Double(0,0,10,10));area.subtract(new Area(new Rectangle2D.Double(4,4,6,6)));area.add(new Area(new Rectangle2D.Double(15,0,3,3)));
  assertTrue(PenFillGeometry.preview(area,45,new PenFill(.4,10,true)).overshoot().isEmpty());
  try{Thread.currentThread().interrupt();assertThrows(java.util.concurrent.CancellationException.class,()->PenFillGeometry.paths(area,0,new PenFill(.4,10,true)));}finally{Thread.interrupted();}
 }
}
