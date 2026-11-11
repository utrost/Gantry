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
            dialog[0].operationControls(busy::get, stops::incrementAndGet);
            dialog[0].addNotify();
            dialog[0].dispose();
            if (!dialog[0].isDisplayable()) throw new AssertionError("closed before operation cleanup");
            if (leaves.get()!=1 || stops.get()!=1 || poll.isRunning()) throw new AssertionError("step cleanup/Stop missing");
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
        System.exit(0);
    }
}
