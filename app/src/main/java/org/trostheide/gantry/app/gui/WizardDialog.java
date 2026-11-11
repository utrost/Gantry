package org.trostheide.gantry.app.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.List;

/**
 * Generic step-sequencing shell shared by the guided workflows (pre-plot checklist, machine
 * setup, axis calibration). Owns navigation (Back/Next/Skip/Cancel) and a "Step N of M" progress
 * trail; has no plotting or hardware knowledge of its own — each concrete wizard supplies its own
 * {@link WizardStep}s and this class just sequences them.
 */
final class WizardDialog extends JDialog {

    private final List<WizardStep> steps;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);
    private final JLabel progressLabel = new JLabel();
    private final JButton backBtn = new JButton("< Back");
    private final JButton nextBtn = new JButton("Next >");
    private final JButton skipBtn = new JButton("Skip");
    private final JButton cancelBtn = new JButton("Cancel");
    private int current = 0;
    private boolean finished = false;
    private boolean stepEntered;
    private java.util.function.BooleanSupplier operationBusy = () -> false;
    private Runnable stopOperation = () -> {};
    private javax.swing.Timer operationTimer;
    private boolean closePending;
    private final JButton stopButton = new JButton("Stop operation");
    private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));

    WizardDialog(Window owner, String title, List<WizardStep> steps) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Wizard needs at least one step");
        }
        this.steps = List.copyOf(steps);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getRootPane().registerKeyboardAction(e -> dispose(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);

        for (int i = 0; i < steps.size(); i++) {
            cardPanel.add(steps.get(i).panel(), String.valueOf(i));
        }

        progressLabel.setFont(progressLabel.getFont().deriveFont(Font.BOLD));
        progressLabel.setHorizontalAlignment(SwingConstants.LEFT);
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        top.add(progressLabel, BorderLayout.CENTER);

        backBtn.addActionListener(e -> onBack());
        nextBtn.addActionListener(e -> onNext());
        skipBtn.addActionListener(e -> onSkip());
        cancelBtn.addActionListener(e -> onCancel());

        stopButton.setVisible(false);
        stopButton.addActionListener(e -> stopOperation.run());
        buttons.add(stopButton);
        buttons.add(cancelBtn);
        buttons.add(skipBtn);
        buttons.add(backBtn);
        buttons.add(nextBtn);

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(cardPanel, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(nextBtn);

        setSize(new Dimension(560, 420));
        setLocationRelativeTo(owner);
        showStep(0);
    }

    /** True once the wizard ran to completion (last step's Next/Finish was clicked), false if cancelled. */
    boolean finishedSuccessfully() {
        return finished;
    }

    private void showStep(int index) {
        current = index;
        WizardStep step = steps.get(current);
        cardLayout.show(cardPanel, String.valueOf(current));
        progressLabel.setText("Step " + (current + 1) + " of " + steps.size() + ": " + step.title());
        backBtn.setEnabled(current > 0);
        skipBtn.setVisible(step.isOptional());
        nextBtn.setText(current == steps.size() - 1 ? "Finish" : "Next >");
        stepEntered = true;
        step.onEnter();
        updateNextEnabled();
    }

    /** Re-evaluates whether {@code Next} should be enabled; call after UI state changes inside a step. */
    void updateNextEnabled() {
        nextBtn.setEnabled(!operationBusy.getAsBoolean() && steps.get(current).canAdvance());
    }

    private void leaveStep() {
        if (!stepEntered) return;
        stepEntered = false;
        steps.get(current).onLeave();
    }

    void operationControls(java.util.function.BooleanSupplier busy, Runnable stop) {
        operationBusy=busy;stopOperation=stop;
        stopButton.setVisible(true);
        operationTimer=new javax.swing.Timer(100,e->{
            boolean active=operationBusy.getAsBoolean();
            stopButton.setEnabled(active); backBtn.setEnabled(!active && current>0);
            skipBtn.setEnabled(!active); updateNextEnabled();
            if(closePending && !active)dispose();
        });
        operationTimer.start();
    }

    @Override public void dispose() {
        leaveStep();
        if(operationBusy.getAsBoolean()) {
            closePending=true;stopOperation.run();progressLabel.setText("Stopping operation before closing…");return;
        }
        if(operationTimer!=null)operationTimer.stop();
        super.dispose();
    }

    private void onBack() {
        if(operationBusy.getAsBoolean() || current==0)return;
        leaveStep();
        showStep(current - 1);
    }

    private void onNext() {
        WizardStep step = steps.get(current);
        if (operationBusy.getAsBoolean() || !step.canAdvance()) {
            return;
        }
        leaveStep();
        if (current == steps.size() - 1) {
            finished = true;
            dispose();
            return;
        }
        showStep(current + 1);
    }

    private void onSkip() {
        if(operationBusy.getAsBoolean())return;
        leaveStep();
        if (current == steps.size() - 1) {
            finished = true;
            dispose();
            return;
        }
        showStep(current + 1);
    }

    private void onCancel() {
        int choice = JOptionPane.showConfirmDialog(this, "Close this wizard? Applied machine changes and motion cannot be undone.",
                "Cancel wizard", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            finished = false;
            dispose();
        }
    }
}
