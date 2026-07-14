package org.trostheide.gantry.app.gui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.Component;

/** The optional startup welcome that routes beginners into guided practice or machine setup. */
final class StartupWelcomeDialog {

    enum Action { GUIDED_PRACTICE, MACHINE_SETUP, DISMISSED }

    record Result(Action action, boolean showOnStartup) { }

    private StartupWelcomeDialog() { }

    static Result show(Component parent, boolean showOnStartup) {
        JCheckBox showAgain = new JCheckBox("Show this welcome when Gantry starts", showOnStartup);
        JPanel message = new JPanel();
        message.setLayout(new BoxLayout(message, BoxLayout.Y_AXIS));
        message.add(new JLabel("<html>Welcome to Gantry. Adding artwork cannot move a machine.<br><br>"
                + "Guided practice will configure Gantry, load a supplied drawing, and use the "
                + "no-hardware mock plotter by default.</html>"));
        message.add(showAgain);
        message.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));

        Object[] options = {"Start guided practice", "Machine setup only", "Close"};
        int choice = JOptionPane.showOptionDialog(parent, message, "Your first plot",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        return resultForChoice(choice, showAgain.isSelected());
    }

    static Result resultForChoice(int choice, boolean showOnStartup) {
        Action action = switch (choice) {
            case 0 -> Action.GUIDED_PRACTICE;
            case 1 -> Action.MACHINE_SETUP;
            default -> Action.DISMISSED;
        };
        return new Result(action, showOnStartup);
    }
}
