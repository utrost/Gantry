package org.trostheide.gantry.svgtoolbox.patterns;

import org.trostheide.gantry.svgtoolbox.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;

/** One physical geometry source for generation and coverage preview. Round-tip approximation. */
public final class PenFillGeometry {
    private PenFillGeometry() { }
    public record Result(List<Shape> paths, Area target, Area covered, Area uncovered, Area overshoot, boolean lostFeatures) { }
    public static List<Shape> paths(Shape target,double angle,PenFill pen) {
        check(target,pen.spacingMm());
        Path2D boundary=new Path2D.Double();
        boundary.append(target.getPathIterator(null,.0002),false);
        Area allowed=new Area(boundary);
        if(pen.insideBoundary()) {
            // Erode the boundary on both exterior and hole contours. A small tolerance guards flattening.
            allowed.subtract(new Area(new BasicStroke((float)(pen.widthMm()+.002),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)
                    .createStrokedShape(allowed)));
        }
        if(allowed.isEmpty())return List.of();
        Path2D precise = new Path2D.Double();
        precise.append(allowed.getPathIterator(null, .0002), false);
        check(precise, pen.spacingMm());
        return new LinearHatchPattern().generate(precise,new Config.Builder().build(),HatchStyle.of(angle,pen.spacingMm()));
    }
    public static Result preview(Shape target,double angle,PenFill pen) {
        List<Shape> lines=paths(target,angle,pen);
        if(lines.size()>3000)throw new IllegalArgumentException("Coverage preview is too dense. Reduce overlap or preview a smaller area.");
        Area covered=new Area();BasicStroke stroke=new BasicStroke((float)pen.widthMm(),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND);
        for(Shape line:lines){cancelled();covered.add(new Area(stroke.createStrokedShape(line)));}
        Area original=new Area(target),uncovered=new Area(original),outside=new Area(covered);
        uncovered.subtract(covered);outside.subtract(original);
        return new Result(List.copyOf(lines),original,covered,uncovered,outside,pen.insideBoundary()&&(!uncovered.isEmpty()));
    }
    public static void check(Shape region,double gap) {
        if(!Double.isFinite(gap)||gap<=0)throw new IllegalArgumentException("Invalid fill spacing.");
        Rectangle2D bounds=region.getBounds2D();double rows=Math.hypot(bounds.getWidth(),bounds.getHeight())/gap;
        int edges=0;PathIterator it=region.getPathIterator(null,.05);
        while(!it.isDone()){cancelled();edges++;if(edges>100000)throw new IllegalArgumentException("Fill boundary is too complex. Simplify the source artwork first.");it.next();}
        if(!Double.isFinite(rows)||rows>10000||rows*edges>2000000)
            throw new IllegalArgumentException("Fill is too dense or complex. Reduce overlap or fill a smaller area.");
    }
    public static void cancelled(){if(Thread.currentThread().isInterrupted())throw new CancellationException("Fill cancelled");}
}
