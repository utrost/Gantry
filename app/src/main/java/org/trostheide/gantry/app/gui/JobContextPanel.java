package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.ProcessorOutput;
import javax.swing.*;
import java.awt.*;

/** Current-job identity and pen instructions, separate from editable artwork selection. */
final class JobContextPanel extends JPanel {
    private final JLabel title=WorkspaceStyle.heading("Current job");
    private final JLabel pen=new JLabel(" ");
    private final JLabel swatch=new JLabel();
    private final JTextArea instruction=WorkspaceStyle.note("");
    private final DefaultListModel<String> pens=new DefaultListModel<>();
    private final JList<String> sequence=new JList<>(pens);
    private final JButton back;
    private int total;
    JobContextPanel(Runnable returnToArtwork){
        super(new BorderLayout(8,12));setBorder(BorderFactory.createEmptyBorder(16,12,12,12));
        pen.setFont(pen.getFont().deriveFont(Font.BOLD,20f));pen.getAccessibleContext().setAccessibleName("Requested pen");
        JPanel identity=new JPanel(new BorderLayout(10,6));identity.add(swatch,BorderLayout.WEST);identity.add(pen,BorderLayout.CENTER);
        add(WorkspaceStyle.stack(title,identity,instruction),BorderLayout.NORTH);
        sequence.getAccessibleContext().setAccessibleName("Job pen sequence");sequence.setFocusable(false);
        add(new JScrollPane(sequence),BorderLayout.CENTER);
        back=WorkspaceStyle.button("Return to artwork",returnToArtwork);add(back,BorderLayout.SOUTH);
    }
    void start(ProcessorOutput output,String name){
        title.setText(name);pens.clear();total=output.layers().size();
        for(int i=0;i<total;i++)pens.addElement((i+1)+". "+output.layers().get(i).id());
        pen.setText("Preparing job");swatch.setIcon(null);
    }
    void requestPen(int index,Layer layer){
        pen.setText("<html>Pen "+index+" of "+total+"<br>"+WorkspaceStyle.escape(layer.id())+"</html>");
        pen.setToolTipText(layer.id());sequence.setSelectedIndex(index-1);sequence.ensureIndexIsVisible(index-1);
        Color color=CanvasPalette.parseHex(layer.color());
        swatch.setIcon(new Icon(){public int getIconWidth(){return 44;}public int getIconHeight(){return 44;}
            public void paintIcon(Component c,Graphics g,int x,int y){g.setColor(color==null?Color.GRAY:color);g.fillRoundRect(x,y,42,42,8,8);g.setColor(UIManager.getColor("Label.foreground"));g.drawRoundRect(x,y,42,42,8,8);}});
    }
    void render(WorkspacePresentation.View view){
        instruction.setText(view.phase()==WorkspacePresentation.Phase.WAITING?"Fit this pen, then choose Pen ready — continue below the canvas.":view.hint());
        back.setEnabled(!view.stop());
    }
}
