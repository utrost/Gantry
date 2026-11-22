package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.plotter.*;

import java.awt.*;

import javax.swing.*;

/** Explicit transient review; never writes firmware or enlarges a calibrated profile. */
final class FirmwareReviewDialog {
    static void show(
            Component parent,
            GcodeBackend backend,
            GcodeBackend.SafetySnapshot snapshot,
            Runnable approved) {
        var limits = snapshot.limits();
        var options = backend.getOptions();
        JComboBox<FirmwareEnvelope.Space> space = new JComboBox<>(FirmwareEnvelope.Space.values());
        space.setSelectedIndex(-1);
        space.setName("firmwareCoordinateSpace");
        space.getAccessibleContext().setAccessibleName("Firmware machine-coordinate convention");
        JCheckBox reference =
                new JCheckBox("I verified the machine reference and paper/work origin");
        reference.setName("firmwareReferenceConfirmed");
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(
                new JLabel(
                        "<html><body style='width:460px'><h3>Review firmware and work"
                            + " origin</h3>Firmware XY travel: "
                                + limits.travelX()
                                + " × "
                                + limits.travelY()
                                + " mm<br>"
                                + "Firmware XY maximum feeds: "
                                + limits.feedX()
                                + " / "
                                + limits.feedY()
                                + " mm/min<br>"
                                + "Configured profile (unchanged): "
                                + options.machineWidth
                                + " × "
                                + options.machineHeight
                                + " mm; draw/travel "
                                + options.feedRateDraw
                                + " / "
                                + options.feedRateTravel
                                + " mm/min<br>"
                                + "Reported WCO: "
                                + snapshot.offsetX()
                                + ", "
                                + snapshot.offsetY()
                                + " mm<br>Reference: "
                                + (snapshot.homed()
                                        ? "Home completed this connection"
                                        : "unverified power-on/reference position")
                                + "<p>Homing seeks switches. A machine reference is not the paper"
                                + " origin. Return to work origin only commands X0 Y0. Reading"
                                + " settings proves none of these physical facts.<p>Select the"
                                + " actual firmware machine-coordinate ranges; do not infer them"
                                + " from the picture or homing direction mask. Firmware and smaller"
                                + " profile limits are intersected. Feed checks use a conservative"
                                + " XY cap. No settings are written or automatically"
                                + " capped.</body></html>"));
        body.add(space);
        body.add(reference);
        JOptionPane pane =
                new JOptionPane(body, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = pane.createDialog(parent, "Review firmware limits");
        dialog.setVisible(true);
        dialog.dispose();
        if (!Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue())) return;
        try {
            backend.approveSafetyReview(
                    snapshot,
                    (FirmwareEnvelope.Space) space.getSelectedItem(),
                    reference.isSelected());
            approved.run();
        } catch (RuntimeException failure) {
            JOptionPane.showMessageDialog(
                    parent,
                    failure.getMessage(),
                    "Firmware review not applied",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
