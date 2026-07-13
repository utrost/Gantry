package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.GantryConfig;
import javax.swing.*;
import java.awt.*;
import java.util.function.*;

/** Occasional settings and optimization dialogs kept out of the main composition class. */
final class ApplicationDialogs {
    record OptimizeOptions(double tolerance,boolean reorder,double mergeTolerance){}
    private final Component parent; private final Supplier<GantryConfig> config; private final Consumer<GantryConfig> saveSettings;
    private final Runnable setup; private final Consumer<OptimizeOptions> optimize; private final BooleanSupplier hasDocument;
    ApplicationDialogs(Component parent,Supplier<GantryConfig> config,Consumer<GantryConfig> saveSettings,Runnable setup,
            Consumer<OptimizeOptions> optimize,BooleanSupplier hasDocument){this.parent=parent;this.config=config;this.saveSettings=saveSettings;this.setup=setup;this.optimize=optimize;this.hasDocument=hasDocument;}
    void settings(){SettingsPanel panel=new SettingsPanel();panel.loadConfig(config.get());boolean[] wizard={false};JButton run=new JButton("Run Setup Wizard...");
        JPanel body=new JPanel(new BorderLayout(0,6));body.add(run,BorderLayout.NORTH);body.add(panel);JOptionPane pane=new JOptionPane(body,JOptionPane.PLAIN_MESSAGE,JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog=pane.createDialog(parent,"Settings");run.addActionListener(e->{wizard[0]=true;dialog.dispose();});dialog.setVisible(true);dialog.dispose();
        if(wizard[0])setup.run();else if(Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue()))saveSettings.accept(panel.toConfig());}
    void optimize(){if(!hasDocument.getAsBoolean()){JOptionPane.showMessageDialog(parent,"Open a Commands (JSON) file or Import SVG first.");return;}
        JSpinner tolerance=new JSpinner(new SpinnerNumberModel(.2,0,10,.1));JCheckBox reorder=new JCheckBox("Reorder",true);JSpinner merge=new JSpinner(new SpinnerNumberModel(.2,0,10,.1));
        JPanel form=new JPanel(new FlowLayout(FlowLayout.LEFT,4,2));form.add(new JLabel("Tolerance"));form.add(tolerance);form.add(reorder);form.add(new JLabel("Merge"));form.add(merge);
        if(JOptionPane.showConfirmDialog(parent,form,"Optimize Commands (JSON)",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION)
            optimize.accept(new OptimizeOptions(((Number)tolerance.getValue()).doubleValue(),reorder.isSelected(),((Number)merge.getValue()).doubleValue()));}
}
