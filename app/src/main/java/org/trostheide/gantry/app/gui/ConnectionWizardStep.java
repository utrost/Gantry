package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.awt.*;
import java.util.function.BooleanSupplier;

/** Reusable wizard step which establishes the application's existing connection. */
final class ConnectionWizardStep implements WizardStep {
    private final BooleanSupplier connected;
    private final Runnable connectAction;
    private final JPanel panel = new JPanel(new BorderLayout(0, 10));
    private final JLabel status = new JLabel();
    private WizardDialog owner;
    private Timer poll;

    ConnectionWizardStep(BooleanSupplier connected, Runnable connectAction) {
        this.connected = connected;
        this.connectAction = connectAction;
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        panel.add(new JLabel("<html>Connect to the plotter before continuing.</html>"), BorderLayout.NORTH);
        JButton button = new JButton("Connect");
        button.addActionListener(e -> { connectAction.run(); refresh(); });
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(button); row.add(status); panel.add(row, BorderLayout.CENTER);
    }

    void attachOwner(WizardDialog owner) { this.owner = owner; }
    private void refresh() {
        status.setText(connected.getAsBoolean() ? "Connected." : "Not connected.");
        if (owner != null) owner.updateNextEnabled();
    }
    @Override public String title() { return "Connect"; }
    @Override public JComponent panel() { return panel; }
    @Override public boolean canAdvance() { return connected.getAsBoolean(); }
    @Override public void onEnter() { refresh(); poll = new Timer(300, e -> refresh()); poll.start(); }
    @Override public void onLeave() { if (poll != null) poll.stop(); poll = null; }
}
