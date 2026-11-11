package org.trostheide.gantry.app.gui;

import com.formdev.flatlaf.util.UIScale;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

/** Small vector toolbar icons, painted at the active desktop scale. */
final class WorkspaceIcon implements Icon {
    enum Kind { SAVE, UNDO, REDO, MOTION }
    boolean isMotion(){return kind==Kind.MOTION;}
    private final Kind kind;
    WorkspaceIcon(Kind kind){this.kind=kind;}
    public int getIconWidth(){return UIScale.scale(18);}
    public int getIconHeight(){return UIScale.scale(18);}
    public void paintIcon(Component component,Graphics graphics,int x,int y){
        Graphics2D g=(Graphics2D)graphics.create();
        try{g.translate(x,y);g.scale(getIconWidth()/18.0,getIconHeight()/18.0);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(component.isEnabled()?component.getForeground():UIManager.getColor("Label.disabledForeground"));g.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            if(kind==Kind.SAVE){g.drawRoundRect(2,2,14,14,2,2);g.drawRect(5,2,7,5);g.drawRect(5,10,8,6);}
            else if(kind==Kind.MOTION){g.drawLine(3,9,15,9);g.drawLine(9,3,9,15);g.drawLine(3,9,5,7);g.drawLine(3,9,5,11);g.drawLine(15,9,13,7);g.drawLine(15,9,13,11);g.drawLine(9,3,7,5);g.drawLine(9,3,11,5);g.drawLine(9,15,7,13);g.drawLine(9,15,11,13);}
            else{if(kind==Kind.REDO){g.translate(18,0);g.scale(-1,1);}Path2D path=new Path2D.Double();path.moveTo(6,3);path.lineTo(2,7);path.lineTo(6,11);path.moveTo(2,7);path.lineTo(10,7);path.curveTo(16,7,17,14,11,15);g.draw(path);}
        }finally{g.dispose();}
    }
    static void install(JButton button,Kind kind,String description){button.setIcon(new WorkspaceIcon(kind));button.setText(null);button.setToolTipText(description);button.getAccessibleContext().setAccessibleName(description);WorkspaceStyle.quiet(button);}
}
