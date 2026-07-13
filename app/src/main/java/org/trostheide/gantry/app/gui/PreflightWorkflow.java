package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Swing implementation of the pre-plot checklist, decoupled from the main window. */
final class PreflightWorkflow {
    record Actions(BooleanSupplier hasDocument, BooleanSupplier connected, Runnable connect,
                   Runnable home, Runnable frame, Runnable start, String penMode) { }

    private final Actions actions;

    PreflightWorkflow(Actions actions) { this.actions = actions; }

    void show(Window owner, Component parent) {
        if (!actions.hasDocument().getAsBoolean()) {
            JOptionPane.showMessageDialog(parent, "Load or import a drawing first.",
                    "Pre-Plot Checklist", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ConnectStep connect = new ConnectStep();
        ChecklistStep checklist = new ChecklistStep();
        List<WizardStep> steps = List.of(connect, new HomeStep(), new FrameStep(), checklist, new ConfirmStep());
        WizardDialog wizard = new WizardDialog(owner, "Pre-Plot Checklist", steps);
        connect.owner = wizard;
        checklist.owner = wizard;
        wizard.setVisible(true);
        if (wizard.finishedSuccessfully()) actions.start().run();
    }

    private abstract static class Step implements WizardStep {
        final JPanel panel = new JPanel(new BorderLayout(0, 10));
        Step(String message) {
            panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            panel.add(new JLabel("<html>" + message + "</html>"), BorderLayout.NORTH);
        }
        @Override public JComponent panel() { return panel; }
    }

    private final class ConnectStep extends Step {
        final JLabel status = new JLabel();
        WizardDialog owner;
        Timer poll;
        ConnectStep() {
            super("Connect to the plotter before continuing.");
            JButton button = new JButton("Connect");
            button.addActionListener(e -> { actions.connect().run(); refresh(); });
            panel.add(row(button, status), BorderLayout.CENTER);
        }
        void refresh() {
            status.setText(actions.connected().getAsBoolean() ? "Connected." : "Not connected.");
            if (owner != null) owner.updateNextEnabled();
        }
        @Override public String title() { return "Connect"; }
        @Override public boolean canAdvance() { return actions.connected().getAsBoolean(); }
        @Override public void onEnter() { refresh(); poll = new Timer(300, e -> refresh()); poll.start(); }
        @Override public void onLeave() { if (poll != null) poll.stop(); poll = null; }
    }

    private final class HomeStep extends Step {
        HomeStep() {
            super("Run the homing cycle so the machine knows where it is.");
            JLabel status = new JLabel("Not homed yet.");
            JButton button = new JButton("Home");
            button.addActionListener(e -> {
                actions.home().run();
                status.setText("Homing requested - check the console for completion.");
            });
            panel.add(row(button, status), BorderLayout.CENTER);
        }
        @Override public String title() { return "Home"; }
        @Override public boolean isOptional() { return true; }
    }

    private final class FrameStep extends Step {
        FrameStep() {
            super("Trace the job's outline with the pen up, to check it lines up with the taped-down paper. "
                    + "Repeat as many times as you like.");
            JLabel status = new JLabel(" ");
            JButton button = new JButton("Frame the job");
            button.addActionListener(e -> { actions.frame().run(); status.setText("Tracing outline..."); });
            panel.add(row(button, status), BorderLayout.CENTER);
        }
        @Override public String title() { return "Frame the job"; }
        @Override public boolean isOptional() { return true; }
    }

    private final class ChecklistStep implements WizardStep {
        final JPanel panel = new JPanel();
        final List<JCheckBox> checks = new ArrayList<>();
        WizardDialog owner;
        ChecklistStep() {
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            String pen = switch (actions.penMode()) {
                case "zaxis" -> "Z-axis pen holder";
                case "m3m5" -> "spindle-driven pen holder";
                default -> "servo pen lift";
            };
            for (String label : List.of("Correct pen installed for the configured " + pen,
                    "Pen lifts and lowers correctly (jog it once if unsure)",
                    "Paper is taped down flat at the framed area",
                    "Correct layers/stations are selected for this job")) {
                JCheckBox box = new JCheckBox(label);
                box.addActionListener(e -> { if (owner != null) owner.updateNextEnabled(); });
                checks.add(box); panel.add(box); panel.add(Box.createVerticalStrut(6));
            }
        }
        @Override public String title() { return "Physical checklist"; }
        @Override public JComponent panel() { return panel; }
        @Override public boolean canAdvance() { return checks.stream().allMatch(JCheckBox::isSelected); }
    }

    private static final class ConfirmStep extends Step {
        ConfirmStep() { super("Everything checks out. Click Finish to start plotting "
                + "(this triggers the same Start Plot action as the main toolbar button)."); }
        @Override public String title() { return "Confirm & start"; }
    }

    private static JPanel row(JComponent... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (JComponent component : components) row.add(component);
        return row;
    }
}
