package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.ProcessorOutput;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Plot buttons, layer selection, progress, and estimate presentation. */
final class PlotControlsPanel extends JPanel {
    record Actions(Runnable start, Runnable preflight, Runnable confirm, Runnable pause,
                   Runnable stop, Runnable selectionChanged, Consumer<Boolean> colorByLayer) { }

    private final Actions actions;
    private final JButton start = new JButton("Start");
    private final JButton preflight = new JButton("Pre-flight...");
    private final JButton confirm = new JButton("Confirm");
    private final JButton pause = new JButton("Pause");
    private final JButton stop = new JButton("Stop");
    private final JButton all = new JButton("All");
    private final JButton none = new JButton("None");
    private final JSpinner passes = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
    private final JLabel time = new JLabel("Est: --:--");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JPanel layerList = new JPanel();
    private final JCheckBox colors = new JCheckBox("Colour layers", true);
    private final List<JCheckBox> layers = new ArrayList<>();
    private boolean rebuilding;
    private boolean connected;

    PlotControlsPanel(Actions actions) {
        this.actions = actions;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new TitledBorder("Plot"));
        start.addActionListener(e -> actions.start().run());
        preflight.addActionListener(e -> actions.preflight().run());
        confirm.addActionListener(e -> actions.confirm().run());
        pause.addActionListener(e -> actions.pause().run());
        stop.addActionListener(e -> actions.stop().run());
        progress.setStringPainted(true); progress.setVisible(false);
        layerList.setLayout(new BoxLayout(layerList, BoxLayout.Y_AXIS));

        JPanel first = row(new JLabel("Passes"), passes, preflight, start, stop);
        JPanel second = row(confirm, pause);
        all.addActionListener(e -> selectAll(true)); none.addActionListener(e -> selectAll(false));
        JPanel header = row(new JLabel("Layers"), all, none);
        JScrollPane scroll = new JScrollPane(layerList);
        scroll.setPreferredSize(new Dimension(180, 84));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
        colors.addActionListener(e -> actions.colorByLayer().accept(colors.isSelected()));
        JPanel footer = row(time, colors);
        for (JComponent component : List.of(first, second, progress, header, scroll, footer)) {
            component.setAlignmentX(LEFT_ALIGNMENT); add(component);
        }
    }

    void rebuild(ProcessorOutput output, VisualizationPanel visualization, boolean plotting) {
        rebuilding = true; layers.clear(); layerList.removeAll();
        if (output != null) for (int i=0;i<output.layers().size();i++) {
            Layer layer=output.layers().get(i);
            String color=layer.color()==null||layer.color().isEmpty()?"no colour":layer.color();
            JCheckBox box=new JCheckBox(layer.id()+" — "+color,true);
            box.setForeground(visualization.colorForLayer(i)); box.setEnabled(!plotting);
            box.addActionListener(e -> changed()); layers.add(box); layerList.add(box);
        }
        rebuilding=false; layerList.revalidate(); layerList.repaint(); actions.selectionChanged().run();
    }

    List<Integer> selectedLayers() {
        List<Integer> selected=new ArrayList<>();
        for(int i=0;i<layers.size();i++) if(layers.get(i).isSelected()) selected.add(i);
        return selected;
    }
    int passes(){return ((Number)passes.getValue()).intValue();}
    void setConnected(boolean connected){this.connected=connected;if(!isPlotting())start.setEnabled(connected);}
    void setPlotting(boolean plotting){
        putClientProperty("plotting",plotting); start.setEnabled(!plotting&&connected);
        confirm.setEnabled(plotting); pause.setEnabled(plotting); stop.setEnabled(plotting);
        all.setEnabled(!plotting); none.setEnabled(!plotting); colors.setEnabled(!plotting); passes.setEnabled(!plotting);
        for(JCheckBox box:layers)box.setEnabled(!plotting); progress.setVisible(plotting);
        if(plotting){progress.setValue(0);progress.setString("0%");} else pause.setText("Pause");
    }
    boolean isPlotting(){return Boolean.TRUE.equals(getClientProperty("plotting"));}
    void setPaused(boolean paused){pause.setText(paused?"Resume":"Pause");}
    void setProgress(int percent){progress.setValue(percent);progress.setString(percent+"%");}
    void setTime(String text){time.setText(text);}
    void setTime(String text,String tooltip){time.setText(text);time.setToolTipText(tooltip);}

    private void selectAll(boolean selected){rebuilding=true;for(JCheckBox b:layers)b.setSelected(selected);rebuilding=false;changed();}
    private void changed(){if(!rebuilding)actions.selectionChanged().run();}
    private static JPanel row(JComponent... items){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,4,2));for(JComponent i:items)p.add(i);return p;}
}
