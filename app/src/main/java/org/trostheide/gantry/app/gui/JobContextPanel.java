package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.ProcessorOutput;
import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Current-job identity and pen instructions, separate from editable artwork selection. */
final class JobContextPanel extends JPanel {
    private final JLabel title=WorkspaceStyle.heading("Current job");
    private final JTextArea pen=new JTextArea(" ", 0, 20);
    private final JLabel swatch=new JLabel();
    private final JTextArea instruction=WorkspaceStyle.note("");
    private final DefaultListModel<String> pens=new DefaultListModel<>();
    private final JList<String> sequence=new JList<>(pens);
    private final JButton back;
    private int total;
    JobContextPanel(Runnable returnToArtwork){
        super(new BorderLayout(8,12));setBorder(BorderFactory.createEmptyBorder(16,12,12,12));
        pen.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD,20f));
        pen.setEditable(false);pen.setOpaque(false);pen.setLineWrap(true);pen.setWrapStyleWord(true);
        pen.getAccessibleContext().setAccessibleName("Requested pen");
        title.setMinimumSize(new Dimension(0, title.getPreferredSize().height));
        JPanel identity=new JPanel(new BorderLayout(10,6));identity.add(swatch,BorderLayout.WEST);identity.add(pen,BorderLayout.CENTER);
        add(WorkspaceStyle.stack(title,identity,instruction),BorderLayout.NORTH);
        sequence.getAccessibleContext().setAccessibleName("Job pen sequence");sequence.setFocusable(false);
        add(new JScrollPane(sequence),BorderLayout.CENTER);
        back=WorkspaceStyle.button("Return to artwork",returnToArtwork);add(back,BorderLayout.SOUTH);
    }
    void start(ProcessorOutput output,String name){
        start(output,name,List.of());
    }
    void start(ProcessorOutput output,String name,List<String> penNames){
        title.setText(name);title.setToolTipText(name);pens.clear();total=output.layers().size();
        for(int i=0;i<total;i++)pens.addElement((i+1)+". "+(i<penNames.size()?penNames.get(i):output.layers().get(i).id()));
        pen.setText("Preparing job");swatch.setIcon(null);
    }
    void requestPen(int index,Layer layer){
        String name=index>0&&index<=pens.size()?pens.get(index-1).replaceFirst("^[0-9]+\\. ",""):layer.id();
        pen.setText("Pen "+index+" of "+total+"\n"+name);
        pen.setToolTipText(name);sequence.setSelectedIndex(index-1);sequence.ensureIndexIsVisible(index-1);
        Color color=CanvasPalette.parseHex(layer.color());
        swatch.setIcon(new Icon(){public int getIconWidth(){return 44;}public int getIconHeight(){return 44;}
            public void paintIcon(Component c,Graphics g,int x,int y){g.setColor(color==null?Color.GRAY:color);g.fillRoundRect(x,y,42,42,8,8);g.setColor(UIManager.getColor("Label.foreground"));g.drawRoundRect(x,y,42,42,8,8);}});
    }
    void render(WorkspacePresentation.View view){
        instruction.setText(view.phase()==WorkspacePresentation.Phase.WAITING?"Fit this pen, then choose Pen ready — continue below the canvas.":view.hint());
        back.setEnabled(!view.stop());
    }
}
