package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import org.trostheide.gantry.app.plot.GantryConfig;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceActionStyleTest {
    @Test void jobActionTracksConsequencesAcrossThemesAndDoesNotEnableControls() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (String theme : new String[]{"Dark", "Light"}) {
                GantryConfig config = new GantryConfig(); config.uiTheme = theme;
                WorkspaceStyle.install(config);
                JButton primary = new JButton("Start plotting"); primary.setEnabled(false);
                WorkspaceShell shell = new WorkspaceShell(new JPanel(), new JPanel(), new JPanel(),
                        new JPanel(), new JPanel(), new JPanel(), new JPanel(), primary,
                        () -> {}, () -> {}, () -> config, () -> {}, () -> {});
                for (WorkspacePresentation.Action action : new WorkspacePresentation.Action[]{
                        WorkspacePresentation.Action.START, WorkspacePresentation.Action.CONTINUE,
                        WorkspacePresentation.Action.PAUSE, WorkspacePresentation.Action.RESUME,
                        WorkspacePresentation.Action.CHECK, WorkspacePresentation.Action.ADD}) {
                    shell.render(new WorkspacePresentation.View(WorkspacePresentation.Phase.READY,
                            "Ready", "", "Action", action, true, false), true);
                    boolean movement = action == WorkspacePresentation.Action.START
                            || action == WorkspacePresentation.Action.CONTINUE || action == WorkspacePresentation.Action.RESUME;
                    assertEquals(movement, primary.getIcon() instanceof WorkspaceIcon icon && icon.isMotion());
                    assertFalse(primary.isEnabled(), "Styling must not override safety gates");
                    SwingUtilities.updateComponentTreeUI(shell);
                    assertFalse(primary.isEnabled());
                    assertEquals(WorkspaceStyle.roleFor(action), primary.getClientProperty("Gantry.actionRole"));
                }
                assertEquals(WorkspaceStyle.ActionRole.STOP, shell.stop.getClientProperty("Gantry.actionRole"));
                assertNull(primary.getAccessibleContext().getAccessibleDescription(), "No stale movement warning on Add");
            }
        });
    }
}
