package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PenFillSpacingTest {
    @Test void overlapExamplesAndInvalidValues(){
        for(int i=0;i<4;i++)assertEquals(.5-.05*i,new org.trostheide.gantry.svgtoolbox.PenFill(.5,i*10,true).spacingMm(),1e-12);
        for(double width:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->new org.trostheide.gantry.svgtoolbox.PenFill(width,10,true).spacingMm());
        for(double overlap:new double[]{-1,100,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->new org.trostheide.gantry.svgtoolbox.PenFill(.5,overlap,true).spacingMm());
    }
    @Test void denseWorkIsRejectedBeforeGeneration(){
        Path2D region=new Path2D.Double(new Rectangle2D.Double(0,0,600,800));
        assertThrows(IllegalArgumentException.class,()->RegionHatch.checkWorkload(region,.00001));
        assertDoesNotThrow(()->RegionHatch.checkWorkload(region,.4));
    }
}
