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
    interface LayerSettingsEditor { void edit(int layerIndex); }
    record Actions(Runnable start, Runnable preflight, Runnable confirm, Runnable pause,
                   Runnable stop, Runnable selectionChanged, Runnable projectChanged,
                   Consumer<Boolean> colorByLayer, LayerSettingsEditor layerSettings) { }

    private final Actions actions;
    private final JButton start = new JButton("Start plotting");
    private final JButton preflight = new JButton("Check before plotting...");
    private final JButton confirm = new JButton("Pen ready — continue");
    private final JButton pause = new JButton("Pause");
    private final JButton stop = new JButton("Stop");
    private final JButton all = new JButton("All");
    private final JButton none = new JButton("None");
    private final JSpinner passes = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
    private final JLabel time = new JLabel("Est: --:--");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JPanel layerList = new JPanel() {
        @Override public Dimension getPreferredSize() {
            Dimension preferred = super.getPreferredSize();
            return new Dimension(0, preferred.height);
        }
    };
    private final JCheckBox colors = new JCheckBox("Colour layers", true);
    private final List<JCheckBox> layers = new ArrayList<>();
    private final List<JButton> layerSettings = new ArrayList<>();
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
        passes.addChangeListener(e->actions.projectChanged().run());
        progress.setStringPainted(true); progress.setVisible(false);
        layerList.setLayout(new BoxLayout(layerList, BoxLayout.Y_AXIS));

        // Separate primary actions so font scaling cannot wrap one into an invisible row.
        JPanel passesRow = row(new JLabel("Passes"), passes);
        JPanel preflightRow = row(preflight);
        JPanel startRow = row(start);
        JPanel runningRow = row(confirm);
        JPanel stopRow = row(pause, stop);
        all.addActionListener(e -> selectAll(true)); none.addActionListener(e -> selectAll(false));
        JPanel header = row(new JLabel("Layers"), all, none);
        JScrollPane scroll = new JScrollPane(layerList);
        int layerHeight = 3 * (all.getPreferredSize().height + 4) + 4;
        scroll.setPreferredSize(new Dimension(180, layerHeight));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, layerHeight));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        colors.addActionListener(e -> actions.colorByLayer().accept(colors.isSelected()));
        JPanel footer = row(time, colors);
        for (JComponent component : List.of(
                passesRow, preflightRow, startRow, runningRow, stopRow, progress, header, scroll, footer)) {
            component.setAlignmentX(LEFT_ALIGNMENT); add(component);
        }
    }

    void rebuild(ProcessorOutput output, VisualizationPanel visualization, boolean plotting) {
        rebuilding = true; layers.clear(); layerSettings.clear(); layerList.removeAll();
        if (output != null) for (int i=0;i<output.layers().size();i++) {
            Layer layer=output.layers().get(i);
            String color=layer.color()==null||layer.color().isEmpty()?"no colour":layer.color();
            JCheckBox box=new JCheckBox(layer.id()+" — "+color,true);
            box.setForeground(visualization.colorForLayer(i)); box.setEnabled(!plotting);
            box.setToolTipText(layer.id() + " — " + color + ": visible in the preview and included in the plot");
            box.setMinimumSize(new Dimension(0, box.getPreferredSize().height));
            int layerIndex = i;
            box.addActionListener(e -> changed()); layers.add(box);
            JLabel order = new JLabel((i + 1) + ".");
            JButton settings = new JButton("Edit…");
            settings.setToolTipText("Set this layer's refill station and maximum drawing distance");
            settings.setEnabled(!plotting);
            settings.addActionListener(e -> actions.layerSettings().edit(layerIndex));
            layerSettings.add(settings);
            JPanel swatch = new JPanel();
            swatch.setBackground(visualization.colorForLayer(i));
            swatch.setPreferredSize(new Dimension(12, 12));
            swatch.setToolTipText("Pen colour: " + color);
            JPanel entry = new JPanel(new BorderLayout(4, 0));
            entry.add(row(order, swatch), BorderLayout.WEST);
            entry.add(box, BorderLayout.CENTER);
            entry.add(settings, BorderLayout.EAST);
            entry.setAlignmentX(LEFT_ALIGNMENT);
            entry.setMaximumSize(new Dimension(Integer.MAX_VALUE, entry.getPreferredSize().height));
            layerList.add(entry);
        }
        rebuilding=false; layerList.revalidate(); layerList.repaint(); actions.selectionChanged().run();
    }

    List<Integer> selectedLayers() {
        List<Integer> selected=new ArrayList<>();
        for(int i=0;i<layers.size();i++) if(layers.get(i).isSelected()) selected.add(i);
        return selected;
    }
    int passes(){return ((Number)passes.getValue()).intValue();}
    void setPasses(int value){passes.setValue(Math.max(1,Math.min(10,value)));}
    void setSelectedLayers(List<Integer> selected){rebuilding=true;for(int i=0;i<layers.size();i++)layers.get(i).setSelected(selected.contains(i));rebuilding=false;actions.selectionChanged().run();}
    void setConnected(boolean connected){this.connected=connected;if(!isPlotting())start.setEnabled(connected);}
    void setPlotting(boolean plotting){
        putClientProperty("plotting",plotting); start.setEnabled(!plotting&&connected);
        confirm.setEnabled(plotting); pause.setEnabled(plotting); stop.setEnabled(plotting);
        all.setEnabled(!plotting); none.setEnabled(!plotting); colors.setEnabled(!plotting); passes.setEnabled(!plotting);
        for(JCheckBox box:layers)box.setEnabled(!plotting);
        progress.setVisible(plotting);
        for(JButton settings:layerSettings)settings.setEnabled(!plotting);
        if(plotting){progress.setValue(0);progress.setString("0%");} else pause.setText("Pause");
    }
    boolean isPlotting(){return Boolean.TRUE.equals(getClientProperty("plotting"));}
    void setPaused(boolean paused){pause.setText(paused?"Resume":"Pause");}
    void setProgress(int percent){progress.setValue(percent);progress.setString(percent+"%");}
    void setTime(String text){time.setText(text);}
    void setTime(String text,String tooltip){time.setText(text);time.setToolTipText(tooltip);}

    private void selectAll(boolean selected){rebuilding=true;for(JCheckBox b:layers)b.setSelected(selected);rebuilding=false;changed();}
    private void changed(){if(!rebuilding){actions.selectionChanged().run();actions.projectChanged().run();}}
    private static JPanel row(JComponent... items){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,4,2));for(JComponent i:items)p.add(i);return p;}
}
