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
    { WorkspaceStyle.action(stopButton, WorkspaceStyle.ActionRole.STOP, true); }
    private final java.util.Set<Integer> skipped = new java.util.LinkedHashSet<>();
    private final JLabel skippedLabel = new JLabel();
    private final javax.swing.JTextArea exitNotice = WorkspaceStyle.note("");
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
        JPanel footer = new JPanel(new BorderLayout(0, 4));
        JPanel notices = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        notices.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
        skippedLabel.setVisible(false);
        exitNotice.setVisible(false);
        notices.add(skippedLabel);
        notices.add(exitNotice);
        footer.add(notices, BorderLayout.NORTH);
        footer.add(buttons, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
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
        updateSkippedSummary();
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
        nextBtn.setEnabled(!closePending && !operationBusy.getAsBoolean() && steps.get(current).canAdvance());
    }

    private void leaveStep() {
        if (!stepEntered) return;
        stepEntered = false;
        steps.get(current).onLeave();
    }

    void operationControls(java.util.function.BooleanSupplier busy, Runnable stop, java.util.function.BooleanSupplier connected) {
        operationBusy=busy;stopOperation=stop;
        exitNotice.setText("Closing stops the active operation. Motion and settings already applied are not undone.");
        exitNotice.setVisible(true);
        stopButton.setVisible(true);
        if (operationTimer != null) operationTimer.stop();
        operationTimer=new javax.swing.Timer(100,e->{
            boolean active=operationBusy.getAsBoolean();
            stopButton.setEnabled(active); backBtn.setEnabled(!closePending && !active && current>0);
            skipBtn.setEnabled(!closePending && !active); updateNextEnabled();
            updateMotionAvailability(cardPanel,!connected.getAsBoolean()?"Connect a plotter first.":active?"Wait for the current operation to finish, or use Stop.":null);
            if(closePending && !active)dispose();
        });
        operationTimer.start();
    }

    private static void updateMotionAvailability(java.awt.Container parent,String reason) {
        for(java.awt.Component child:parent.getComponents()) {
            if(child instanceof JButton button && button.getClientProperty("Gantry.actionRole")==WorkspaceStyle.ActionRole.MOTION)
                WorkspaceStyle.availability(button,reason);
            if(child instanceof java.awt.Container nested)updateMotionAvailability(nested,reason);
        }
    }

    @Override public void dispose() {
        leaveStep();
        if(operationBusy.getAsBoolean()) {
            if (!closePending) stopOperation.run();
            closePending=true;
            backBtn.setEnabled(false); nextBtn.setEnabled(false); skipBtn.setEnabled(false);
            progressLabel.setText("Stopping operation before closing…");return;
        }
        if(operationTimer!=null)operationTimer.stop();
        super.dispose();
    }

    private void onBack() {
        if(closePending || operationBusy.getAsBoolean() || current==0)return;
        leaveStep();
        showStep(current - 1);
    }

    private void onNext() {
        WizardStep step = steps.get(current);
        if (closePending || operationBusy.getAsBoolean() || !step.canAdvance()) {
            return;
        }
        skipped.remove(current);
        updateSkippedSummary();
        leaveStep();
        if (current == steps.size() - 1) {
            finished = true;
            dispose();
            return;
        }
        showStep(current + 1);
    }

    private void onSkip() {
        if(closePending || operationBusy.getAsBoolean() || !steps.get(current).isOptional())return;
        skipped.add(current);
        updateSkippedSummary();
        leaveStep();
        if (current == steps.size() - 1) {
            finished = true;
            dispose();
            return;
        }
        showStep(current + 1);
    }

    java.util.List<String> skippedSteps() {
        return skipped.stream().sorted().map(index -> steps.get(index).title()).toList();
    }

    private void updateSkippedSummary() {
        String summary = "Skipped (not verified): " + String.join(", ", skippedSteps());
        skippedLabel.setText(summary);
        skippedLabel.setToolTipText(summary);
        skippedLabel.getAccessibleContext().setAccessibleDescription(summary);
        skippedLabel.setVisible(!skipped.isEmpty());
    }

    private void onCancel() {
        int choice = JOptionPane.showConfirmDialog(this, exitNotice.isVisible()
                ? "Close this wizard? Applied machine changes and motion cannot be undone."
                : "Close this wizard without completing it?",
                "Cancel wizard", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            finished = false;
            dispose();
        }
    }
}
