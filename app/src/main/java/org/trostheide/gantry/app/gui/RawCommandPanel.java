package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.plotter.PlotterBackend;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.function.*;

/** Raw G-code entry view, gated by connection and plot state. */
final class RawCommandPanel extends JPanel {
    private final JTextField input=new JTextField(16);private final JButton send=new JButton("Send");
    private final Consumer<Consumer<PlotterBackend>> backend;private final Consumer<String> log;
    private boolean connected,plotting;
    private String unavailable;
    void setUnavailable(String reason){unavailable=reason;refresh();}
    RawCommandPanel(Consumer<Consumer<PlotterBackend>> backend,Consumer<String> log){this.backend=backend;this.log=log;
        setLayout(new FlowLayout(FlowLayout.LEFT,6,4));setBorder(new TitledBorder("Raw G-code"));
        Action action=new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent e){submit();}};
        send.setAction(action);send.setText("Send");WorkspaceStyle.motion(send);send.setToolTipText("Send raw commands — may move the machine immediately");input.addActionListener(action);add(input);add(send);refresh();}
    void setConnected(boolean value){connected=value;refresh();}void setPlotting(boolean value){plotting=value;refresh();}
    private void refresh(){String reason=!connected?"Connect a plotter first.":unavailable!=null?unavailable:plotting?"Wait for the current job to finish.":null;WorkspaceStyle.availability(input,reason);WorkspaceStyle.availability(send,reason);}
    private void submit(){if(!input.isEnabled())return;String command=input.getText().trim();if(command.isEmpty())return;input.setText("");backend.accept(b->{for(String line:b.sendRaw(command))log.accept(line);});}
}
