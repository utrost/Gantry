package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.text.ParseException;
import java.text.ParsePosition;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import java.util.function.Consumer;

/** Explicit commit/revert behavior, including range validation and local feedback. */
final class NumericEditSupport {
    private NumericEditSupport() {}
    static void install(JSpinner spinner, Consumer<String> feedback) {
        JFormattedTextField field=((JSpinner.DefaultEditor)spinner.getEditor()).getTextField();
        field.getAccessibleContext().setAccessibleName(spinner.getAccessibleContext().getAccessibleName());
        // NumberFormatter otherwise accepts a prefix ("12garbage" becomes 12).
        // Install strict parsing in the formatter itself so commitEdit and focus traversal agree.
        NumberFormatter original = (NumberFormatter) field.getFormatter();
        NumberFormatter strict = new NumberFormatter((java.text.NumberFormat) original.getFormat()) {
            @Override public Object stringToValue(String text) throws ParseException {
                String input = text.trim();
                ParsePosition parsed = new ParsePosition(0);
                Object value = getFormat().parseObject(input, parsed);
                if (value == null || parsed.getIndex() != input.length())
                    throw new ParseException("Enter a complete number", parsed.getIndex());
                return super.stringToValue(input);
            }
        };
        strict.setValueClass(original.getValueClass());
        strict.setMinimum(original.getMinimum());
        strict.setMaximum(original.getMaximum());
        field.setFormatterFactory(new DefaultFormatterFactory(strict));
        field.setFocusLostBehavior(JFormattedTextField.PERSIST);
        java.util.function.BooleanSupplier commit=()->{
            try {
                Object value=field.getFormatter().stringToValue(field.getText());
                if(!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))throw new IllegalArgumentException();
                SpinnerNumberModel model=(SpinnerNumberModel)spinner.getModel();
                double n=number.doubleValue();
                if(model.getMinimum() instanceof Number min && n<min.doubleValue() || model.getMaximum() instanceof Number max && n>max.doubleValue())throw new IllegalArgumentException();
                spinner.setValue(value);field.setValue(spinner.getValue());field.putClientProperty("JComponent.outline",null);field.getAccessibleContext().setAccessibleDescription(null);
                double applied=((Number)spinner.getValue()).doubleValue();
                feedback.accept(Math.abs(applied-n)>.0001?"Placement constrained to "+applied+" mm by the machine bed.":"");
                return true;
            } catch(Exception invalid) {
                field.putClientProperty("JComponent.outline","error");
                SpinnerNumberModel model=(SpinnerNumberModel)spinner.getModel();
                String message="Enter a value from "+model.getMinimum()+" to "+model.getMaximum()+". Escape restores the previous value.";
                field.getAccessibleContext().setAccessibleDescription(message);feedback.accept(message);return false;
            }
        };
        field.setInputVerifier(new InputVerifier(){public boolean verify(JComponent input){return commit.getAsBoolean();}});
        field.getInputMap().put(KeyStroke.getKeyStroke("ENTER"),"commitGantryNumber");
        field.getActionMap().put("commitGantryNumber",new AbstractAction(){public void actionPerformed(ActionEvent e){commit.getAsBoolean();}});
        field.getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"),"revertGantryNumber");
        field.getActionMap().put("revertGantryNumber",new AbstractAction(){public void actionPerformed(ActionEvent e){field.setValue(spinner.getValue());field.putClientProperty("JComponent.outline",null);field.getAccessibleContext().setAccessibleDescription(null);feedback.accept("");}});
    }
}
