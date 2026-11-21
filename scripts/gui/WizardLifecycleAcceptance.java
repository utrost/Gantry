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
        stationNudgeStartsFromStoredStation();
        System.exit(0);
    }
    private static void stationNudgeStartsFromStoredStation() throws Exception {
        var config = new org.trostheide.gantry.app.plot.GantryConfig();
        config.stations.clear();
        config.stations.put("paint", new org.trostheide.gantry.app.plot.StationConfig(100, 100, 0, "simple_dip"));
        double[] position = {250, 150}; // deliberately nowhere near the configured station
        AtomicBoolean penUp = new AtomicBoolean(), persisted = new AtomicBoolean();
        var backend = new org.trostheide.gantry.plotter.MockPlotterBackend() {
            public void penup() { penUp.set(true); }
            public void moveto(double x, double y) { position[0]=x; position[1]=y; }
            public void move(double dx, double dy) { position[0]+=dx; position[1]+=dy; }
        };
        backend.connect();
        Throwable[] failure = {null};
        SwingUtilities.invokeAndWait(() -> {
            var workflow = new StationTestWorkflow(config, () -> true, action -> action.accept(backend),
                    () -> persisted.set(true), line -> {}, () -> false, () -> {});
            int[] stage = {0}, polls = {0};
            Timer driver = new Timer(30, null);
            driver.addActionListener(event -> {
                WizardDialog wizard = null;
                for (var window : java.awt.Window.getWindows())
                    if (window instanceof WizardDialog d && d.isVisible() && d.getTitle().equals("Test Color Stations")) wizard=d;
                try {
                    if (++polls[0]>100) throw new AssertionError("Station wizard did not complete");
                    if (wizard==null) return;
                    if (stage[0]==0) { button(wizard,"Next >").doClick(); stage[0]++; }
                    else if (stage[0]==1) { button(wizard,"+X").doClick(); stage[0]++; }
                    else {
                        if (!penUp.get() || position[0]!=101 || position[1]!=100)
                            throw new AssertionError("Nudge moved relative to the machine instead of the station: " + java.util.Arrays.toString(position));
                        button(wizard,"Next >").doClick(); button(wizard,"Finish").doClick(); driver.stop();
                    }
                } catch (Throwable error) {
                    failure[0]=error; driver.stop(); if(wizard!=null)wizard.dispose();
                }
            });
            driver.start();
            workflow.show(null, null);
            driver.stop();
        });
        if (failure[0]!=null) throw new AssertionError("Station nudge regression", failure[0]);
        var station=config.stations.get("paint");
        if (!persisted.get() || station.x()!=101 || station.y()!=100) throw new AssertionError("Nudged station was not saved");
        System.out.println("PASS station nudge starts at stored coordinates, raises pen, and saves the submitted target");
    }
    private static JButton button(java.awt.Container root, String text) {
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return button;
            if (child instanceof java.awt.Container nested) { JButton result=button(nested,text); if(result!=null)return result; }
        }
        return null;
    }
}
