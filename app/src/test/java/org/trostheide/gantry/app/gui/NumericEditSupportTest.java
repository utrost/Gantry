package org.trostheide.gantry.app.gui;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.text.ParseException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NumericEditSupportTest {
    @Test void rejectsPartialNonFiniteAndOutOfRangeInputWithoutChangingTheValue() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JSpinner spinner = spinner();
            JFormattedTextField field = field(spinner);
            for (String invalid : new String[]{"12garbage", "12 mm", "1.2.3", "1e2", "NaN", "∞", "", "-1", "1001"}) {
                field.setText(invalid);
                assertFalse(field.getInputVerifier().verify(field), invalid);
                assertEquals(10.0, spinner.getValue(), invalid);
                assertEquals("error", field.getClientProperty("JComponent.outline"));
                assertThrows(ParseException.class, field::commitEdit, invalid);
            }
        });
    }

    @Test void commitIsOneChangeAndEscapeRestoresWithoutAnEdit() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger edits = new AtomicInteger();
            JSpinner spinner = spinner();
            spinner.addChangeListener(e -> edits.incrementAndGet());
            JFormattedTextField field = field(spinner);
            field.setText(" 12.5 ");
            action(field, "ENTER");
            assertEquals(12.5, spinner.getValue());
            assertEquals(1, edits.get());
            assertTrue(field.getInputVerifier().verify(field));
            assertEquals(1, edits.get(), "focus loss after Enter must not add an edit");
            field.setText("80");
            action(field, "ESCAPE");
            assertEquals(12.5, field.getValue());
            assertEquals(1, edits.get());
            field.setText("bad");
            action(field, "ENTER");
            assertNotNull(field.getAccessibleContext().getAccessibleDescription());
            action(field, "ESCAPE");
            assertNull(field.getClientProperty("JComponent.outline"));
            assertNull(field.getAccessibleContext().getAccessibleDescription());
        });
    }

    @Test void localeSpecificDecimalSeparatorStillWorks() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(10.0, 0.0, 1000.0, 1.0));
            JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0.###");
            editor.getFormat().setDecimalFormatSymbols(java.text.DecimalFormatSymbols.getInstance(Locale.GERMANY));
            spinner.setEditor(editor);
            NumericEditSupport.install(spinner, ignored -> {});
            field(spinner).setText("12,5");
            action(field(spinner), "ENTER");
            assertEquals(12.5, spinner.getValue());
        });
    }

    @Test void stopBypassesInvalidPropertyFocusVerification() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton stop = new JButton("Stop");
            WorkspaceStyle.action(stop, WorkspaceStyle.ActionRole.STOP, true);
            assertFalse(stop.getVerifyInputWhenFocusTarget());
            JButton apply = new JButton("Apply");
            WorkspaceStyle.primary(apply);
            assertTrue(apply.getVerifyInputWhenFocusTarget());
        });
    }

    private static JSpinner spinner() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(10.0, 0.0, 1000.0, 1.0));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0.###");
        editor.getFormat().setDecimalFormatSymbols(java.text.DecimalFormatSymbols.getInstance(Locale.ROOT));
        spinner.setEditor(editor);
        NumericEditSupport.install(spinner, ignored -> {});
        return spinner;
    }
    private static JFormattedTextField field(JSpinner spinner) {
        return ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
    }
    private static void action(JFormattedTextField field, String key) {
        Object name = field.getInputMap().get(KeyStroke.getKeyStroke(key));
        field.getActionMap().get(name).actionPerformed(new ActionEvent(field, ActionEvent.ACTION_PERFORMED, key));
    }
}
