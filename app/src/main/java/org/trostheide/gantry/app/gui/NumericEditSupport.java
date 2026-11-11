package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.function.Consumer;

/** Explicit commit/revert behavior, including range validation and local feedback. */
final class NumericEditSupport {
    private NumericEditSupport() {}
    static void install(JSpinner spinner, Consumer<String> feedback) {
        JFormattedTextField field=((JSpinner.DefaultEditor)spinner.getEditor()).getTextField();
        field.getAccessibleContext().setAccessibleName(spinner.getAccessibleContext().getAccessibleName());
        field.setFocusLostBehavior(JFormattedTextField.PERSIST);
        java.util.function.BooleanSupplier commit=()->{
            try {
                Object value=field.getFormatter().stringToValue(field.getText());
                if(!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))throw new IllegalArgumentException();
                SpinnerNumberModel model=(SpinnerNumberModel)spinner.getModel();
                double n=number.doubleValue();
                if(model.getMinimum() instanceof Number min && n<min.doubleValue() || model.getMaximum() instanceof Number max && n>max.doubleValue())throw new IllegalArgumentException();
                spinner.setValue(value);field.setValue(spinner.getValue());field.putClientProperty("JComponent.outline",null);
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
        field.getActionMap().put("revertGantryNumber",new AbstractAction(){public void actionPerformed(ActionEvent e){field.setValue(spinner.getValue());field.putClientProperty("JComponent.outline",null);feedback.accept("");}});
    }
}
