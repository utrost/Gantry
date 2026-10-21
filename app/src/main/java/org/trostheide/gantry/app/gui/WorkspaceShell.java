package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.GantryConfig;
import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

/** Layout only: task navigation does not mutate documents or command the plotter. */
final class WorkspaceShell extends JPanel {
    final JTabbedPane inspector = new JTabbedPane();
    final JLabel jobTitle = new JLabel("Start with your artwork");
    final JTextArea jobHint = WorkspaceStyle.note("");
    final JLabel jobSummary = new JLabel("No artwork");
    final JTextArea jobDetail = WorkspaceStyle.note("No active job");
    final JButton stop;
    final JSplitPane split;
    private final JPanel drawer;
    private final JToggleButton diagnostics;
    WorkspaceShell(JComponent canvas,JComponent prepare,JComponent check,JComponent plot,JComponent machine,
                   JComponent console,JComponent raw,JButton primary,Runnable stopAction,Runnable copy,
                   Supplier<GantryConfig> config,Runnable save) {
        super(new BorderLayout(8,8));
        for(var entry:java.util.List.of(new Object[]{"Prepare",prepare},new Object[]{"Check",check},new Object[]{"Plot",plot},new Object[]{"Machine",machine})){
            JScrollPane scroll=new JScrollPane((JComponent)entry[1]);scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.getVerticalScrollBar().setUnitIncrement(24);inspector.addTab((String)entry[0],scroll);
        }
        inspector.setMinimumSize(new Dimension(300,0));
        split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,canvas,inspector);split.setResizeWeight(1);split.setBorder(BorderFactory.createEmptyBorder());
        add(split,BorderLayout.CENTER);
        SwingUtilities.invokeLater(()->split.setDividerLocation(Math.max(100,split.getWidth()-config.get().inspectorWidth)));
        Timer persist=new Timer(350,e->{config.get().inspectorWidth=Math.max(300,split.getWidth()-split.getDividerLocation());save.run();});persist.setRepeats(false);
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,e->persist.restart());
        JPanel bottom=new JPanel(new BorderLayout(6,6));
        JPanel job=new JPanel(new BorderLayout(8,4));job.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,UIManager.getColor("Separator.foreground")),BorderFactory.createEmptyBorder(10,8,8,8)));
        jobTitle.setFont(jobTitle.getFont().deriveFont(Font.BOLD,15f));
        JPanel copyPanel=new JPanel(new BorderLayout(0,3));copyPanel.add(jobTitle,BorderLayout.NORTH);copyPanel.add(jobSummary,BorderLayout.CENTER);copyPanel.add(jobHint,BorderLayout.SOUTH);jobHint.setRows(2);job.add(copyPanel,BorderLayout.CENTER);
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));primary.putClientProperty("JButton.buttonType","roundRect");controls.add(primary);
        stop=WorkspaceStyle.button("Stop",stopAction);stop.setBackground(new Color(175,35,35));stop.setForeground(Color.WHITE);stop.getAccessibleContext().setAccessibleDescription("Stop the active job and attempt motion halt and pen lift");controls.add(stop);job.add(controls,BorderLayout.EAST);
        diagnostics=new JToggleButton("Diagnostics");diagnostics.setSelected(config.get().diagnosticsOpen);
        JPanel drawerTools=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));drawerTools.add(diagnostics);drawerTools.add(WorkspaceStyle.button("Copy diagnostics",copy));
        drawer=new JPanel(new BorderLayout());drawer.add(raw,BorderLayout.NORTH);drawer.add(console,BorderLayout.CENTER);drawer.setPreferredSize(new Dimension(0,150));drawer.setVisible(diagnostics.isSelected());
        diagnostics.addActionListener(e->{drawer.setVisible(diagnostics.isSelected());config.get().diagnosticsOpen=diagnostics.isSelected();save.run();revalidate();});
        JPanel diagnosticArea=new JPanel(new BorderLayout(0,4));diagnosticArea.add(drawerTools,BorderLayout.NORTH);diagnosticArea.add(drawer,BorderLayout.CENTER);
        bottom.add(diagnosticArea,BorderLayout.CENTER);bottom.add(job,BorderLayout.SOUTH);add(bottom,BorderLayout.SOUTH);
    }
    void showView(String name){for(int i=0;i<inspector.getTabCount();i++)if(name.equals(inspector.getTitleAt(i)))inspector.setSelectedIndex(i);}
    void render(WorkspacePresentation.View view,boolean guidance){jobTitle.setText(view.title());jobHint.setText(view.hint());jobHint.setVisible(guidance);stop.setVisible(view.stop());stop.setEnabled(view.stop());}
}
