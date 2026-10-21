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
            var g = incoming.gcode;
            String summary = String.format(java.util.Locale.ROOT,
                    "Machine bed: %.1f × %.1f mm\nOrigin: %s · %s\nConnection: %s at %d baud\nPen mode: %s\nDraw / travel: %d / %d mm/min\nWatercolor stations: %d\n\nThis replaces Gantry's machine settings. Check the local serial port before connecting.\nApplication preferences, simulation mode and preflight policy stay unchanged.\nController firmware settings (such as GRBL $100/$101) are not included or written.",
                    g.machineWidth,g.machineHeight,incoming.machineOrigin,incoming.orientation,g.serialPort,g.baudRate,g.penMode,g.feedRateDraw,g.feedRateTravel,incoming.stations.size());
            if (JOptionPane.showOptionDialog(parent, summary, "Import machine settings",
                    JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE,null,new String[]{"Apply settings","Cancel"},"Cancel") != 0) return;
            if (!available.getAsBoolean()) { disconnected(parent);return; }
            apply.accept(incoming);
        } catch (IOException | RuntimeException ex) { error(parent, ex); }
    }
    private static JFileChooser chooser() { JFileChooser chooser = new JFileChooser();chooser.setFileFilter(new FileNameExtensionFilter("Gantry machine settings (*.json)", "json"));return chooser; }
    private static void disconnected(Component parent) { JOptionPane.showMessageDialog(parent,"Disconnect the plotter and finish any machine operation before importing settings.","Import machine settings",JOptionPane.INFORMATION_MESSAGE); }
    private static void error(Component parent, Exception error) { JOptionPane.showMessageDialog(parent,error.getMessage(),"Machine settings",JOptionPane.ERROR_MESSAGE); }
}
