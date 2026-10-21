package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Transient operator confirmations. Never saved or restored across connection/session changes. */
final class WorkspaceChecks extends JPanel {
    private final JLabel automatic = new JLabel();
    private final List<JCheckBox> checks = List.of(new JCheckBox("Pen installed and lift checked"),
            new JCheckBox("Paper secured; bed and frame clear"), new JCheckBox("Correct origin, size and pen layers"));
    private final Runnable changed;
    private boolean automaticPass;
    WorkspaceChecks(Runnable home,Runnable frame,Runnable penUp,Runnable changed) {
        super();setBorder(BorderFactory.createEmptyBorder(10,12,10,12));this.changed=changed;setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
        add(WorkspaceStyle.heading("Before the machine moves"));add(automatic);
        add(WorkspaceStyle.note("Machine checks"));
        add(WorkspaceStyle.button("Home machine…",home));
        add(WorkspaceStyle.button("Frame artwork — pen up",frame));
        add(WorkspaceStyle.button("Raise pen",penUp));
        add(Box.createVerticalStrut(16));add(WorkspaceStyle.heading("Your physical checks"));
        add(WorkspaceStyle.note("Confirm these yourself. Simulation does not verify hardware."));
        for(JCheckBox c:checks){c.setAlignmentX(LEFT_ALIGNMENT);c.addActionListener(e->changed.run());add(c);}
    }
    void update(boolean hasArtwork,boolean selection,boolean connected,boolean idle,boolean busy,String summary){
        automaticPass=hasArtwork&&selection&&connected&&idle&&!busy;
        automatic.setText("<html><b>Automatic checks</b><br>"+WorkspaceStyle.escape(summary)+"<br>"+
                (automaticPass?"✓ Drawing, layers and idle connection available":"Review drawing, selected layers and idle connection")+"</html>");
        for(JCheckBox c:checks)c.setEnabled(automaticPass);
    }
    boolean ready(){return automaticPass&&checks.stream().allMatch(AbstractButton::isSelected);}
    void clearConfirmations(){checks.forEach(c->c.setSelected(false));}
}
