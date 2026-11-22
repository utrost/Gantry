package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.session.CompositionArtwork;
import org.trostheide.gantry.app.session.DocumentSession;
import org.trostheide.gantry.model.Bounds;

/** Physical group edits preserve the affine placement of every unaffected artwork. */
final class PhysicalArtworkPlacement {
    record Edit(double x,double y,double width,double height,boolean widthChanged) {}
    static double[] bounds(VisualizationPanel view,Bounds bounds){
        double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
        for(double x:new double[]{bounds.minX(),bounds.maxX()})for(double y:new double[]{bounds.minY(),bounds.maxY()}){
            double[] point=view.transformPointToMotor(new VisualizationPanel.Point2D(x,y));
            minX=Math.min(minX,point[0]);minY=Math.min(minY,point[1]);maxX=Math.max(maxX,point[0]);maxY=Math.max(maxY,point[1]);
        }
        return new double[]{minX,minY,maxX-minX,maxY-minY};
    }
    static boolean apply(DocumentSession session,VisualizationPanel view,CompositionArtwork artwork,Edit edit){
        double[] current=bounds(view,artwork.bounds());
        double ratio=edit.widthChanged()?edit.width()/Math.max(.000001,current[2]):edit.height()/Math.max(.000001,current[3]);
        if(!Double.isFinite(ratio) || ratio<=0)throw new IllegalArgumentException("Enter a positive physical size.");
        var old=artwork.transform();double scale=old.scale()*ratio;
        Bounds source=artwork.originalBounds();
        Bounds candidate=new Bounds(old.x(),old.y(),old.x()+(source.maxX()-source.minX())*scale,old.y()+(source.maxY()-source.minY())*scale);
        double[] next=bounds(view,candidate);
        double requestedX=edit.x()-(view.getPlacementAnchor()%3)*next[2]/2;
        double requestedY=edit.y()-(view.getPlacementAnchor()/3)*next[3]/2;
        if(Math.abs(ratio-1)>.000001&&(requestedX<0||requestedY<0||requestedX+next[2]>view.machineWidth||requestedY+next[3]>view.machineHeight))
            throw new IllegalArgumentException("Resize would move the selected reference outside the bed; reposition or choose a smaller size.");
        double targetX=Math.max(0,Math.min(requestedX,Math.max(0,view.machineWidth-next[2])));
        double targetY=Math.max(0,Math.min(requestedY,Math.max(0,view.machineHeight-next[3])));
        double dx=targetX-next[0],dy=targetY-next[1];
        double[] origin=view.transformPointToMotor(new VisualizationPanel.Point2D(0,0));
        double[] x=view.transformPointToMotor(new VisualizationPanel.Point2D(1,0));
        double[] y=view.transformPointToMotor(new VisualizationPanel.Point2D(0,1));
        double a=x[0]-origin[0],b=y[0]-origin[0],c=x[1]-origin[1],d=y[1]-origin[1],det=a*d-b*c;
        if(Math.abs(det)<1e-12)throw new IllegalArgumentException("Artwork transform cannot be inverted.");
        var transform=new CompositionArtwork.Transform(old.x()+(dx*d-dy*b)/det,old.y()+(a*dy-c*dx)/det,scale,old.mirror());
        if(transform.equals(old))return targetX!=requestedX || targetY!=requestedY;
        session.setPlacement(view.placement());
        session.transformArtwork(artwork.id(),transform);
        view.loadPathsPreservingOverlay(session.currentOutput());
        double[] shifted=view.transformPointToMotor(new VisualizationPanel.Point2D(0,0));
        view.applyMotorShift(origin[0]-shifted[0],origin[1]-shifted[1]);
        session.setPlacement(view.placement());
        return Math.abs(targetX-requestedX)>.001 || Math.abs(targetY-requestedY)>.001;
    }
}
