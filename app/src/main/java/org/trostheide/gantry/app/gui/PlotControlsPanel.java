package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.model.Layer;
import org.trostheide.gantry.model.ProcessorOutput;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Layer inclusion and properties, plus progress for the persistent job strip. */
final class PlotControlsPanel extends JPanel {
    interface LayerSettingsEditor { void edit(int layerIndex); }
    record Actions(Runnable selectionChanged, Runnable projectChanged,
                   Consumer<Boolean> colorByLayer, LayerSettingsEditor layerSettings) { }

    private final Actions actions;
    private final JButton all = new JButton("All");
    private final JButton none = new JButton("None");
    private final JSpinner passes = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
    private final JLabel liveTime = new JLabel("No active job");
    private final JLabel time = new JLabel("Est: --:--");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final javax.swing.table.DefaultTableModel layerModel = new javax.swing.table.DefaultTableModel(new Object[]{"Plot", "#", "Pen", "Layer"}, 0) {
        @Override public Class<?> getColumnClass(int column) { return column==0?Boolean.class:column==2?Color.class:String.class; }
        @Override public boolean isCellEditable(int row,int column) { return column==0&&!isPlotting(); }
    };
    private final JTable layerTable = new JTable(layerModel);
    private final JCheckBox colors = new JCheckBox("Colour layers", true);
    private final JButton properties = WorkspaceStyle.button("Pen properties…", () -> editSelectedLayer());
    private boolean rebuilding;

    PlotControlsPanel(Actions actions) {
        this.actions = actions;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(0,0,0,0));
        passes.addChangeListener(e->actions.projectChanged().run());
        progress.setStringPainted(true); progress.setVisible(false);
        layerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        layerTable.setRowHeight(30); layerTable.setShowGrid(false);
        layerTable.getAccessibleContext().setAccessibleName("Layers and plot inclusion");
        layerTable.getColumnModel().getColumn(0).setMaxWidth(42);
        layerTable.getColumnModel().getColumn(1).setMaxWidth(30);
        layerTable.getColumnModel().getColumn(2).setMaxWidth(36);
        layerTable.setDefaultRenderer(Color.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int col) {
                JLabel label=(JLabel)super.getTableCellRendererComponent(table,"",selected,focus,row,col);
                label.setIcon(new Icon(){public int getIconWidth(){return 12;}public int getIconHeight(){return 12;}public void paintIcon(Component c,Graphics g,int x,int y){g.setColor((Color)value);g.fillRoundRect(x,y,12,12,4,4);}});
                label.setToolTipText("Pen colour");return label;
            }
        });
        layerTable.setDefaultRenderer(String.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int col) {
                JLabel label=(JLabel)super.getTableCellRendererComponent(table,value,selected,focus,row,col);
                label.setToolTipText(String.valueOf(value));return label;
            }
        });
        layerTable.getSelectionModel().addListSelectionListener(e -> {
            int selected=layerTable.getSelectedRow();
            properties.setToolTipText(selected<0?null:"Pen properties for "+layerModel.getValueAt(selected,3));properties.setEnabled(selected>=0&&!isPlotting());properties.setVisible(selected>=0);
        });
        layerModel.addTableModelListener(e -> {if(e.getColumn()==0)changed();});
        properties.setEnabled(false);properties.setVisible(false);WorkspaceStyle.quiet(all);WorkspaceStyle.quiet(none);

        all.addActionListener(e -> selectAll(true)); none.addActionListener(e -> selectAll(false));
        JPanel header = row(new JLabel("Passes"), passes, all, none);
        JScrollPane scroll = new JScrollPane(layerTable);
        int layerHeight = 3 * (all.getPreferredSize().height + 4) + 4;
        scroll.setPreferredSize(new Dimension(180, layerHeight));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, layerHeight));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        colors.addActionListener(e -> actions.colorByLayer().accept(colors.isSelected()));
        JPanel footer = row(time, colors);
        for (JComponent component : List.of(
                header, scroll, properties, footer)) {
            component.setAlignmentX(LEFT_ALIGNMENT); add(component);
        }
    }

    JPanel progressStrip() { JPanel strip=new JPanel(new BorderLayout(8,0));strip.add(progress,BorderLayout.WEST);progress.setPreferredSize(new Dimension(100,16));strip.add(liveTime,BorderLayout.CENTER);return strip; }
    void rebuild(ProcessorOutput output, VisualizationPanel visualization, boolean plotting) {
        rebuilding = true; int selected=layerTable.getSelectedRow(); layerModel.setRowCount(0);
        if(output!=null)for(int i=0;i<output.layers().size();i++) {
            Layer layer=output.layers().get(i);
            layerModel.addRow(new Object[]{true,Integer.toString(i+1),visualization.colorForLayer(i),layer.id()});
        }
        if(selected>=0&&selected<layerModel.getRowCount())layerTable.setRowSelectionInterval(selected,selected);
        rebuilding=false; actions.selectionChanged().run();
    }
    private void editSelectedLayer(){int selected=layerTable.getSelectedRow();if(selected>=0&&!isPlotting())actions.layerSettings().edit(selected);}
    List<Integer> selectedLayers() {
        List<Integer> selected=new ArrayList<>();
        for(int i=0;i<layerModel.getRowCount();i++)if(Boolean.TRUE.equals(layerModel.getValueAt(i,0)))selected.add(i);
        return selected;
    }
    int passes(){return ((Number)passes.getValue()).intValue();}
    void setPasses(int value){passes.setValue(Math.max(1,Math.min(10,value)));}
    void setSelectedLayers(List<Integer> selected){rebuilding=true;for(int i=0;i<layerModel.getRowCount();i++)layerModel.setValueAt(selected.contains(i),i,0);rebuilding=false;actions.selectionChanged().run();}
    void setPlotting(boolean plotting){
        putClientProperty("plotting",plotting);
        all.setEnabled(!plotting); none.setEnabled(!plotting); colors.setEnabled(!plotting); passes.setEnabled(!plotting);
        layerTable.setEnabled(!plotting); properties.setEnabled(!plotting&&layerTable.getSelectedRow()>=0);
        progress.setVisible(plotting);

        if(plotting){progress.setValue(0);progress.setString("0%");}
    }
    void setReplay(boolean replay) {
        progress.setIndeterminate(replay);
        if (replay) progress.setString("Replaying G-code...");
    }
    boolean isPlotting(){return Boolean.TRUE.equals(getClientProperty("plotting"));}
    void setProgress(int percent){progress.setValue(percent);progress.setString(percent+"%");}
    void setTime(String text){time.setText(text);liveTime.setText(text);}
    void setTime(String text,String tooltip){setTime(text);time.setToolTipText(tooltip);liveTime.setToolTipText(tooltip);}

    private void selectAll(boolean selected){rebuilding=true;for(int i=0;i<layerModel.getRowCount();i++)layerModel.setValueAt(selected,i,0);rebuilding=false;changed();}
    private void changed(){if(!rebuilding){actions.selectionChanged().run();actions.projectChanged().run();}}
    private static JPanel row(JComponent... items){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,4,2));for(JComponent i:items)p.add(i);return p;}
}
