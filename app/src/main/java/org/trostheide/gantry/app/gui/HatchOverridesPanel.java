package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.svgtoolbox.HatchStyle;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Editor for the colour-specific hatch styles already supported by SVGToolBox. */
final class HatchOverridesPanel extends JPanel {
    static final String USE_GLOBAL = "Use global";
    private static final String[] PATTERNS =
            {USE_GLOBAL, "linear", "cross", "zigzag", "wave", "dot", "none", "empty"};

    private final OverrideTableModel model = new OverrideTableModel();
    private final JTable table = new JTable(model);

    HatchOverridesPanel(Collection<String> fillColors) {
        super(new BorderLayout(0, 4));
        setBorder(BorderFactory.createTitledBorder("Per-colour hatch overrides"));

        table.setFillsViewportHeight(true);
        table.setPreferredScrollableViewportSize(new Dimension(390, 105));
        table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JComboBox<>(PATTERNS)));
        add(new JScrollPane(table), BorderLayout.CENTER);

        JButton add = new JButton("Add colour");
        add.addActionListener(e -> {
            int row = model.add("#000000", USE_GLOBAL, 45.0, 5.0);
            table.getSelectionModel().setSelectionInterval(row, row);
            table.editCellAt(row, 0);
        });
        JButton remove = new JButton("Remove");
        remove.addActionListener(e -> model.remove(table.getSelectedRows()));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.add(add);
        buttons.add(remove);
        buttons.add(new JLabel("Choose 'Use global' to leave a colour unchanged."));
        add(buttons, BorderLayout.SOUTH);

        fillColors.forEach(color -> model.addIfAbsent(color, USE_GLOBAL, 45.0, 5.0));
    }

    Map<String, HatchStyle> buildOverrides() {
        if (table.isEditing() && !table.getCellEditor().stopCellEditing()) {
            throw new IllegalArgumentException("Finish editing the hatch override table first.");
        }
        Map<String, HatchStyle> overrides = new LinkedHashMap<>();
        for (OverrideRow row : model.rows()) {
            String color = normalizeColor(row.color());
            if (overrides.containsKey(color) && !USE_GLOBAL.equals(row.pattern())) {
                throw new IllegalArgumentException("Duplicate hatch override colour: " + color);
            }
            if (!USE_GLOBAL.equals(row.pattern())) {
                if (row.gap() <= 0) {
                    throw new IllegalArgumentException("Hatch override gap must be greater than 0 for " + color + ".");
                }
                overrides.put(color, new HatchStyle(row.angle(), row.gap(), row.pattern()));
            }
        }
        return overrides;
    }

    List<OverrideRow> rows() {
        return model.rows();
    }

    void restoreRows(Collection<OverrideRow> rows) {
        model.replace(rows);
    }

    void addIfAbsent(String color) {
        model.addIfAbsent(color, USE_GLOBAL, 45.0, 5.0);
    }

    void setOverride(String color, String pattern, double angle, double gap) {
        model.set(color, pattern, angle, gap);
    }

    private static String normalizeColor(String value) {
        String color = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!color.matches("#[0-9a-f]{6}")) {
            throw new IllegalArgumentException("Hatch override colour must be #RRGGBB: " + value);
        }
        return color;
    }

    record OverrideRow(String color, String pattern, double angle, double gap) { }

    private static final class OverrideTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Fill colour", "Pattern", "Angle (deg)", "Gap"};
        private final List<OverrideRow> rows = new ArrayList<>();

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }
        @Override public Class<?> getColumnClass(int column) {
            return column < 2 ? String.class : Double.class;
        }
        @Override public boolean isCellEditable(int row, int column) { return true; }
        @Override public Object getValueAt(int row, int column) {
            OverrideRow value = rows.get(row);
            return switch (column) {
                case 0 -> value.color();
                case 1 -> value.pattern();
                case 2 -> value.angle();
                default -> value.gap();
            };
        }
        @Override public void setValueAt(Object value, int row, int column) {
            OverrideRow old = rows.get(row);
            try {
                rows.set(row, switch (column) {
                    case 0 -> new OverrideRow(String.valueOf(value), old.pattern(), old.angle(), old.gap());
                    case 1 -> new OverrideRow(old.color(), String.valueOf(value), old.angle(), old.gap());
                    case 2 -> new OverrideRow(old.color(), old.pattern(), number(value), old.gap());
                    default -> new OverrideRow(old.color(), old.pattern(), old.angle(), number(value));
                });
            } catch (NumberFormatException ignored) {
                // JTable leaves the previous valid value in place.
            }
            fireTableCellUpdated(row, column);
        }

        int add(String color, String pattern, double angle, double gap) {
            int index = rows.size();
            rows.add(new OverrideRow(color, pattern, angle, gap));
            fireTableRowsInserted(index, index);
            return index;
        }

        void addIfAbsent(String color, String pattern, double angle, double gap) {
            String normalized = color.toLowerCase(Locale.ROOT);
            if (rows.stream().noneMatch(row -> row.color().equalsIgnoreCase(normalized))) {
                add(normalized, pattern, angle, gap);
            }
        }

        void set(String color, String pattern, double angle, double gap) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).color().equalsIgnoreCase(color)) {
                    rows.set(i, new OverrideRow(color.toLowerCase(Locale.ROOT), pattern, angle, gap));
                    fireTableRowsUpdated(i, i);
                    return;
                }
            }
            add(color.toLowerCase(Locale.ROOT), pattern, angle, gap);
        }

        void remove(int[] selected) {
            for (int i = selected.length - 1; i >= 0; i--) rows.remove(selected[i]);
            fireTableDataChanged();
        }

        void replace(Collection<OverrideRow> replacements) {
            rows.clear();
            rows.addAll(replacements);
            fireTableDataChanged();
        }

        List<OverrideRow> rows() { return List.copyOf(rows); }

        private static double number(Object value) {
            return value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value));
        }
    }
}
