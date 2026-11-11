package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.util.function.*;

/** File choice and review only; importing never sends controller commands. */
final class MachineSettingsTransfer {
    private MachineSettingsTransfer() { }
    static void exportSettings(Component parent, GantryConfig config, Consumer<String> feedback) {
        JFileChooser chooser = chooser();chooser.setSelectedFile(new File("gantry-machine-settings.json"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".json")) file = new File(file.getPath()+".json");
        if (file.exists() && JOptionPane.showConfirmDialog(parent, "Replace " + file.getName() + "?",
                "Export machine settings", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try { MachineSettingsIO.exportSettings(config, file);feedback.accept("Machine settings exported: " + file.getName()); }
        catch (IOException | RuntimeException ex) { error(parent, ex); }
    }
    static void importSettings(Component parent, Supplier<GantryConfig> current, BooleanSupplier available,
            Consumer<GantryConfig> apply) {
        if (!available.getAsBoolean()) { disconnected(parent);return; }
        JFileChooser chooser = chooser();
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        try {
            GantryConfig incoming = MachineSettingsIO.importSettings(chooser.getSelectedFile(), current.get());
            if (JOptionPane.showOptionDialog(parent, review(current.get(),incoming), "Import machine settings",
                    JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE,null,new String[]{"Apply settings","Cancel"},"Cancel") != 0) return;
            if (!available.getAsBoolean()) { disconnected(parent);return; }
            apply.accept(incoming);
        } catch (IOException | RuntimeException ex) { error(parent, ex); }
    }
    static JComponent review(GantryConfig current,GantryConfig incoming){
        var comparison=MachineSettingsIO.comparison(current,incoming);
        JPanel panel=new JPanel(new BorderLayout(8,8));
        long count=comparison.stream().filter(MachineSettingsIO.Change::changed).count();
        panel.add(WorkspaceStyle.note(count+" machine values will change. Check the local serial port before connecting. Simulation, preflight policy and appearance stay local. No firmware settings are written."),BorderLayout.NORTH);
        JTabbedPane groups=new JTabbedPane();
        for(String group:new String[]{"Connection","Machine geometry","Pen & speeds","Limits","Watercolor stations"}){
            var rows=comparison.stream().filter(c->c.group().equals(group)).toList();
            long changed=rows.stream().filter(MachineSettingsIO.Change::changed).count();
            var model=new javax.swing.table.DefaultTableModel(new Object[]{"Setting","Current","Imported","Change"},0){
                @Override public boolean isCellEditable(int row,int column){return false;}
            };
            for(var row:rows)model.addRow(new Object[]{row.property(),row.before(),row.after(),row.changed()?"Changed":"Unchanged"});
            JTable table=new JTable(model);table.setRowHeight(26);table.getAccessibleContext().setAccessibleName(group+" settings comparison");
            table.setAutoCreateRowSorter(true);JScrollPane scroll=new JScrollPane(table);scroll.setPreferredSize(new Dimension(620,240));
            groups.addTab(group+" ("+changed+")",scroll);
        }
        panel.add(groups,BorderLayout.CENTER);return panel;
    }
    private static JFileChooser chooser() { JFileChooser chooser = new JFileChooser();chooser.setFileFilter(new FileNameExtensionFilter("Gantry machine settings (*.json)", "json"));return chooser; }
    private static void disconnected(Component parent) { JOptionPane.showMessageDialog(parent,"Disconnect the plotter and finish any machine operation before importing settings.","Import machine settings",JOptionPane.INFORMATION_MESSAGE); }
    private static void error(Component parent, Exception error) { JOptionPane.showMessageDialog(parent,error.getMessage(),"Machine settings",JOptionPane.ERROR_MESSAGE); }
}
