package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.GantryConfig;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.function.Supplier;

/** Layout and navigation only; no document or machine mutations. */
final class WorkspaceShell extends JPanel {
    final JPanel inspector = new JPanel(new BorderLayout());
    final JLabel jobTitle = new JLabel("Start with your artwork");
    final JTextArea jobHint = WorkspaceStyle.note("");
    final JLabel jobSummary = new JLabel("No artwork");
    final JTextArea jobDetail = WorkspaceStyle.note("No active job");
    final JButton stop;
    private final JButton primary;
    final JSplitPane split;
    final JToggleButton machineToggle = new JToggleButton("Machine");
    final JToggleButton pin = new JToggleButton("Pin");
    private final JPanel cards = new JPanel(new CardLayout()) {
        @Override public Dimension getPreferredSize(){for(Component card:getComponents())if(card.isVisible())return card.getPreferredSize();return super.getPreferredSize();}
    };
    private final JPanel machineHeader=new JPanel(new BorderLayout());
    void setMachineStatus(JComponent status){machineHeader.removeAll();machineHeader.add(status,BorderLayout.CENTER);machineHeader.add(machineActions,BorderLayout.EAST);machineHeader.revalidate();}
    private final JPanel machineActions=new JPanel(new FlowLayout(FlowLayout.RIGHT,4,0));
    private final JPanel machineSection = new JPanel(new BorderLayout());
    private final JButton back;
    private Runnable returnAction=()->showView("Prepare");
    void setReturnAction(Runnable action){returnAction=action;}
    private final Runnable stopJog;
    private final Runnable save;
    private final Supplier<GantryConfig> config;
    private boolean activeJob;
    private final JPanel progressSlot=new JPanel(new BorderLayout());

    WorkspaceShell(JComponent canvas,JComponent prepare,JComponent check,JComponent plot,JComponent machine,
                   JComponent console,JComponent raw,JButton primary,Runnable stopAction,Runnable copy,
                   Supplier<GantryConfig> config,Runnable save,Runnable stopJog) {
        super(new BorderLayout(6,4));
        this.primary=primary;
        this.stopJog=stopJog; this.config=config; this.save=save;
        cards.add(prepare,"Prepare"); cards.add(check,"Check"); cards.add(plot,"Plot");
        // CardLayout otherwise uses the tallest hidden card when computing the scroll height.
        back=WorkspaceStyle.button("‹ Artwork",()->returnAction.run()); back.setVisible(false);
        JPanel properties=new JPanel(new BorderLayout()); properties.add(back,BorderLayout.NORTH); properties.add(cards,BorderLayout.CENTER);
        machineHeader.add(WorkspaceStyle.heading("Machine"),BorderLayout.WEST);
        WorkspaceStyle.quiet(pin);pin.setSelected(config.get().machinePanelPinned);
        pin.setToolTipText("Keep machine controls open across jobs and restarts");machineActions.add(pin);
        machineActions.add(WorkspaceStyle.button("Close",()->setMachineOpen(false)));machineHeader.add(machineActions,BorderLayout.EAST);
        machineSection.add(machineHeader,BorderLayout.NORTH);machineSection.add(machine,BorderLayout.CENTER);
        machineSection.setVisible(pin.isSelected());
        JPanel contents=new JPanel(new BorderLayout());contents.add(machineSection,BorderLayout.NORTH);contents.add(properties,BorderLayout.CENTER);
        JPanel topAligned=new InspectorContent();topAligned.add(contents,BorderLayout.CENTER);
        JScrollPane scroll=new JScrollPane(topAligned);scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);scroll.getVerticalScrollBar().setUnitIncrement(24);
        inspector.add(scroll); inspector.setMinimumSize(new Dimension(340,0));
        WorkspaceStyle.quiet(machineToggle);machineToggle.setSelected(pin.isSelected());
        machineToggle.addActionListener(e->setMachineOpen(machineToggle.isSelected()));
        pin.addActionListener(e->{config.get().machinePanelPinned=pin.isSelected();save.run();});
        machineSection.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"closeMachine");
        machineSection.getActionMap().put("closeMachine",new AbstractAction(){public void actionPerformed(ActionEvent e){setMachineOpen(false);machineToggle.requestFocusInWindow();}});
        split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,canvas,inspector);split.setResizeWeight(1);split.setBorder(BorderFactory.createEmptyBorder());add(split,BorderLayout.CENTER);
        split.addComponentListener(new java.awt.event.ComponentAdapter(){
            private boolean restored;
            @Override public void componentResized(java.awt.event.ComponentEvent e){
                if(!restored && split.getWidth()>500){restored=true;split.setDividerLocation(Math.max(100,split.getWidth()-Math.max(340,config.get().inspectorWidth)));}
            }
        });
        Timer persist=new Timer(350,e->{config.get().inspectorWidth=Math.max(340,split.getWidth()-split.getDividerLocation());save.run();});persist.setRepeats(false);
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,e->persist.restart());
        JPanel bottom=new JPanel(new BorderLayout(6,6));
        JPanel job=new JPanel(new BorderLayout(8,4));job.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,UIManager.getColor("Separator.foreground")),BorderFactory.createEmptyBorder(6,10,6,10)));
        jobTitle.setFont(jobTitle.getFont().deriveFont(Font.BOLD,15f));
        JPanel copyPanel=new JPanel(new BorderLayout(0,3));copyPanel.setMinimumSize(new Dimension(0,0));copyPanel.add(jobTitle,BorderLayout.NORTH);JPanel middle=new JPanel(new BorderLayout(0,3));middle.add(jobSummary,BorderLayout.NORTH);middle.add(progressSlot,BorderLayout.CENTER);copyPanel.add(middle,BorderLayout.CENTER);jobTitle.setToolTipText(jobHint.getText());job.add(copyPanel,BorderLayout.CENTER);
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));WorkspaceStyle.primary(primary);controls.add(primary);
        stop=WorkspaceStyle.button("Stop",stopAction);WorkspaceStyle.action(stop,WorkspaceStyle.ActionRole.STOP,true);stop.getAccessibleContext().setAccessibleDescription("Stop the active job and attempt motion halt and pen lift");controls.add(stop);job.add(controls,BorderLayout.EAST);
        JToggleButton diagnostics=new JToggleButton("Diagnostics");WorkspaceStyle.quiet(diagnostics);diagnostics.setSelected(config.get().diagnosticsOpen);
        JPanel drawerTools=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));controls.add(diagnostics,0);
        JPanel drawer=new JPanel(new BorderLayout());JPanel drawerHeader=new JPanel(new BorderLayout());drawerHeader.add(raw,BorderLayout.CENTER);drawerHeader.add(WorkspaceStyle.button("Copy diagnostics",copy),BorderLayout.EAST);
        drawer.add(drawerHeader,BorderLayout.NORTH);drawer.add(console,BorderLayout.CENTER);drawer.setPreferredSize(new Dimension(0,150));drawer.setVisible(diagnostics.isSelected());
        diagnostics.addActionListener(e->{drawer.setVisible(diagnostics.isSelected());config.get().diagnosticsOpen=diagnostics.isSelected();save.run();revalidate();});
        JPanel diagnosticArea=new JPanel(new BorderLayout(0,4));diagnosticArea.add(drawer,BorderLayout.CENTER);
        bottom.add(diagnosticArea,BorderLayout.CENTER);bottom.add(job,BorderLayout.SOUTH);add(bottom,BorderLayout.SOUTH);
    }
    /** Forms follow the viewport width even when a document has a very long name. */
    private static final class InspectorContent extends JPanel implements Scrollable {
        InspectorContent(){super(new BorderLayout());}
        public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return 24;}
        public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return Math.max(24,visible.height-24);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return getParent()!=null && getPreferredSize().height<getParent().getHeight();}
    }
    void setProgressComponent(JComponent component){progressSlot.add(component,BorderLayout.NORTH);jobDetail.setRows(1);progressSlot.add(jobDetail,BorderLayout.SOUTH);}
    void refreshInspectorSize(){revalidate();}
    void setMachineOpen(boolean open){stopJog.run();if(!open&&pin.isSelected()){pin.setSelected(false);config.get().machinePanelPinned=false;save.run();}machineToggle.setSelected(open);machineSection.setVisible(open);revalidate();repaint();}
    boolean isMachineOpen(){return machineSection.isVisible();}
    void showView(String name){
        stopJog.run();
        if("Machine".equals(name)){setMachineOpen(true);return;}
        ((CardLayout)cards.getLayout()).show(cards,name); back.setVisible("Check".equals(name));
        revalidate();repaint();
    }
    void render(WorkspacePresentation.View view,boolean guidance){
        WorkspaceStyle.action(primary,WorkspaceStyle.roleFor(view.action()),true);
        WorkspaceStyle.status(jobTitle,switch(view.phase()) {
            case COMPLETE, READY -> "Gantry.success";
            case FAILED -> "Gantry.error";
            case WAITING, PAUSED, STOPPED -> "Gantry.warning";
            default -> "Label.foreground";
        });
        jobTitle.setText(view.title());jobTitle.setToolTipText(view.hint());jobHint.setText(view.hint());jobHint.setVisible(guidance);stop.setVisible(view.stop());stop.setEnabled(view.stop());
        if(view.stop()&&view.phase()!=WorkspacePresentation.Phase.MANUAL&&!activeJob&&!config.get().machinePanelPinned)setMachineOpen(false);
        activeJob=view.stop();back.setEnabled(!activeJob);progressSlot.setVisible(activeJob||view.phase()==WorkspacePresentation.Phase.COMPLETE||view.phase()==WorkspacePresentation.Phase.STOPPED||view.phase()==WorkspacePresentation.Phase.FAILED);
    }
}
