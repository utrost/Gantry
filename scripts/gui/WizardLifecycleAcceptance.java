package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Native Swing lifecycle check; no machine or profile is accessed. */
public final class WizardLifecycleAcceptance {
    public static void main(String[] args) throws Exception {
        AtomicInteger leaves = new AtomicInteger(), stops = new AtomicInteger(), polls = new AtomicInteger();
        AtomicBoolean busy = new AtomicBoolean(true);
        WizardDialog[] dialog = new WizardDialog[1];
        Timer poll = new Timer(20, e -> polls.incrementAndGet());
        SwingUtilities.invokeAndWait(() -> {
            WizardStep step = new WizardStep() {
                public String title() { return "Lifecycle check"; }
                public JComponent panel() { return new JPanel(); }
                public void onEnter() { poll.start(); }
                public void onLeave() { leaves.incrementAndGet(); poll.stop(); }
            };
            dialog[0] = new WizardDialog(null, "Lifecycle check", List.of(step));
            dialog[0].operationControls(busy::get, stops::incrementAndGet, ()->true);
            dialog[0].addNotify();
            dialog[0].dispose();
            if (!dialog[0].isDisplayable()) throw new AssertionError("closed before operation cleanup");
            if (leaves.get()!=1 || stops.get()!=1 || poll.isRunning()) throw new AssertionError("step cleanup/Stop missing");
            dialog[0].dispose();
            if (stops.get()!=1) throw new AssertionError("repeated close requested duplicate Stop");
        });
        int count = polls.get();
        busy.set(false);
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(() -> {
            if (dialog[0].isDisplayable() || leaves.get()!=1 || polls.get()!=count)
                throw new AssertionError("closed wizard still active");
            dialog[0].dispose();
            if (leaves.get()!=1) throw new AssertionError("duplicate onLeave");
        });
        System.out.println("PASS wizard Close stops polling, requests Stop, waits for cleanup and leaves exactly once");
        SwingUtilities.invokeAndWait(() -> {
            for (String exit : List.of("window", "escape", "finish", "skip")) {
                AtomicInteger entered = new AtomicInteger(), left = new AtomicInteger();
                Timer timer = new Timer(20, e -> {});
                WizardStep step = new WizardStep() {
                    public String title() { return "Optional calibration"; }
                    public JComponent panel() { return new JPanel(); }
                    public boolean isOptional() { return true; }
                    public void onEnter() { entered.incrementAndGet(); timer.start(); }
                    public void onLeave() { left.incrementAndGet(); timer.stop(); }
                };
                WizardDialog wizard = new WizardDialog(null, "Exit check", List.of(step));
                wizard.addNotify();
                switch (exit) {
                    case "window" -> wizard.dispatchEvent(new java.awt.event.WindowEvent(wizard, java.awt.event.WindowEvent.WINDOW_CLOSING));
                    case "escape" -> {
                        Object key = wizard.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("ESCAPE"));
                        wizard.getRootPane().getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(wizard, 0, "escape"));
                    }
                    case "finish" -> button(wizard, "Finish").doClick();
                    case "skip" -> button(wizard, "Skip").doClick();
                }
                if (wizard.isDisplayable() || timer.isRunning() || entered.get()!=1 || left.get()!=1)
                    throw new AssertionError("Exit lifecycle failed: " + exit);
                if (exit.equals("skip") != wizard.skippedSteps().equals(List.of("Optional calibration")))
                    throw new AssertionError("Skipped-step reporting failed: " + exit);
                if (wizard.finishedSuccessfully() != (exit.equals("finish") || exit.equals("skip")))
                    throw new AssertionError("Completion result failed: " + exit);
                System.out.println("PASS wizard " + exit + " cleans up once and reports its outcome");
            }
            AtomicInteger enters = new AtomicInteger(), leavesAgain = new AtomicInteger();
            WizardStep step = new WizardStep() {
                public String title() { return "Direction check"; }
                public JComponent panel() { return new JPanel(); }
                public boolean isOptional() { return true; }
                public void onEnter() { enters.incrementAndGet(); }
                public void onLeave() { leavesAgain.incrementAndGet(); }
            };
            WizardDialog wizard = new WizardDialog(null, "Navigation check", List.of(step, new PanelStep("Done", new JPanel())));
            wizard.addNotify();
            button(wizard, "Skip").doClick();
            if (!wizard.skippedSteps().equals(List.of("Direction check"))) throw new AssertionError("Skip not recorded");
            button(wizard, "< Back").doClick();
            button(wizard, "Next >").doClick();
            if (!wizard.skippedSteps().isEmpty() || enters.get()!=2 || leavesAgain.get()!=2) throw new AssertionError("Revisited step not completed cleanly");
            button(wizard, "Finish").doClick();
            System.out.println("PASS wizard Back/Next revisits and clears resolved skipped steps");
        });
        System.exit(0);
    }
    private static JButton button(java.awt.Container root, String text) {
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return button;
            if (child instanceof java.awt.Container nested) { JButton result=button(nested,text); if(result!=null)return result; }
        }
        return null;
    }
}
