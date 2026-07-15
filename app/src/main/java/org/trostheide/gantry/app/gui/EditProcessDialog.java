package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.svgtoolbox.Config;

import javax.swing.*;
import java.awt.*;

/**
 * Modal dialog for "Edit &gt; Re-process Source SVG...": re-runs the processors against the
 * originally imported SVG file, so the user can tweak them without re-importing from scratch.
 *
 * <p>The body is the shared {@link ToolboxOptionsPanel}, so this dialog exposes exactly the same
 * option set as the import dialog's "Process artwork" tab (they used to diverge).
 */
public final class EditProcessDialog extends JDialog {

    private final ToolboxOptionsPanel optionsPanel;
    private Config result;
    private boolean processingEnabled;

    public EditProcessDialog(Window owner) {
        this(owner, null);
    }

    public EditProcessDialog(Window owner, java.io.File sourceSvg) {
        this(owner, sourceSvg, null);
    }

    public EditProcessDialog(Window owner, java.io.File sourceSvg, Config initialConfig) {
        super(owner, "Adjust artwork processing", ModalityType.APPLICATION_MODAL);
        optionsPanel = new ToolboxOptionsPanel(SvgFillColors.read(sourceSvg));
        if (initialConfig != null) optionsPanel.applyConfig(initialConfig);

        JButton okBtn = new JButton("Apply");
        JButton resetBtn = new JButton("Reset to original artwork");
        JButton cancelBtn = new JButton("Cancel");
        okBtn.addActionListener(e -> onOk());
        resetBtn.addActionListener(e -> optionsPanel.resetToOriginal());
        cancelBtn.addActionListener(e -> dispose());
        resetBtn.setToolTipText("Turn off processors so Apply restores the originally imported paths");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(resetBtn);
        buttons.add(okBtn);
        buttons.add(cancelBtn);

        JPanel padded = new JPanel(new BorderLayout(8, 0));
        padded.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane controls = new JScrollPane(optionsPanel);
        controls.setBorder(null);
        controls.getVerticalScrollBar().setUnitIncrement(12);
        padded.add(controls, BorderLayout.WEST);
        padded.add(new ProcessingPreviewPanel(sourceSvg, optionsPanel), BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(padded, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(okBtn);
        setSize(980, 720);
        setMinimumSize(new Dimension(760, 560));
        setLocationRelativeTo(owner);
    }

    private void onOk() {
        try {
            processingEnabled = optionsPanel.hasProcessingEnabled();
            result = optionsPanel.buildConfig();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid option", JOptionPane.ERROR_MESSAGE);
            return;
        }
        dispose();
    }

    /** Shows the dialog and returns the chosen config, or {@code null} if cancelled. */
    public Config showDialog() {
        setVisible(true);
        return result;
    }

    /** False when the chosen goal is Keep artwork unchanged. */
    public boolean processingEnabled() { return processingEnabled; }
}
