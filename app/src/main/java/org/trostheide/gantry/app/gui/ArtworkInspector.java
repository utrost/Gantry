package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.session.CompositionArtwork;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Contextual artwork properties, independent of connection and job state. */
final class ArtworkInspector extends JPanel {
    private final JComboBox<String> selection = new JComboBox<>();
    private final JLabel summary = new JLabel("No artwork loaded");
    private final JPanel placement;
    private final JPanel layers;
    private final JPanel layersHeading=WorkspaceStyle.section("Layers & pens",null);
    private final JButton processing;
    private final JButton groups;
    private final JPanel groupPlacement = new JPanel();
    private final JPanel relativePlacement = new JPanel(new GridLayout(0,2,6,4));
    private final JSpinner bedX=number(0,-10000,10000),bedY=number(0,-10000,10000),width=number(1,.001,10000),height=number(1,.001,10000);
    private final VisualizationPanel visualization;
    private final JSpinner x=number(0,-10000,10000),y=number(0,-10000,10000),scale=number(1,.001,1000);
    private final JCheckBox mirror=new JCheckBox("Mirror horizontally");
    private final JLabel groupSize=new JLabel();
    private List<CompositionArtwork> artworks=List.of();
    private CompositionArtwork selected;
    private boolean refreshing, locked;
    private boolean svg, raster;

    ArtworkInspector(JPanel placement,JPanel layers,Consumer<String> select,Runnable manage,
                     Runnable processSource,Runnable reprocess,Runnable vectorize,
                     BiConsumer<CompositionArtwork,CompositionArtwork.Transform> transform, VisualizationPanel visualization,
                     BiConsumer<CompositionArtwork,PhysicalArtworkPlacement.Edit> physicalEdit) {
        this.visualization=visualization;
        setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));setBorder(BorderFactory.createEmptyBorder(1,10,1,10));this.placement=placement;this.layers=layers;
        selection.getAccessibleContext().setAccessibleName("Artwork properties target");
        selection.setMaximumSize(new Dimension(Integer.MAX_VALUE,selection.getPreferredSize().height));selection.setMinimumSize(new Dimension(0,selection.getPreferredSize().height));
        selection.addActionListener(e->{if(!refreshing){int i=selection.getSelectedIndex();select.accept(i<=0?null:artworks.get(i-1).id());}});
        processing=WorkspaceStyle.button("Processing ▾",()->{});WorkspaceStyle.quiet(processing);
        processing.addActionListener(e->{JPopupMenu menu=new JPopupMenu();
            if(selected!=null&&selected.sourcePath()!=null&&selected.importOptions()!=null)add(menu,"Re-process selected artwork…",reprocess);
            else if(selected==null&&svg)add(menu,"Adjust source processing…",processSource);
            if(selected==null&&raster)add(menu,"Re-vectorize image…",vectorize);
            if(menu.getComponentCount()>0)menu.show(processing,0,processing.getHeight());});
        groups=WorkspaceStyle.button("Artwork groups…",manage);
        for(Object[] entry:new Object[][]{{"Group X offset (mm)",x},{"Group Y offset (mm)",y},{"Group scale",scale}}){JLabel label=new JLabel((String)entry[0]);JComponent field=(JComponent)entry[1];label.setLabelFor(field);field.getAccessibleContext().setAccessibleName(label.getText());relativePlacement.add(label);relativePlacement.add(field);}
        relativePlacement.add(groupSize);relativePlacement.add(mirror);
        groupPlacement.setLayout(new BoxLayout(groupPlacement,BoxLayout.Y_AXIS));
        groupPlacement.add(WorkspaceStyle.section("Position & size","mm · from origin"));
        JPanel physical=new JPanel(new GridLayout(2,4,6,6));
        String[] labels={"X","Y","Width","Height"};JSpinner[] values={bedX,bedY,width,height};
        JTextArea validation=WorkspaceStyle.note("");validation.setVisible(false);
        for(int i=0;i<values.length;i++){
            JLabel label=new JLabel(labels[i]);label.setLabelFor(values[i]);values[i].getAccessibleContext().setAccessibleName("Selected artwork "+labels[i]+" in millimetres");values[i].setPreferredSize(new Dimension(68,values[i].getPreferredSize().height));
            physical.add(label);physical.add(values[i]);NumericEditSupport.install(values[i],message->{validation.setText(message);validation.setVisible(!message.isBlank());revalidate();});
            final boolean widthEdit=i==2;
            values[i].addChangeListener(e->{if(!refreshing&&!locked&&selected!=null)physicalEdit.accept(selected,new PhysicalArtworkPlacement.Edit(
                    ((Number)bedX.getValue()).doubleValue(),((Number)bedY.getValue()).doubleValue(),((Number)width.getValue()).doubleValue(),((Number)height.getValue()).doubleValue(),widthEdit));});
        }
        groupPlacement.add(physical);width.setToolTipText("Physical width in mm; proportions remain locked");height.setToolTipText("Physical height in mm; proportions remain locked");
        JCheckBox relative=new JCheckBox("Relative transform…");relativePlacement.setVisible(false);
        relative.addActionListener(e->{relativePlacement.setVisible(relative.isSelected());groupPlacement.setMaximumSize(new Dimension(Integer.MAX_VALUE,groupPlacement.getPreferredSize().height));revalidate();});
        groupPlacement.add(relative);groupPlacement.add(relativePlacement);groupPlacement.add(validation);
        Runnable apply=()->{if(!refreshing&&!locked&&selected!=null)transform.accept(selected,new CompositionArtwork.Transform(((Number)x.getValue()).doubleValue(),((Number)y.getValue()).doubleValue(),((Number)scale.getValue()).doubleValue(),mirror.isSelected()));};
        x.addChangeListener(e->apply.run());y.addChangeListener(e->apply.run());scale.addChangeListener(e->apply.run());mirror.addActionListener(e->apply.run());
        for(Component child:groupPlacement.getComponents())if(child instanceof JComponent component)component.setAlignmentX(LEFT_ALIGNMENT);
        JPanel target=new JPanel(new BorderLayout(8,0));target.add(WorkspaceStyle.heading("Artwork"),BorderLayout.WEST);target.add(selection,BorderLayout.CENTER);
        for(JComponent component:new JComponent[]{target,summary,placement,groupPlacement,processing,groups,layersHeading,layers}){
            component.setAlignmentX(LEFT_ALIGNMENT);if(component!=layers)component.setMaximumSize(new Dimension(Integer.MAX_VALUE,component.getPreferredSize().height));add(component);add(Box.createVerticalStrut(2));
        }
    }
    void refresh(List<CompositionArtwork> next,CompositionArtwork selected,boolean hasOutput,boolean svg,boolean raster,String description){
        refreshing=true;
        try{this.artworks=next;this.selected=selected;this.svg=svg;this.raster=raster;
            selection.removeAllItems();selection.addItem("Whole drawing");for(var a:next)selection.addItem(a.label());
            for(int i=0;i<next.size();i++)if(selected!=null&&next.get(i).id().equals(selected.id()))selection.setSelectedIndex(i+1);
            selection.setToolTipText(selected==null?"Properties for the whole drawing":selected.label());selection.setVisible(hasOutput);summary.setVisible(!hasOutput);layers.setVisible(hasOutput);layersHeading.setVisible(hasOutput);summary.setText(description);summary.setToolTipText(description);
            placement.setVisible(hasOutput&&selected==null);groupPlacement.setVisible(selected!=null);
            if(selected!=null){double[] physical=PhysicalArtworkPlacement.bounds(visualization,selected.bounds());bedX.setValue(physical[0]);bedY.setValue(physical[1]);width.setValue(Math.max(.001,physical[2]));height.setValue(Math.max(.001,physical[3]));var t=selected.transform();x.setValue(t.x());y.setValue(t.y());scale.setValue(t.scale());mirror.setSelected(t.mirror());var b=selected.bounds();groupSize.setText(b==null?"":String.format(java.util.Locale.ROOT,"%.1f × %.1f mm",b.maxX()-b.minX(),b.maxY()-b.minY()));}
            boolean canProcess=selected==null?svg||raster:selected.sourcePath()!=null&&selected.importOptions()!=null;
            processing.setVisible(hasOutput&&canProcess);groups.setVisible(next.size()>1);
        }finally{refreshing=false;}
        revalidate();repaint();
    }
    void updateSummary(String description){summary.setText(description);summary.setToolTipText(description);}
    void setPlotting(boolean plotting){locked=plotting;for(JComponent component:new JComponent[]{selection,processing,groups,x,y,scale,mirror,bedX,bedY,width,height})component.setEnabled(!plotting);}
    private static JSpinner number(double value,double min,double max){return new JSpinner(new SpinnerNumberModel(value,min,max,.1));}
    private static void add(JPopupMenu menu,String name,Runnable action){JMenuItem item=new JMenuItem(name);item.addActionListener(e->action.run());menu.add(item);}
}
