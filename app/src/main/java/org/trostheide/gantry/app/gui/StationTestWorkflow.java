package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.GantryConfig;
import org.trostheide.gantry.app.plot.PlotService;
import org.trostheide.gantry.app.plot.StationConfig;
import org.trostheide.gantry.plotter.PlotterBackend;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Guided dry/wet visit and position correction for configured paint stations. */
final class StationTestWorkflow {
    private final GantryConfig config;
    private final BooleanSupplier connected;
    private final Consumer<Consumer<PlotterBackend>> backendAction;
    private final Runnable persist;
    private final Consumer<String> log;

    StationTestWorkflow(GantryConfig config, BooleanSupplier connected,
            Consumer<Consumer<PlotterBackend>> backendAction, Runnable persist, Consumer<String> log) {
        this.config = config; this.connected = connected; this.backendAction = backendAction;
        this.persist = persist; this.log = log;
    }

    void show(Window owner, Component parent) {
        if (!connected.getAsBoolean()) { message(parent, "Connect to the plotter first."); return; }
        if (config.stations.isEmpty()) { message(parent, "No refill stations configured yet."); return; }
        List<WizardStep> steps = new ArrayList<>();
        steps.add(new PanelStep("Intro", label("<h3>Test color stations</h3>Move to each station, "
                + "perform an optional wet test, and nudge its stored position.")));
        List<StationStep> stationSteps = new ArrayList<>();
        for (Map.Entry<String, StationConfig> entry : config.stations.entrySet()) {
            StationStep step = new StationStep(entry.getKey(), entry.getValue());
            stationSteps.add(step); steps.add(step);
        }
        steps.add(new PanelStep("Done", label("<h3>Test run complete</h3>Finish to save nudged positions.")));
        WizardDialog wizard = new WizardDialog(owner, "Test Color Stations", steps);
        wizard.setVisible(true);
        if (!wizard.finishedSuccessfully()) return;
        for (StationStep step : stationSteps) config.stations.put(step.name, step.current());
        persist.run(); log.accept("Station test run finished; positions saved.");
    }

    private final class StationStep implements WizardStep {
        final String name; final StationConfig base; double x; double y;
        final JPanel panel = new JPanel(); final JLabel coordinates = new JLabel();
        final JSpinner amount = new JSpinner(new SpinnerNumberModel(1.0, .1, 50.0, .5));
        StationStep(String name, StationConfig base) {
            this.name=name; this.base=base; x=base.x(); y=base.y();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
            panel.add(new JLabel("Station '"+name+"' — "+base.behavior())); updateLabel(); panel.add(coordinates);
            JButton move=new JButton("Move here (pen up)"), wet=new JButton("Wet test (dip)");
            move.addActionListener(e -> service(s -> s.dryVisitStation(current())));
            wet.addActionListener(e -> service(s -> s.wetTestStation(current())));
            panel.add(row(move,wet));
            JButton xm=new JButton("−X"), xp=new JButton("+X"), ym=new JButton("−Y"), yp=new JButton("+Y");
            xm.addActionListener(e->nudge(-step(),0)); xp.addActionListener(e->nudge(step(),0));
            ym.addActionListener(e->nudge(0,-step())); yp.addActionListener(e->nudge(0,step()));
            panel.add(row(new JLabel("Step:"),amount,xm,xp,ym,yp));
        }
        double step(){return ((Number)amount.getValue()).doubleValue();}
        void nudge(double dx,double dy){x+=dx;y+=dy;updateLabel();backendAction.accept(b->{b.penup();b.move(dx,dy);});}
        void service(Consumer<PlotService> action){backendAction.accept(b->{PlotService s=new PlotService(b,config.toPlotSettings());s.setLogCallback(log);action.accept(s);});}
        StationConfig current(){return new StationConfig(round(x),round(y),base.zDown(),base.behavior(),base.color(),base.dwellMs(),base.swirlRadius());}
        void updateLabel(){coordinates.setText(String.format("Position: (%.1f, %.1f) mm",round(x),round(y)));}
        @Override public String title(){return "Station "+name;} @Override public JComponent panel(){return panel;}
        @Override public boolean isOptional(){return true;}
    }

    private static JPanel row(JComponent... items){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT));for(JComponent i:items)p.add(i);return p;}
    private static JComponent label(String html){return new JLabel("<html><body style='width:430px'>"+html+"</body></html>");}
    private static double round(double value){return Math.round(value*100.0)/100.0;}
    private static void message(Component parent,String text){JOptionPane.showMessageDialog(parent,text,"Test Color Stations",JOptionPane.INFORMATION_MESSAGE);}
}
