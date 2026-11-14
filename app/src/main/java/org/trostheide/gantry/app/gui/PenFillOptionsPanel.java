package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.svgtoolbox.PenFill;
import javax.swing.*;
import java.awt.*;
import java.text.ParseException;
import java.util.Locale;

final class PenFillOptionsPanel extends JPanel {
    private final JSpinner width,overlap;
    private final JCheckBox inside=new JCheckBox("Keep strokes inside boundary",true);
    private final JLabel result=new JLabel();
    private Runnable changed=()->{};
    PenFillOptionsPanel(double layerWidth,double overlapPercent) {
        super(new BorderLayout(8,8));
        width=new JSpinner(new SpinnerNumberModel(layerWidth,0.001,100.0,0.05));
        overlap=new JSpinner(new SpinnerNumberModel(overlapPercent,0.0,99.0,5.0));
        JPanel fields=new JPanel(new GridLayout(0,2,8,8));
        addField(fields,"Effective stroke width (mm)",width);addField(fields,"Overlap (%)",overlap);
        fields.add(new JLabel("Calculated spacing"));fields.add(result);fields.add(inside);fields.add(new JLabel("Round-tip estimate"));add(fields,BorderLayout.CENTER);
        JPanel profiles=new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton load=new JButton("Load pen/paper profile…"),save=new JButton("Save profile…");profiles.add(load);profiles.add(save);add(profiles,BorderLayout.NORTH);
        load.addActionListener(e->{try{Object chosen=JOptionPane.showInputDialog(this,"Choose a pen/paper combination","Pen/paper profiles",JOptionPane.PLAIN_MESSAGE,null,PenFillProfiles.local().load().toArray(),null);if(chosen instanceof PenFillProfiles.Profile p)setFill(p.fill());}catch(Exception ex){error(ex);}});
        save.addActionListener(e->{try{commit();JTextField name=new JTextField();JSpinner nominal=new JSpinner(new SpinnerNumberModel(layerWidth,.001,100.,.05));JPanel form=new JPanel(new GridLayout(0,2));form.add(new JLabel("Pen and paper name"));form.add(name);form.add(new JLabel("Nominal tip diameter (mm)"));form.add(nominal);if(JOptionPane.showConfirmDialog(this,form,"Save calibrated profile",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;nominal.commitEdit();
            var profile=new PenFillProfiles.Profile(name.getText().trim(),((Number)nominal.getValue()).doubleValue(),fill());
            if(PenFillProfiles.local().load().stream().anyMatch(p->p.name().equals(profile.name())) && JOptionPane.showConfirmDialog(this,"Replace this saved profile?","Replace profile",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            PenFillProfiles.local().save(profile);JOptionPane.showMessageDialog(this,"Profile saved. Nominal diameter is stored separately from effective stroke width.");}catch(Exception ex){error(ex);}});
        width.addChangeListener(e->refresh());overlap.addChangeListener(e->refresh());inside.addActionListener(e->refresh());refresh();
    }
    void onChange(Runnable callback){changed=callback;}
    void setFill(PenFill fill){width.setValue(fill.widthMm());overlap.setValue(fill.overlapPercent());inside.setSelected(fill.insideBoundary());refresh();}
    private void error(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Pen/paper profile",JOptionPane.ERROR_MESSAGE);}
    private static void addField(JPanel panel,String text,JSpinner field){JLabel label=new JLabel(text);label.setLabelFor(field);field.getAccessibleContext().setAccessibleName(text);panel.add(label);panel.add(field);}
    private void refresh(){result.setText(String.format(Locale.ROOT,"%.4f mm",fill().spacingMm()));changed.run();}
    void commit() throws ParseException {width.commitEdit();overlap.commitEdit();fill();}
    double width(){return ((Number)width.getValue()).doubleValue();}
    double overlap(){return ((Number)overlap.getValue()).doubleValue();}
    PenFill fill(){return new PenFill(width(),overlap(),inside.isSelected());}
}
