package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.app.plot.GantryConfig;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/** Guided editor for the existing machine settings panels. */
final class SetupWorkflow {
    private final GantryConfig initial;
    private final Consumer<GantryConfig> saveAndApply;
    private final Runnable calibrate;

    SetupWorkflow(GantryConfig initial, Consumer<GantryConfig> saveAndApply, Runnable calibrate) {
        this.initial = initial;
        this.saveAndApply = saveAndApply;
        this.calibrate = calibrate;
    }

    boolean show(Window owner) { return show(owner, true); }

    boolean show(Window owner, boolean calibrateAfterSetup) {
        SettingsPanel settings = new SettingsPanel();
        settings.loadConfig(initial);
        JCheckBox calibrateNext = new JCheckBox(
                "Continue to axis calibration now (connects to the machine)", calibrateAfterSetup);
        JPanel done = new JPanel();
        done.setLayout(new BoxLayout(done, BoxLayout.Y_AXIS));
        done.add(new JLabel("<html><body style='width:430px'><h3>All set</h3>Click <b>Finish</b> "
                + "to save and apply these settings. Refill stations remain available under "
                + "<i>Settings &gt; Preferences…</i>.</body></html>"));
        done.add(Box.createVerticalStrut(12));
        done.add(calibrateNext);

        List<WizardStep> steps = List.of(
                new PanelStep("Welcome", wrap("<html><h3>Machine setup</h3>Configure the connection, "
                        + "bed geometry and origin, then pen and speeds.</html>")),
                new PanelStep("Connection", wrap(settings.connectionPanel())),
                new PanelStep("Geometry & origin", wrap(settings.geometryPanel())),
                new PanelStep("Pen & speeds", wrap(settings.penPanel())),
                new PanelStep("Done", wrap(done)));
        WizardDialog wizard = new WizardDialog(owner, "Machine Setup", steps);
        wizard.setVisible(true);
        if (wizard.finishedSuccessfully()) {
            saveAndApply.accept(settings.toConfig());
            if (calibrateNext.isSelected()) calibrate.run();
            return true;
        }
        return false;
    }

    private static JComponent wrap(String html) {
        JLabel label = new JLabel(html.replaceFirst("(?i)<html>", "<html><body style='width:430px'>"));
        label.setVerticalAlignment(SwingConstants.TOP);
        return wrap(label);
    }

    private static JComponent wrap(JComponent content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(content, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
    }
}
