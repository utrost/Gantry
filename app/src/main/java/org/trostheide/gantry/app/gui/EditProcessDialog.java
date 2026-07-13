package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.svgtoolbox.Config;

import javax.swing.*;
import java.awt.*;

/**
 * Modal dialog for "Edit &gt; Process SVG...": re-runs the SVGToolBox processors against the
 * originally imported SVG file, so the user can tweak them without re-importing from scratch.
 *
 * <p>The body is the shared {@link ToolboxOptionsPanel}, so this dialog exposes exactly the same
 * option set as the import dialog's "Process SVG" tab (they used to diverge). Field values are
 * remembered by that panel across reopens and across both dialogs.
 */
public final class EditProcessDialog extends JDialog {

    private final ToolboxOptionsPanel optionsPanel;
    private Config result;

    public EditProcessDialog(Window owner) {
        this(owner, null);
    }

    public EditProcessDialog(Window owner, java.io.File sourceSvg) {
        super(owner, "Process SVG", ModalityType.APPLICATION_MODAL);
        optionsPanel = new ToolboxOptionsPanel(SvgFillColors.read(sourceSvg));

        JButton okBtn = new JButton("Apply");
        JButton cancelBtn = new JButton("Cancel");
        okBtn.addActionListener(e -> onOk());
        cancelBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(okBtn);
        buttons.add(cancelBtn);

        JPanel padded = new JPanel(new BorderLayout());
        padded.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        padded.add(optionsPanel, BorderLayout.NORTH);

        setLayout(new BorderLayout());
        add(new JScrollPane(padded), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(okBtn);
        setSize(460, 640);
        setLocationRelativeTo(owner);
    }

    private void onOk() {
        try {
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
}
